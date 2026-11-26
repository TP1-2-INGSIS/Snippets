package com.ingsis.snippets.run;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.ingsis.snippets.TestcontainersConfiguration;
import com.ingsis.snippets.clients.permission.PermissionAction;
import com.ingsis.snippets.clients.permission.PermissionCheck;
import com.ingsis.snippets.clients.permission.PermissionClient;
import com.ingsis.snippets.clients.permission.PermissionRole;
import com.ingsis.snippets.snippet.model.Snippet;
import com.ingsis.snippets.snippet.repository.SnippetRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * The run relay end to end over real WebSockets. Language Service is faked by a handler at {@code
 * /languages/{language}/run} in the same application, with {@code services.language.url} pointing
 * at it.
 */
@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT, properties = "run.idle-timeout=2s")
@Import({TestcontainersConfiguration.class, RunRelayIntegrationTest.FakeLanguage.class})
class RunRelayIntegrationTest {

  private static final String ISSUER = "https://tenant.example.auth0.com/";
  private static final String AUDIENCE = "https://snippet-searcher/api";

  private static WireMockServer jwks;
  private static RSAKey signingKey;
  private static final List<String> LANGUAGE_SESSIONS = new CopyOnWriteArrayList<>();
  private static final Map<String, String> STARTS = new ConcurrentHashMap<>();

  /** A free port picked up front, because the fake Language needs its URL at startup. */
  private static final int PORT = freePort();

  @Autowired private SnippetRepository snippets;
  @MockitoBean private PermissionClient permissions;

