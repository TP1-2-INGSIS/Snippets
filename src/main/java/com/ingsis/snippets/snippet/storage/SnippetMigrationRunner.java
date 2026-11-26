package com.ingsis.snippets.snippet.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * Lanza la migración al arrancar en modo {@code assets}, en un hilo aparte para no demorar el
 * arranque. Si Assets no está disponible no tira la app: lo pendiente se migra perezosamente al
 * leer o en el próximo arranque.
 */
class SnippetMigrationRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(SnippetMigrationRunner.class);

  private final SnippetContentMigrator migrator;

  private final boolean enabled;

  SnippetMigrationRunner(SnippetContentMigrator migrator, boolean enabled) {
    this.migrator = migrator;
    this.enabled = enabled;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!enabled) {
      return;
    }
    Thread.ofPlatform()
        .daemon()
        .name("snippet-content-migration")
        .start(() -> {
          try {
            migrator.migrateAll();
          } catch (RuntimeException ex) {
            log.warn("Content migration aborted; it will resume on the next startup", ex);
          }
        });
  }
}
