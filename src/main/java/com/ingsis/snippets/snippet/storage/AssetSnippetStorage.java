package com.ingsis.snippets.snippet.storage;

import com.ingsis.snippets.clients.DownstreamCalls;
import com.ingsis.snippets.common.error.DownstreamException;
import com.ingsis.snippets.snippet.model.Snippet;
import com.ingsis.snippets.snippet.repository.SnippetRepository;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Modo {@code assets} (D3): el contenido vive en el Assets service, {@code /v1/assets/{snippetId}}
 * (POST crea, PUT reemplaza, GET lee, DELETE borra). Los errores de Assets siguen la convención de
 * downstream (D11): todo fallo es un {@link DownstreamException}.
 *
 * <p>Compatibilidad con filas viejas: si un GET da 404 pero la columna {@code content} todavía
 * tiene texto, se devuelve ese texto y se migra la fila (migración perezosa). Cuando se escribe a
 * Assets, la columna de esa fila queda en NULL.
 */
public class AssetSnippetStorage implements SnippetStorage {

  private static final Logger log = LoggerFactory.getLogger(AssetSnippetStorage.class);

  private static final String SERVICE = "Assets Service";

  private static final MediaType TEXT = new MediaType("text", "plain", StandardCharsets.UTF_8);

  private final RestClient client;

  private final SnippetRepository snippets;

  private final TransactionTemplate newTransaction;

  public AssetSnippetStorage(
      RestClient client, SnippetRepository snippets, PlatformTransactionManager transactions) {
    this.client = client;
    this.snippets = snippets;
    this.newTransaction = new TransactionTemplate(transactions);
    this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public void create(UUID snippetId, String content) {
    upload(snippetId, content, true);
  }

  /** PUT, y deja en NULL la columna de la fila (en la transacción de quien llama). */
  @Override
  public void save(UUID snippetId, String content) {
    upload(snippetId, content, false);
    snippets.findById(snippetId).ifPresent(s -> s.setContent(null));
  }

  @Override
  public String get(UUID snippetId) {
    Optional<String> remote = fetch(snippetId);
    if (remote.isPresent()) {
      return remote.get();
    }
    String legacy = snippets.findById(snippetId).map(Snippet::getContent).orElse(null);
    if (legacy == null) {
      throw new DownstreamException(SERVICE + " has no content for snippet " + snippetId, null);
    }
    try {
      migrate(snippetId);
    } catch (RuntimeException ex) {
      log.warn(
          "Lazy migration of snippet {} to Assets failed; served from the column", snippetId, ex);
    }
    return legacy;
  }

  @Override
  public void remove(UUID snippetId) {
    DownstreamCalls.call(
        SERVICE,
        () -> {
          try {
            client.delete().uri("/v1/assets/{id}", snippetId).retrieve().toBodilessEntity();
            return Boolean.TRUE;
          } catch (HttpClientErrorException.NotFound notFound) {
            return Boolean.TRUE; // ya no estaba: idempotente
          }
        },
        error -> null);
  }

  /**
   * Mueve el contenido de la columna a Assets y deja la columna en NULL, en una transacción propia.
   * Si Assets ya tiene el contenido, gana Assets (no se pisa con un valor viejo).
   *
   * @return si la fila tenía contenido en la columna
   */
  public boolean migrate(UUID snippetId) {
    return Boolean.TRUE.equals(
        newTransaction.execute(status -> {
          Snippet snippet = snippets.findById(snippetId).orElse(null);
          String content = snippet == null ? null : snippet.getContent();
          if (content == null) {
            return false;
          }
          if (fetch(snippetId).isEmpty()) {
            upload(snippetId, content, true);
          }
          snippet.setContent(null);
          return true;
        }));
  }

  private Optional<String> fetch(UUID snippetId) {
    return DownstreamCalls.call(
        SERVICE,
        () -> {
          try {
            byte[] body =
                client.get().uri("/v1/assets/{id}", snippetId).retrieve().body(byte[].class);
            return Optional.of(
                new String(body == null ? new byte[0] : body, StandardCharsets.UTF_8));
          } catch (HttpClientErrorException.NotFound notFound) {
            return Optional.<String>empty();
          }
        },
        error -> null);
  }

  private void upload(UUID snippetId, String content, boolean create) {
    DownstreamCalls.call(
        SERVICE,
        () -> {
          RestClient.RequestBodySpec request =
              create
                  ? client.post().uri("/v1/assets/{id}", snippetId)
                  : client.put().uri("/v1/assets/{id}", snippetId);
          request
              .contentType(TEXT)
              .body(content.getBytes(StandardCharsets.UTF_8))
              .retrieve()
              .toBodilessEntity();
          return Boolean.TRUE;
        },
        error -> null);
  }
}
