package com.ingsis.snippets.tests.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ingsis.snippets.authorization.AuthorizationService;
import com.ingsis.snippets.clients.language.LanguageClient;
import com.ingsis.snippets.clients.language.TestResult;
import com.ingsis.snippets.clients.permission.PermissionAction;
import com.ingsis.snippets.clients.permission.PermissionRole;
import com.ingsis.snippets.common.error.DownstreamException;
import com.ingsis.snippets.common.error.NotFoundException;
import com.ingsis.snippets.common.identity.CurrentUser;
import com.ingsis.snippets.snippet.model.Snippet;
import com.ingsis.snippets.snippet.repository.SnippetRepository;
import com.ingsis.snippets.tests.dto.TestRequest;
import com.ingsis.snippets.tests.dto.TestRunResult;
import com.ingsis.snippets.tests.model.SnippetTest;
import com.ingsis.snippets.tests.model.TestStatus;
import com.ingsis.snippets.tests.repository.SnippetTestRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SnippetTestServiceTest {

	private final CurrentUser ana = new CurrentUser("user-ana");

	@Mock
	private SnippetTestRepository testsRepo;

	@Mock
	private SnippetRepository snippets;

	@Mock
	private AuthorizationService authorization;

	@Mock
	private LanguageClient language;

	private SnippetTestService service;

	@BeforeEach
	void setUp() {
		service = new SnippetTestService(testsRepo, snippets, authorization, language);
	}

	private Snippet snippet(UUID id) {
		return new Snippet(id, "n", null, "printscript", "1.1", "println(name);", "user-ana");
	}

	private SnippetTest test(UUID snippetId, UUID testId) {
		return new SnippetTest(testId, snippetId, "t", List.of("Ana"), List.of("Hola Ana"));
	}

	@Test
	void createRequiresWriteAndStartsNotRun() {
		UUID snippetId = UUID.randomUUID();
		given(authorization.require(ana, snippetId, PermissionAction.WRITE)).willReturn(PermissionRole.OWNER);
		given(snippets.findById(snippetId)).willReturn(Optional.of(snippet(snippetId)));
		given(testsRepo.save(any(SnippetTest.class))).willAnswer(i -> i.getArgument(0));

		var view = service.create(ana, snippetId, new TestRequest("t", List.of("Ana"), List.of("Hola Ana")));

		assertThat(view.lastStatus()).isEqualTo(TestStatus.NOT_RUN);
		assertThat(view.inputs()).containsExactly("Ana");
	}

	@Test
	void runPassedStoresPassed() {
		UUID snippetId = UUID.randomUUID();
		UUID testId = UUID.randomUUID();
		given(authorization.require(ana, snippetId, PermissionAction.READ)).willReturn(PermissionRole.READ);
		given(snippets.findById(snippetId)).willReturn(Optional.of(snippet(snippetId)));
		given(testsRepo.findByIdAndSnippetId(testId, snippetId)).willReturn(Optional.of(test(snippetId, testId)));
		given(language.test(eq("printscript"), any(), any(), any(), any()))
			.willReturn(new TestResult(true, List.of("Hola Ana"), List.of("Hola Ana"), null));

		TestRunResult result = service.run(ana, snippetId, testId);

		assertThat(result.status()).isEqualTo(TestStatus.PASSED);
		verify(testsRepo).save(any(SnippetTest.class));
	}

	@Test
	void runNotPassedStoresFailed() {
		UUID snippetId = UUID.randomUUID();
		UUID testId = UUID.randomUUID();
		given(authorization.require(ana, snippetId, PermissionAction.READ)).willReturn(PermissionRole.READ);
		given(snippets.findById(snippetId)).willReturn(Optional.of(snippet(snippetId)));
		given(testsRepo.findByIdAndSnippetId(testId, snippetId)).willReturn(Optional.of(test(snippetId, testId)));
		given(language.test(any(), any(), any(), any(), any()))
			.willReturn(new TestResult(false, List.of("Hola Ana"), List.of("Hola  Ana"), null));

		TestRunResult result = service.run(ana, snippetId, testId);

		assertThat(result.status()).isEqualTo(TestStatus.FAILED);
		assertThat(result.actualOutputs()).containsExactly("Hola  Ana");
	}

	@Test
	void runPropagatesDownstreamFailure() {
		UUID snippetId = UUID.randomUUID();
		UUID testId = UUID.randomUUID();
		given(authorization.require(ana, snippetId, PermissionAction.READ)).willReturn(PermissionRole.READ);
		given(snippets.findById(snippetId)).willReturn(Optional.of(snippet(snippetId)));
		given(testsRepo.findByIdAndSnippetId(testId, snippetId)).willReturn(Optional.of(test(snippetId, testId)));
		given(language.test(any(), any(), any(), any(), any())).willThrow(new DownstreamException("lang down", null));

		assertThatThrownBy(() -> service.run(ana, snippetId, testId)).isInstanceOf(DownstreamException.class);
	}

	@Test
	void testOfAnotherSnippetIsNotFound() {
		UUID snippetId = UUID.randomUUID();
		UUID testId = UUID.randomUUID();
		given(authorization.require(ana, snippetId, PermissionAction.WRITE)).willReturn(PermissionRole.OWNER);
		given(testsRepo.findByIdAndSnippetId(testId, snippetId)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.update(ana, snippetId, testId,
			new TestRequest("t", List.of(), List.of()))).isInstanceOf(NotFoundException.class);
	}

	@Test
	void runAllToleratesFailuresAndKeepsGoing() {
		UUID snippetId = UUID.randomUUID();
		SnippetTest good = test(snippetId, UUID.randomUUID());
		SnippetTest bad = test(snippetId, UUID.randomUUID());
		given(snippets.findById(snippetId)).willReturn(Optional.of(snippet(snippetId)));
		given(testsRepo.findBySnippetId(snippetId)).willReturn(List.of(bad, good));
		given(language.test(any(), any(), any(), any(), any())).willThrow(new DownstreamException("boom", null))
			.willReturn(new TestResult(true, List.of("Hola Ana"), List.of("Hola Ana"), null));

		service.runAll(snippetId);

		assertThat(bad.getLastStatus()).isEqualTo(TestStatus.FAILED);
		assertThat(good.getLastStatus()).isEqualTo(TestStatus.PASSED);
		verify(testsRepo, org.mockito.Mockito.times(2)).save(any(SnippetTest.class));
	}

	@Test
	void runAllOnMissingSnippetDoesNothing() {
		UUID snippetId = UUID.randomUUID();
		given(snippets.findById(snippetId)).willReturn(Optional.empty());

		service.runAll(snippetId);

		verify(testsRepo, org.mockito.Mockito.never()).save(any());
	}

}
