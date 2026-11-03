package com.ingsis.snippets.snippet.dto;

import org.jspecify.annotations.Nullable;

/** Body de {@code PUT /snippets/{id}} (US 4). Todos los campos son opcionales. */
public record UpdateSnippetRequest(@Nullable String name, @Nullable String description, @Nullable String language,
		@Nullable String version, @Nullable String content) {
}
