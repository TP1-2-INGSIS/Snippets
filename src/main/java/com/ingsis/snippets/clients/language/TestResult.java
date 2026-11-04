package com.ingsis.snippets.clients.language;

import java.util.List;

/**
 * Outcome of the assert run by Language Service (D12). {@code error} is null if the run did not
 * fail.
 */
public record TestResult(
    boolean passed, List<String> expectedOutputs, List<String> actualOutputs, SourceError error) {}
