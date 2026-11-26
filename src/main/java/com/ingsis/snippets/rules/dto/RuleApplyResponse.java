package com.ingsis.snippets.rules.dto;

import com.ingsis.snippets.rules.model.RuleKind;
import tools.jackson.databind.JsonNode;

/**
 * Respuesta 202 de {@code PUT /rules/{language}/{kind}} (§5): la config guardada y cuántas tareas
 * se encolaron para los snippets del usuario (US 12, 15). El resultado llega en segundo plano.
 */
public record RuleApplyResponse(String language, RuleKind kind, JsonNode config, int queued) {}
