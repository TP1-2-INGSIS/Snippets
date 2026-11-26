package com.ingsis.snippets.tasks;

import com.ingsis.snippets.clients.language.LanguageClient;
import com.ingsis.snippets.clients.language.LanguageInfo;
import com.ingsis.snippets.snippet.service.RuleApplier;
import com.ingsis.snippets.snippet.service.RuleApplier.RevalidationPage;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Re-valida todos los snippets cuando cambia la versión del motor de un lenguaje (consigna: "con
 * cada versión nueva del parser se deben re-evaluar y validar todos los snippets"). Compara la
 * {@code engineVersion} de {@code GET /languages} con la última guardada en {@code
 * language_engine_version}; si difiere o no existe (lenguaje visto por primera vez), encola un LINT
 * por snippet (con las reglas de su owner) por el pipeline asíncrono y guarda la versión nueva. La
 * fila del lenguaje se toma con {@code FOR UPDATE}: si dos instancias coinciden, la segunda espera,
 * ve la versión ya guardada y no hace nada.
 */
@Component
public class EngineVersionWatcher {

  private static final Logger log = LoggerFactory.getLogger(EngineVersionWatcher.class);

  private final LanguageClient languages;

  private final RuleApplier applier;

  private final JdbcTemplate jdbc;

  private final TransactionTemplate tx;

  private final int pageSize;

  public EngineVersionWatcher(
      LanguageClient languages,
      RuleApplier applier,
      JdbcTemplate jdbc,
      PlatformTransactionManager transactionManager,
      @Value("${engine.page-size:500}") int pageSize) {
    this.languages = languages;
    this.applier = applier;
    this.jdbc = jdbc;
    this.tx = new TransactionTemplate(transactionManager);
    this.pageSize = pageSize;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onStartup() {
    check();
  }

  @Scheduled(
      fixedDelayString = "${engine.check-interval:10m}",
      initialDelayString = "${engine.check-interval:10m}")
  public void scheduled() {
    check();
  }

  /**
   * @return cuántos snippets se encolaron para revalidar en esta corrida
   */
  public int check() {
    List<LanguageInfo> infos;
    try {
      infos = languages.languages();
    } catch (RuntimeException ex) {
      log.warn(
          "Language Service unavailable; engine version check will retry on the next tick", ex);
      return 0;
    }
    int total = 0;
    for (LanguageInfo info : infos) {
      try {
        total += revalidateIfChanged(info);
      } catch (RuntimeException ex) {
        log.warn("Engine revalidation of {} failed; will retry on the next tick", info.id(), ex);
      }
    }
    return total;
  }

  private int revalidateIfChanged(LanguageInfo info) {
    String current = info.engineVersion();
    if (current == null || current.isBlank()) {
      log.debug("Language {} reports no engineVersion; skipping", info.id());
      return 0;
    }
    Integer queued =
        tx.execute(status -> {
          String language = info.id();
          jdbc.update(
              "INSERT INTO language_engine_version (language, engine_version) VALUES (?, NULL)"
                  + " ON CONFLICT (language) DO NOTHING",
              language);
          List<String> stored =
              jdbc.query(
                  "SELECT engine_version FROM language_engine_version WHERE language = ?"
                      + " FOR UPDATE",
                  (rs, i) -> rs.getString(1),
                  language);
          if (!stored.isEmpty() && current.equals(stored.get(0))) {
            return 0;
          }
          int count = revalidateAll(language);
          jdbc.update(
              "UPDATE language_engine_version SET engine_version = ?, updated_at = now()"
                  + " WHERE language = ?",
              current,
              language);
          log.info(
              "Engine of {} is now {} (was {}): {} snippets re-enqueued",
              language,
              current,
              stored.isEmpty() ? null : stored.get(0),
              count);
          return count;
        });
    return queued == null ? 0 : queued;
  }

  private int revalidateAll(String language) {
    int total = 0;
    UUID after = null;
    while (true) {
      RevalidationPage page = applier.revalidateLanguagePage(language, after, pageSize);
      total += page.queued();
      after = page.lastId();
      if (page.scanned() < pageSize) {
        return total;
      }
    }
  }
}
