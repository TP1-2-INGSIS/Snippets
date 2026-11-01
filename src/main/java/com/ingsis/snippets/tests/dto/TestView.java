package com.ingsis.snippets.tests.dto;

import java.util.List;
import java.util.UUID;

import com.ingsis.snippets.tests.model.SnippetTest;
import com.ingsis.snippets.tests.model.TestStatus;

/** Vista de un test (§5). */
public record TestView(UUID id, String name, List<String> inputs, List<String> expectedOutputs,
		TestStatus lastStatus) {

	public static TestView of(SnippetTest test) {
		return new TestView(test.getId(), test.getName(), test.getInputs(), test.getExpectedOutputs(),
			test.getLastStatus());
	}

}
