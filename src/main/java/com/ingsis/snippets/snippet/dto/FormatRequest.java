package com.ingsis.snippets.snippet.dto;

import jakarta.validation.constraints.NotBlank;

/** Body de {@code POST /snippets/format}. */
public record FormatRequest(
    @NotBlank String content, @NotBlank String language, @NotBlank String version) {}
