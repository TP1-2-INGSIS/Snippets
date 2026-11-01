package com.ingsis.snippets.tests.service;

import java.util.List;
import java.util.UUID;

import com.ingsis.snippets.authorization.AuthorizationService;
import com.ingsis.snippets.clients.language.LanguageClient;
import com.ingsis.snippets.clients.language.TestResult;
import com.ingsis.snippets.clients.permission.PermissionAction;
import com.ingsis.snippets.common.error.NotFoundException;
import com.ingsis.snippets.common.identity.CurrentUser;
import com.ingsis.snippets.snippet.model.Snippet;
import com.ingsis.snippets.snippet.repository.SnippetRepository;
import com.ingsis.snippets.tests.dto.TestRequest;
import com.ingsis.snippets.tests.dto.TestRunResult;
import com.ingsis.snippets.tests.dto.TestView;
import com.ingsis.snippets.tests.model.SnippetTest;
import com.ingsis.snippets.tests.model.TestStatus;
import com.ingsis.snippets.tests.repository.SnippetTestRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD y ejecución de tests por snippet (US 8, 9). El assert lo hace Language Service:
 * este servicio solo traduce {@code passed} a {@code PASSED}/{@code FAILED} (D12).
 */
@Service
public class SnippetTestService {

	private static final Logger log = LoggerFactory.getLogger(SnippetTestService.class);

	private final SnippetTestRepository tests;

	private final SnippetRepository snippets;

	private final AuthorizationService authorization;

	private final LanguageClient language;

	public SnippetTestService(SnippetTestRepository tests, SnippetRepository snippets,
			AuthorizationService authorization, LanguageClient language) {
		this.tests = tests;
		this.snippets = snippets;
		this.authorization = authorization;
		this.language = language;
	}

	@Transactional
	public TestView create(CurrentUser user, UUID snippetId, TestRequest request) {
		authorization.require(user, snippetId, PermissionAction.WRITE);
		requireSnippet(snippetId);
		SnippetTest test = new SnippetTest(UUID.randomUUID(), snippetId, request.name(), request.inputs(),
			request.expectedOutputs());
		return TestView.of(tests.save(test));
	}

	@Transactional(readOnly = true)
	public List<TestView> list(CurrentUser user, UUID snippetId) {
		authorization.require(user, snippetId, PermissionAction.READ);
		return tests.findBySnippetId(snippetId).stream().map(TestView::of).toList();
	}

	@Transactional
	public TestView update(CurrentUser user, UUID snippetId, UUID testId, TestRequest request) {
		authorization.require(user, snippetId, PermissionAction.WRITE);
		SnippetTest test = requireTest(snippetId, testId);
		test.setName(request.name());
		test.setInputs(request.inputs());
		test.setExpectedOutputs(request.expectedOutputs());
		return TestView.of(tests.save(test));
	}

	@Transactional
	public void delete(CurrentUser user, UUID snippetId, UUID testId) {
		authorization.require(user, snippetId, PermissionAction.WRITE);
		SnippetTest test = requireTest(snippetId, testId);
		tests.delete(test);
	}

	@Transactional
	public TestRunResult run(CurrentUser user, UUID snippetId, UUID testId) {
		authorization.require(user, snippetId, PermissionAction.READ);
		Snippet snippet = requireSnippet(snippetId);
		SnippetTest test = requireTest(snippetId, testId);

		TestResult result = language.test(snippet.getLanguage(), snippet.getContent(), snippet.getVersion(),
			test.getInputs(), test.getExpectedOutputs());
		TestStatus status = result.passed() ? TestStatus.PASSED : TestStatus.FAILED;
		test.recordRun(status);
		tests.save(test);
		return new TestRunResult(testId, status, result.expectedOutputs(), result.actualOutputs(), result.error());
	}

	/**
	 * Corre todos los tests de un snippet uno por uno (US 16, D13). Un fallo o una falla
	 * de Language no corta los demás. Pensado para el listener de cambio de contenido.
	 */
	@Transactional
	public void runAll(UUID snippetId) {
		Snippet snippet = snippets.findById(snippetId).orElse(null);
		if (snippet == null) {
			return;
		}
		for (SnippetTest test : tests.findBySnippetId(snippetId)) {
			try {
				TestResult result = language.test(snippet.getLanguage(), snippet.getContent(), snippet.getVersion(),
					test.getInputs(), test.getExpectedOutputs());
				test.recordRun(result.passed() ? TestStatus.PASSED : TestStatus.FAILED);
			}
			catch (RuntimeException ex) {
				test.recordRun(TestStatus.FAILED);
				log.warn("Auto-run failed for test {} of snippet {}", test.getId(), snippetId, ex);
			}
			tests.save(test);
		}
	}

	private Snippet requireSnippet(UUID snippetId) {
		return snippets.findById(snippetId)
			.orElseThrow(() -> new NotFoundException("Snippet " + snippetId + " not found"));
	}

	private SnippetTest requireTest(UUID snippetId, UUID testId) {
		return tests.findByIdAndSnippetId(testId, snippetId)
			.orElseThrow(() -> new NotFoundException("Test " + testId + " not found"));
	}

}
