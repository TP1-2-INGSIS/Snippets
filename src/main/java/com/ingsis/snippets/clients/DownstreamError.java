package com.ingsis.snippets.clients;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Error body shared by the downstream services: {@code { error, message, details }}. Language
 * reports the violations of an INVALID_SOURCE in {@code diagnostics}; older versions used {@code
 * details}.
 */
public record DownstreamError(
    String error,
    String message,
    @Nullable List<Object> details,
    @Nullable List<Object> diagnostics) {

  /** {@code diagnostics} when present and not empty, otherwise {@code details}. */
  public List<Object> violations() {
    if (diagnostics != null && !diagnostics.isEmpty()) {
      return diagnostics;
    }
    return details == null ? List.of() : details;
  }
}
