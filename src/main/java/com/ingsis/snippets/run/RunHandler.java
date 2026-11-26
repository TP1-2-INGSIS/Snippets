package com.ingsis.snippets.run;

import com.ingsis.snippets.clients.ServicesProperties;
import com.ingsis.snippets.clients.permission.PermissionAction;
import com.ingsis.snippets.clients.permission.PermissionCheck;
import com.ingsis.snippets.clients.permission.PermissionClient;
import com.ingsis.snippets.snippet.model.Snippet;
import com.ingsis.snippets.snippet.repository.SnippetRepository;
import com.ingsis.snippets.snippet.storage.SnippetStorage;
import jakarta.annotation.PreDestroy;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * Relay for interactive execution (US10). Snippets authenticates (handshake) and authorizes (READ
 * on the snippet), then relays the JSON messages unchanged to Language Service and back:
 *
 * <ul>
 *   <li>UI to Language: {@code input}, {@code stop}.
 *   <li>Language to UI: {@code output}, {@code input-request}, {@code finished}, {@code error}.
 * </ul>
 *
 * Close codes: Language's own code is forwarded unchanged (1000, 1008, 1011); the relay uses 1008
 * when identity or permission fail (Language is never contacted) and 1011 when Language is
 * unreachable, drops without a close code, or the relay fails (idle timeout included).
 */
@Component
class RunHandler extends TextWebSocketHandler {

  static final int MAX_MESSAGE_BYTES = 1024 * 1024;

  private static final Logger LOG = LoggerFactory.getLogger(RunHandler.class);
  private static final int SEND_TIME_LIMIT_MS = 10_000;
  private static final int SEND_BUFFER_LIMIT_BYTES = 4 * 1024 * 1024;
  private static final long CONNECT_TIMEOUT_SECONDS = 5;

