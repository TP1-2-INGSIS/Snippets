package com.ingsis.snippets.snippet.dto;

import java.util.List;

/** Respuesta paginada de {@code GET /snippets} (§5). */
public record SnippetPage(List<SnippetSummary> items, int page, int size, long total) {}
