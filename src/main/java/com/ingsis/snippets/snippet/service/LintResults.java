package com.ingsis.snippets.snippet.service;

import com.ingsis.snippets.clients.language.SourceError;
import com.ingsis.snippets.snippet.model.LintStatus;
import com.ingsis.snippets.snippet.model.Snippet;
import com.ingsis.snippets.snippet.model.SourceViolation;
import java.util.List;

/** Traduce el resultado de {@code lint} al estado persistido del snippet (D08). */
final class LintResults {

  private LintResults() {}

  /** Sin violaciones → COMPLIANT; con violaciones → NON_COMPLIANT. */
  static void apply(Snippet snippet, List<SourceError> violations) {
    List<SourceViolation> mapped =
        violations.stream()
            .map(e -> new SourceViolation(e.rule(), e.message(), e.line(), e.column()))
            .toList();
    snippet.setLintViolations(mapped);
    snippet.setLintStatus(mapped.isEmpty() ? LintStatus.COMPLIANT : LintStatus.NON_COMPLIANT);
  }
}
