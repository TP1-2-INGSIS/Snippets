package com.ingsis.snippets.rules.dto;

import jakarta.validation.constraints.NotNull;

import tools.jackson.databind.JsonNode;

/** Body de {@code PUT /rules/{language}/{kind}}. {@code config} es opaco (D07). */
public record RuleConfigRequest(@NotNull JsonNode config) {
}