  private final PermissionClient permissions;
  private final SnippetRepository snippets;
  private final SnippetStorage storage;
  private final ServicesProperties services;
  private final RunProperties properties;
  private final ObjectMapper objectMapper;
  private final StandardWebSocketClient client;
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "run-relay-idle");
        t.setDaemon(true);
        return t;
      });
  private final Map<String, Relay> relays = new ConcurrentHashMap<>();

  RunHandler(
      PermissionClient permissions,
      SnippetRepository snippets,
      SnippetStorage storage,
      ServicesProperties services,
      RunProperties properties,
      ObjectMapper objectMapper) {
    this.permissions = permissions;
    this.snippets = snippets;
    this.storage = storage;
    this.services = services;
    this.properties = properties;
    this.objectMapper = objectMapper;
    WebSocketContainer container = ContainerProvider.getWebSocketContainer();
    container.setDefaultMaxTextMessageBufferSize(MAX_MESSAGE_BYTES);
    this.client = new StandardWebSocketClient(container);
  }

  @PreDestroy
  void shutdown() {
    scheduler.shutdownNow();
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession raw) throws Exception {
    WebSocketSession ui =
        new ConcurrentWebSocketSessionDecorator(raw, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES);
    Relay relay = new Relay(ui);
    relays.put(raw.getId(), relay);

    Object user = raw.getAttributes().get(RunWebSocketConfig.ATTR_USER);
    Object snippetId = raw.getAttributes().get(RunWebSocketConfig.ATTR_SNIPPET);
    if (!(user instanceof String userId) || !(snippetId instanceof UUID id)) {
      relay.close(CloseStatus.POLICY_VIOLATION.withReason("unauthorized"));
      return;
    }
    Snippet snippet;
    try {
      PermissionCheck check = permissions.check(userId, id, PermissionAction.READ);
      Optional<Snippet> found =
          check.allowed() && check.role() != null ? snippets.findById(id) : Optional.empty();
      if (found.isEmpty()) {
        relay.close(CloseStatus.POLICY_VIOLATION.withReason("forbidden"));
        return;
      }
      snippet = found.get();
      connectToLanguage(relay, snippet, storage.get(id));
    } catch (RuntimeException e) {
      LOG.warn("Run relay for snippet {} failed: {}", id, e.toString());
      relay.fail("Execution service unavailable");
    }
  }

  private void connectToLanguage(Relay relay, Snippet snippet, String content) {
    URI uri = languageUri(snippet.getLanguage());
    WebSocketSession lang;
    try {
      lang =
          client
              .execute(new LanguageHandler(relay), uri.toString())
              .get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while connecting to Language", e);
    } catch (Exception e) {
      throw new IllegalStateException("Language service unreachable", e);
    }
    relay.attach(
        new ConcurrentWebSocketSessionDecorator(lang, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES));
    relay.sendToLanguage(
        new TextMessage(
            objectMapper.writeValueAsString(
                Map.of(
                    "type",
                    "start",
                    "content",
                    content,
                    "version",
                    snippet.getVersion(),
                    "env",
                    Map.of()))));
  }

  /** {@code http(s)://host:port} becomes {@code ws(s)://host:port/languages/{language}/run}. */
  URI languageUri(String language) {
    UriComponentsBuilder base = UriComponentsBuilder.fromUriString(services.language().url());
    String scheme = "https".equalsIgnoreCase(base.build().getScheme()) ? "wss" : "ws";
    return base.scheme(scheme)
        .path("/languages/{language}/run")
        .buildAndExpand(language)
        .encode()
        .toUri();
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    Relay relay = relays.get(session.getId());
    if (relay != null) {
      relay.sendToLanguage(message);
    }
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    Relay relay = relays.get(session.getId());
    if (relay != null) {
      relay.close(CloseStatus.SERVER_ERROR);
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    Relay relay = relays.remove(session.getId());
    if (relay != null) {
      relay.close(CloseStatus.NORMAL);
    }
  }

  /** The pair of sessions of one execution. Closing either side closes the other. */
  private final class Relay {

    private final WebSocketSession ui;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile @Nullable WebSocketSession language;
    private volatile @Nullable ScheduledFuture<?> idle;

    Relay(WebSocketSession ui) {
      this.ui = ui;
      touch();
    }

    void attach(WebSocketSession language) {
      this.language = language;
      // The relay may have been closed while connecting: don't leak the new session.
      if (closed.get()) {
        closeQuietly(language, CloseStatus.NORMAL);
      }
    }

    synchronized void touch() {
      ScheduledFuture<?> previous = idle;
      if (previous != null) {
        previous.cancel(false);
      }
      if (!closed.get()) {
        idle =
            scheduler.schedule(
                () -> fail("Execution timed out"),
                properties.idleTimeout().toMillis(),
                TimeUnit.MILLISECONDS);
      }
    }

    void sendToLanguage(TextMessage message) {
      touch();
      WebSocketSession target = language;
      if (target == null || !target.isOpen()) {
        return;
      }
      try {
        target.sendMessage(message);
      } catch (IOException | RuntimeException e) {
        fail("Execution service unavailable");
      }
    }

    void sendToUi(TextMessage message) {
      touch();
      try {
        ui.sendMessage(message);
      } catch (IOException | RuntimeException e) {
        close(CloseStatus.SERVER_ERROR);
      }
    }

    /** Tells the UI what went wrong with an {@code error} message, then closes with 1011. */
    void fail(String message) {
      if (!closed.get() && ui.isOpen()) {
        try {
          ui.sendMessage(
              new TextMessage(
                  objectMapper.writeValueAsString(Map.of("type", "error", "message", message))));
        } catch (IOException | RuntimeException e) {
          LOG.debug("Could not deliver the error message to the UI");
        }
      }
      close(CloseStatus.SERVER_ERROR);
    }

    void close(CloseStatus status) {
      if (!closed.compareAndSet(false, true)) {
        return;
      }
      ScheduledFuture<?> pending = idle;
      if (pending != null) {
        pending.cancel(false);
      }
      relays.values().remove(this);
      closeQuietly(language, status.getCode() == 1008 ? CloseStatus.NORMAL : status);
      closeQuietly(ui, status);
    }

    private void closeQuietly(@Nullable WebSocketSession session, CloseStatus status) {
      if (session == null) {
        return;
      }
      try {
        session.close(status);
      } catch (IOException | RuntimeException e) {
        LOG.debug("Close failed: {}", e.toString());
      }
    }
  }

  /** Close codes Language may send on purpose (1000, 1008, 1011...); 1005/1006 mean it dropped. */
  private static boolean isForwardable(int code) {
    return (code >= 1000 && code <= 1003 && code != 1002 || code >= 1007 && code <= 1013)
        || (code >= 3000 && code <= 4999);
  }

  /** The client side of the Language connection. */
  private final class LanguageHandler extends TextWebSocketHandler {

    private final Relay relay;

    LanguageHandler(Relay relay) {
      this.relay = relay;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
      relay.sendToUi(message);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
      relay.fail("Execution service unavailable");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
      int code = status.getCode();
      if (isForwardable(code)) {
        // Language already sent its own error message when it had one: pass the code through.
        relay.close(new CloseStatus(code, status.getReason()));
      } else {
        relay.fail("Execution service closed the connection");
      }
    }
  }
}
