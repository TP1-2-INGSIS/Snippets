package com.ingsis.snippets.tasks;

import com.ingsis.snippets.snippet.model.FormatStatus;
import com.ingsis.snippets.snippet.model.LintStatus;
import com.ingsis.snippets.snippet.model.Snippet;
import com.ingsis.snippets.snippet.model.SourceViolation;
import com.ingsis.snippets.snippet.repository.SnippetRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aplica un resultado de un worker al snippet, en una transacción. Es idempotente: aplicar dos
 * veces el mismo resultado deja el mismo estado. El ACK lo hace quien llama, después del commit.
 */
@Service
public class ResultProcessor {

  private static final Logger log = LoggerFactory.getLogger(ResultProcessor.class);

  private final SnippetRepository snippets;

  public ResultProcessor(SnippetRepository snippets) {
    this.snippets = snippets;
  }

  /**
   * @return si el resultado se aplicó (false = snippet borrado, resultado viejo o tipo ignorado)
   */
  @Transactional
  public boolean apply(TaskResultEvent result) {
    Snippet snippet = snippets.findById(result.snippetId()).orElse(null);
    if (snippet == null) {
      log.debug("Result {} for a deleted snippet; ignoring", result.taskId());
      return false;
    }
    if (isStale(snippet, result)) {
      log.debug("Stale result {} for snippet {}; ignoring", result.taskId(), snippet.getId());
      return false;
    }
    switch (result.type()) {
      case LINT -> applyLint(snippet, result);
      case FORMAT -> applyFormat(snippet, result);
      default -> {
        log.debug("Ignoring result {} of type {}", result.taskId(), result.type());
        return false;
      }
    }
    snippets.save(snippet);
    return true;
  }

  /**
   * Viejo si el snippet cambió desde que se encoló la tarea (el hash del eco no coincide) o si no
   * es la última tarea encolada de ese tipo (otro cambio de reglas o un re-encolado la reemplazó).
   */
  static boolean isStale(Snippet snippet, TaskResultEvent result) {
    if (!ContentHash.sha256(snippet.getContent()).equals(result.contentHash())) {
      return true;
    }
    UUID current =
        switch (result.type()) {
          case LINT -> snippet.getLintTaskId();
          case FORMAT -> snippet.getFormatTaskId();
          default -> null;
        };
    return current != null && !current.equals(result.taskId());
  }

  private static void applyLint(Snippet snippet, TaskResultEvent result) {
    List<SourceViolation> violations =
        result.errorsOrEmpty().stream()
            .map(e -> new SourceViolation(e.rule(), e.message(), e.line(), e.column()))
            .toList();
    snippet.setLintViolations(violations);
    if (result.effectiveStatus() == ResultStatus.OK) {
      snippet.setLintStatus(violations.isEmpty() ? LintStatus.COMPLIANT : LintStatus.NON_COMPLIANT);
    } else {
      snippet.setLintStatus(LintStatus.FAILED);
    }
  }

  private static void applyFormat(Snippet snippet, TaskResultEvent result) {
    if (result.effectiveStatus() == ResultStatus.OK && result.formattedContent() != null) {
      snippet.setFormattedContent(result.formattedContent());
      snippet.setFormatStatus(FormatStatus.DONE);
    } else {
      snippet.setFormatStatus(FormatStatus.FAILED);
    }
  }
}
