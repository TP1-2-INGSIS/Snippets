package com.ingsis.snippets.snippet.model;

import org.jspecify.annotations.Nullable;

/**
 * Una violación del último lint, tal como la reporta Language Service (§4, §5).
 * Se guarda dentro de {@code lint_violations} (jsonb, D20). {@code line}/{@code column}
 * pueden faltar según la regla.
 */
public record SourceViolation(String rule, String message, @Nullable Integer line, @Nullable Integer column) {
}
