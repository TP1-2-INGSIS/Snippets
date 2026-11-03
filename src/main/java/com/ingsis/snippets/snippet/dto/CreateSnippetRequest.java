package com.ingsis.snippets.snippet.dto;

import jakarta.validation.constraints.NotBlank;

import org.jspecify.annotations.Nullable;

/** Body de {@code POST /snippets} (US 3). */
public record CreateSnippetRequest(@NotBlank String name, @Nullable String description, @NotBlank String language,
		@NotBlank String version, @NotBlank String content) {
}