  private static int freePort() {
    try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @BeforeAll
  static void startJwks() throws Exception {
    signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
    jwks = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    jwks.start();
    jwks.stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/jwks.json"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(new JWKSet(signingKey.toPublicJWK()).toString())));
  }

  @AfterAll
  static void stopJwks() {
    jwks.stop();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("auth.issuer-uri", () -> ISSUER);
    registry.add("auth.audience", () -> AUDIENCE);
    registry.add("auth.jwks-uri", () -> "http://localhost:" + jwks.port() + "/jwks.json");
    // The fake Language lives in this same application: its port is only known at runtime.
    registry.add("server.port", () -> PORT);
    registry.add("services.language.url", () -> "http://localhost:" + PORT);
  }

  @BeforeEach
  void reset() {
    LANGUAGE_SESSIONS.clear();
    STARTS.clear();
  }

  private UUID snippet(String language) {
    UUID id = UUID.randomUUID();
    snippets.save(new Snippet(id, "s", null, language, "1.1", "println(\"hi\");", "owner"));
    return id;
  }

  private void allow(String user, UUID id) {
    when(permissions.check(user, id, PermissionAction.READ))
        .thenReturn(new PermissionCheck(true, PermissionRole.OWNER));
  }

  private Client connect(String pathAndQuery) throws Exception {
    Client client = new Client();
    client.session =
        new StandardWebSocketClient()
            .execute(client, "ws://localhost:" + PORT + pathAndQuery)
            .get(5, TimeUnit.SECONDS);
    return client;
  }

  @Test
  void relaysAnInteractiveExecutionWithHeaderIdentity() throws Exception {
    UUID id = snippet("printscript");
    allow("user-ana", id);

    Client ui = connect("/snippets/" + id + "/run?userId=user-ana");
    assertThat(ui.next()).contains("\"type\":\"output\"").contains("hello");
    assertThat(ui.next()).contains("\"type\":\"input-request\"");
    ui.send("{\"type\":\"input\",\"value\":\"Ana\"}");
    assertThat(ui.next()).contains("\"type\":\"output\"").contains("hi Ana");
    assertThat(ui.next()).contains("\"type\":\"finished\"");
    assertThat(ui.closed.poll(5, TimeUnit.SECONDS).getCode()).isEqualTo(1000);

    assertThat(STARTS.get("printscript"))
        .contains("\"type\":\"start\"")
        .contains("\"version\":\"1.1\"")
        .contains("println(")
        .contains("\"env\":{}");
  }

  @Test
  void validJwtInTheQueryIdentifiesTheUserBySub() throws Exception {
    UUID id = snippet("printscript");
    allow("auth0|usr_1", id);

    Client ui = connect("/snippets/" + id + "/run?access_token=" + token("auth0|usr_1"));
    assertThat(ui.next()).contains("hello");
    verify(permissions).check("auth0|usr_1", id, PermissionAction.READ);
    ui.session.close();
  }

  @Test
  void invalidTokenRejectsTheHandshake() throws Exception {
    UUID id = snippet("printscript");
    assertThatThrownBy(() -> connect("/snippets/" + id + "/run?access_token=garbage"))
        .hasMessageContaining("401");
    assertThat(LANGUAGE_SESSIONS).isEmpty();
  }

  @Test
  void missingIdentityRejectsTheHandshake() {
    UUID id = UUID.randomUUID();
    assertThatThrownBy(() -> connect("/snippets/" + id + "/run")).hasMessageContaining("401");
  }

  @Test
  void accessTokenInTheQueryIsNotAcceptedOnOtherPaths() throws Exception {
    String token = token("auth0|usr_1");
    assertThatThrownBy(() ->
            RestClient.create("http://localhost:" + PORT)
                .get()
                .uri("/snippets?access_token=" + token)
                .retrieve()
                .toBodilessEntity())
        .isInstanceOf(HttpClientErrorException.Unauthorized.class);
  }

  @Test
  void deniedPermissionClosesWith1008WithoutContactingLanguage() throws Exception {
    UUID id = snippet("printscript");
    when(permissions.check("user-bob", id, PermissionAction.READ))
        .thenReturn(new PermissionCheck(false, null));

    Client ui = connect("/snippets/" + id + "/run?userId=user-bob");
    assertThat(ui.closed.poll(5, TimeUnit.SECONDS).getCode()).isEqualTo(1008);
    assertThat(LANGUAGE_SESSIONS).isEmpty();
  }

  @Test
  void unknownSnippetClosesWith1008() throws Exception {
    UUID id = UUID.randomUUID();
    allow("user-ana", id);

    Client ui = connect("/snippets/" + id + "/run?userId=user-ana");
    assertThat(ui.closed.poll(5, TimeUnit.SECONDS).getCode()).isEqualTo(1008);
    assertThat(LANGUAGE_SESSIONS).isEmpty();
  }

  @Test
  void malformedSnippetIdClosesWith1008() throws Exception {
    Client ui = connect("/snippets/not-a-uuid/run?userId=user-ana");
    assertThat(ui.closed.poll(5, TimeUnit.SECONDS).getCode()).isEqualTo(1008);
    verify(permissions, never()).check(any(), any(), eq(PermissionAction.READ));
  }

  @Test
  void unreachableLanguageSendsErrorThenCloses1011() throws Exception {
    UUID id = snippet("down");
    allow("user-ana", id);

    Client ui = connect("/snippets/" + id + "/run?userId=user-ana");
    assertThat(ui.next()).contains("\"type\":\"error\"");
    assertThat(ui.closed.poll(5, TimeUnit.SECONDS).getCode()).isEqualTo(1011);
  }

  @Test
  void languageDroppingTheConnectionSendsErrorThenCloses1011() throws Exception {
    UUID id = snippet("crash");
    allow("user-ana", id);

    Client ui = connect("/snippets/" + id + "/run?userId=user-ana");
    assertThat(ui.next()).contains("\"type\":\"error\"");
    assertThat(ui.closed.poll(5, TimeUnit.SECONDS).getCode()).isEqualTo(1011);
  }

  @Test
  void stopIsForwardedAndLanguageEndsTheExecution() throws Exception {
    UUID id = snippet("stoppable");
    allow("user-ana", id);

    Client ui = connect("/snippets/" + id + "/run?userId=user-ana");
    ui.send("{\"type\":\"stop\"}");
    assertThat(ui.next()).contains("\"type\":\"finished\"");
    assertThat(ui.closed.poll(5, TimeUnit.SECONDS).getCode()).isEqualTo(1000);
  }

  @Test
  void idleRelayIsClosedWithAnError() throws Exception {
    UUID id = snippet("silent");
    allow("user-ana", id);

    Client ui = connect("/snippets/" + id + "/run?userId=user-ana");
    assertThat(ui.next()).contains("\"type\":\"error\"");
    assertThat(ui.closed.poll(5, TimeUnit.SECONDS).getCode()).isEqualTo(1011);
  }

  @Test
  void uiClosingClosesTheLanguageSession() throws Exception {
    UUID id = snippet("silent");
    allow("user-ana", id);

    Client ui = connect("/snippets/" + id + "/run?userId=user-ana");
    ui.session.close();
    assertThat(FakeLanguage.CLOSED_BY_RELAY.poll(5, TimeUnit.SECONDS)).isNotNull();
  }

  private static String token(String subject) throws Exception {
    Date now = new Date();
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(subject)
            .issuer(ISSUER)
            .audience(AUDIENCE)
            .issueTime(new Date(now.getTime() - 3_600_000))
            .expirationTime(new Date(now.getTime() + 600_000))
            .build();
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(), claims);
    jwt.sign(new RSASSASigner(signingKey));
    return jwt.serialize();
  }

