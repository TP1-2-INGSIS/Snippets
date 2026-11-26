package com.ingsis.snippets.tasks;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Resultado que los workers publican en {@code tasks:results} (contrato LANG-2 ↔ SNIP-3). {@code
 * contentHash} es el eco del de la tarea.
 */
public record TaskResultEvent(
    UUID taskId,
    UUID snippetId,
    TaskType type,
    @Nullable String contentHash,
    @Nullable Integer attempts,
    @Nullable ResultStatus status,
    boolean success,
    @Nullable List<ResultError> errors,
    @Nullable String formattedContent,
    @Nullable List<String> outputs,
    @Nullable Boolean passed,
    @Nullable String errorMessage) {

  /** Un diagnóstico {@code {rule, message, line, column}}; line/column pueden faltar. */
  public record ResultError(
      String rule, String message, @Nullable Integer line, @Nullable Integer column) {}

  /** {@code status}, o lo que se deduce de {@code success} si el worker no lo manda. */
  public ResultStatus effectiveStatus() {
    if (status != null) {
      return status;
    }
    return success ? ResultStatus.OK : ResultStatus.FAILED;
  }

  public List<ResultError> errorsOrEmpty() {
    return errors == null ? List.of() : errors;
  }
}
