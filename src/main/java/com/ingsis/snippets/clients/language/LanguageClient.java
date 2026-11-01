package com.ingsis.snippets.clients.language;

import java.util.List;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/** Language Service: everything that depends on the language. {@code rules} is opaque JSON (D07). */
public interface LanguageClient {

	List<LanguageInfo> languages();

	ValidationResult validate(String language, String content, String version);

	String format(String language, String content, String version, @Nullable JsonNode rules);

	List<SourceError> lint(String language, String content, String version, @Nullable JsonNode rules);

	TestResult test(String language, String content, String version, List<String> inputs,
			List<String> expectedOutputs);

}
