package com.ingsis.snippets.sharing.dto;

import jakarta.validation.constraints.NotBlank;

/** Body de {@code POST /snippets/{id}/share} (US 7). */
public record ShareRequest(@NotBlank String userId) {
}
