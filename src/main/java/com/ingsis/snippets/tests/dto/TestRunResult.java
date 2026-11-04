package com.ingsis.snippets.tests.dto;

import com.ingsis.snippets.clients.language.SourceError;
import com.ingsis.snippets.tests.model.TestStatus;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Resultado de {@code POST /snippets/{id}/tests/{testId}/run} (§5, D12). */
public record TestRunResult(
    UUID testId,
    TestStatus status,
    List<String> expectedOutputs,
    List<String> actualOutputs,
    @Nullable SourceError error) {}
