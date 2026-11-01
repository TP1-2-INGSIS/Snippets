package com.ingsis.snippets.rules.dto;

import com.ingsis.snippets.rules.model.RuleKind;

import tools.jackson.databind.JsonNode;

/**
 * Respuesta de {@code PUT /rules/{language}/{kind}} (§5): además de la config guardada,
 * cuántos snippets del usuario se reprocesaron y cuántos fallaron (US 12, 15).
 */
public record RuleApplyResponse(String language, RuleKind kind, JsonNode config, int processed, int failed) {
}