  /** The UI side: collects what the relay sends. */
  private static final class Client extends TextWebSocketHandler {
    final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
    final BlockingQueue<CloseStatus> closed = new LinkedBlockingQueue<>();
    WebSocketSession session;

    String next() throws InterruptedException {
      String message = messages.poll(5, TimeUnit.SECONDS);
      assertThat(message).as("a message from the relay").isNotNull();
      return message;
    }

    void send(String json) throws Exception {
      session.sendMessage(new TextMessage(json));
    }

    @Override
    protected void handleTextMessage(WebSocketSession s, TextMessage message) {
      messages.add(message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
      closed.add(status);
    }
  }

  /** Test-only Language Service: a scripted {@code /languages/{language}/run}. */
  @TestConfiguration(proxyBeanMethods = false)
  @EnableWebSocket
  static class FakeLanguage implements WebSocketConfigurer {

    static final BlockingQueue<String> CLOSED_BY_RELAY = new LinkedBlockingQueue<>();

    @Bean
    @Order(0)
    SecurityFilterChain fakeLanguageChain(HttpSecurity http) throws Exception {
      return http.securityMatcher("/languages/*/run")
          .csrf(AbstractHttpConfigurer::disable)
          .authorizeHttpRequests(a -> a.anyRequest().permitAll())
          .build();
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
      registry
          .addHandler(new Script(), "/languages/{language}/run")
          .addInterceptors(new Refuse())
          .setAllowedOrigins("*");
    }

    private static String languageOf(WebSocketSession s) {
      String path = s.getUri().getPath();
      return path.split("/")[2];
    }

    /** The language "down" refuses the handshake: Language is unreachable. */
    static final class Refuse implements HandshakeInterceptor {
      @Override
      public boolean beforeHandshake(
          ServerHttpRequest request,
          ServerHttpResponse response,
          WebSocketHandler handler,
          Map<String, Object> attributes) {
        if (request.getURI().getPath().contains("/down/")) {
          ((ServletServerHttpResponse) response).getServletResponse().setStatus(503);
          return false;
        }
        return true;
      }

      @Override
      public void afterHandshake(
          ServerHttpRequest request,
          ServerHttpResponse response,
          WebSocketHandler handler,
          Exception exception) {}
    }

    static final class Script extends TextWebSocketHandler {

      @Override
      public void afterConnectionEstablished(WebSocketSession session) {
        LANGUAGE_SESSIONS.add(session.getId());
      }

      @Override
      protected void handleTextMessage(WebSocketSession session, TextMessage message)
          throws Exception {
        String payload = message.getPayload();
        String language = languageOf(session);
        if (payload.contains("\"type\":\"start\"")) {
          STARTS.put(language, payload);
          switch (language) {
            case "printscript" -> {
              session.sendMessage(new TextMessage("{\"type\":\"output\",\"value\":\"hello\"}"));
              session.sendMessage(
                  new TextMessage("{\"type\":\"input-request\",\"prompt\":\"name?\"}"));
            }
            case "crash" -> session.close(new CloseStatus(1011, "boom"));
            default -> {
              // "silent" and "stoppable" wait for the other side.
            }
          }
        } else if (payload.contains("\"type\":\"input\"")) {
          session.sendMessage(new TextMessage("{\"type\":\"output\",\"value\":\"hi Ana\"}"));
          session.sendMessage(new TextMessage("{\"type\":\"finished\"}"));
          session.close(CloseStatus.NORMAL);
        } else if (payload.contains("\"type\":\"stop\"")) {
          session.sendMessage(new TextMessage("{\"type\":\"finished\"}"));
          session.close(CloseStatus.NORMAL);
        }
      }

      @Override
      public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        CLOSED_BY_RELAY.add(session.getId());
      }
    }
  }
}
