package com.ingsis.snippets.tests.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.ingsis.snippets.TestcontainersConfiguration;
import com.ingsis.snippets.snippet.model.Snippet;
import com.ingsis.snippets.snippet.repository.SnippetRepository;
import com.ingsis.snippets.tests.model.SnippetTest;
import com.ingsis.snippets.tests.model.TestStatus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class SnippetTestRepositoryTest {

	@Autowired
	private SnippetTestRepository tests;

	@Autowired
	private SnippetRepository snippets;

	private UUID persistSnippet() {
		Snippet s = new Snippet(UUID.randomUUID(), "n", null, "printscript", "1.1", "c", "user-ana");
		return snippets.save(s).getId();
	}

	@Test
	void savesAndReadsJsonArraysAndDefaultStatus() {
		UUID snippetId = persistSnippet();
		var saved = tests.save(new SnippetTest(UUID.randomUUID(), snippetId, "t1", List.of("Ana", "Bob"),
			List.of("Hola Ana", "Hola Bob")));
		tests.flush();

		var found = tests.findById(saved.getId()).orElseThrow();
		assertThat(found.getInputs()).containsExactly("Ana", "Bob");
		assertThat(found.getExpectedOutputs()).containsExactly("Hola Ana", "Hola Bob");
		assertThat(found.getLastStatus()).isEqualTo(TestStatus.NOT_RUN);
		assertThat(found.getLastRunAt()).isNull();
	}

	@Test
	void findByIdAndSnippetIdScopesToTheSnippet() {
		UUID snippetA = persistSnippet();
		UUID snippetB = persistSnippet();
		var test = tests.save(new SnippetTest(UUID.randomUUID(), snippetA, "t", List.of(), List.of()));

		assertThat(tests.findByIdAndSnippetId(test.getId(), snippetA)).isPresent();
		assertThat(tests.findByIdAndSnippetId(test.getId(), snippetB)).isEmpty();
	}

	@Test
	void deletingSnippetCascadesToItsTests() {
		UUID snippetId = persistSnippet();
		tests.save(new SnippetTest(UUID.randomUUID(), snippetId, "t", List.of(), List.of()));
		tests.flush();

		snippets.deleteById(snippetId);
		snippets.flush();

		assertThat(tests.findBySnippetId(snippetId)).isEmpty();
	}

}
