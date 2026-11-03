package com.ingsis.snippets.tests.service;

import com.ingsis.snippets.snippet.event.SnippetContentChanged;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Corre todos los tests de un snippet después de que su contenido cambió (US 16, D13).
 * Sincrónico y tolerante a fallos; no afecta la respuesta del {@code PUT}.
 */
@Component
class ContentChangedTestRunner {

	private final SnippetTestService tests;

	ContentChangedTestRunner(SnippetTestService tests) {
		this.tests = tests;
	}

	@EventListener
	void onContentChanged(SnippetContentChanged event) {
		tests.runAll(event.snippetId());
	}

}
