package com.ingsis.snippets.rules.dto;

import com.ingsis.snippets.rules.model.RuleKind;

import tools.jackson.databind.JsonNode;

/**
 * Respuesta de {@code GET /rules/{language}/{kind}} (§5). {@code isDefault} es true cuando
 * el usuario todavía no guardó reglas y se devuelve el default del servicio (D07).
 */
public record RuleConfigResponse(String language, RuleKind kind, boolean isDefault, JsonNode config) {
}
