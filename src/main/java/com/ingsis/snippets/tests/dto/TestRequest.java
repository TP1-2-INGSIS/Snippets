package com.ingsis.snippets.tests.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Body de {@code POST/PUT /snippets/{id}/tests[/{testId}]} (US 8). */
public record TestRequest(
    @NotBlank String name, @NotNull List<String> inputs, @NotNull List<String> expectedOutputs) {}
