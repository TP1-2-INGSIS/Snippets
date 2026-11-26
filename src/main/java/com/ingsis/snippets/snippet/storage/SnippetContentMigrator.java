package com.ingsis.snippets.snippet.storage;

import com.ingsis.snippets.snippet.repository.SnippetRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;

/**
 * Sube a Assets el contenido que todavía está en la columna {@code content} y la deja en NULL
 * (SNIP-4). Idempotente y reanudable: solo toca filas con contenido, por páginas ordenadas por id,
 * una transacción por fila; una fila que falla se registra y se salta (queda para la próxima
 * corrida) sin cortar el resto.
 */
public class SnippetContentMigrator {

  private static final Logger log = LoggerFactory.getLogger(SnippetContentMigrator.class);

  private static final UUID MIN_ID = new UUID(0L, 0L);

  private final AssetSnippetStorage storage;

  private final SnippetRepository snippets;

  private final int batchSize;

  public SnippetContentMigrator(
      AssetSnippetStorage storage, SnippetRepository snippets, int batchSize) {
    this.storage = storage;
    this.snippets = snippets;
    this.batchSize = Math.max(1, batchSize);
  }

  /**
   * @return cuántas filas se migraron en esta corrida
   */
  public int migrateAll() {
    int migrated = 0;
    int failed = 0;
    UUID after = MIN_ID;
    while (true) {
      List<UUID> page = snippets.findIdsWithContentAfter(after, PageRequest.ofSize(batchSize));
      if (page.isEmpty()) {
        break;
      }
      for (UUID id : page) {
        try {
          if (storage.migrate(id)) {
            migrated++;
          }
        } catch (RuntimeException ex) {
          failed++;
          log.warn("Could not migrate snippet {} to Assets; will retry on the next run", id, ex);
        }
      }
      after = page.get(page.size() - 1);
      log.info("Content migration progress: {} migrated, {} failed so far", migrated, failed);
    }
    log.info("Content migration finished: {} migrated, {} failed", migrated, failed);
    return migrated;
  }
}
