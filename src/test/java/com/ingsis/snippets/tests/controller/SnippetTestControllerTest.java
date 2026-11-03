package com.ingsis.snippets.tests.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.ingsis.snippets.common.error.NotFoundException;
import com.ingsis.snippets.common.identity.CurrentUser;
import com.ingsis.snippets.tests.dto.TestRunResult;
import com.ingsis.snippets.tests.dto.TestView;
import com.ingsis.snippets.tests.model.TestStatus;
import com.ingsis.snippets.tests.service.SnippetTestService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SnippetTestController.class)
class SnippetTestControllerTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private SnippetTestService service;

	@Test
	void createReturns201WithNotRun() throws Exception {
		UUID snippetId = UUID.randomUUID();
		UUID testId = UUID.randomUUID();
		given(service.create(any(CurrentUser.class), eq(snippetId), any()))
			.willReturn(new TestView(testId, "t", List.of("Ana"), List.of("Hola Ana"), TestStatus.NOT_RUN));

		mvc.perform(post("/snippets/" + snippetId + "/tests").header("X-User-Id", "user-ana")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"t\",\"inputs\":[\"Ana\"],\"expectedOutputs\":[\"Hola Ana\"]}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.lastStatus").value("NOT_RUN"))
			.andExpect(jsonPath("$.inputs[0]").value("Ana"));
	}

	@Test
	void createWithBlankNameIsBadRequest() throws Exception {
		mvc.perform(post("/snippets/" + UUID.randomUUID() + "/tests").header("X-User-Id", "user-ana")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"\",\"inputs\":[],\"expectedOutputs\":[]}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("BAD_REQUEST"));
	}

	@Test
	void listReturnsTests() throws Exception {
		UUID snippetId = UUID.randomUUID();
		given(service.list(any(CurrentUser.class), eq(snippetId)))
			.willReturn(List.of(new TestView(UUID.randomUUID(), "t", List.of(), List.of(), TestStatus.PASSED)));

		mvc.perform(get("/snippets/" + snippetId + "/tests").header("X-User-Id", "user-ana"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].lastStatus").value("PASSED"));
	}

	@Test
	void deleteReturns204() throws Exception {
		UUID snippetId = UUID.randomUUID();
		UUID testId = UUID.randomUUID();

		mvc.perform(delete("/snippets/" + snippetId + "/tests/" + testId).header("X-User-Id", "user-ana"))
			.andExpect(status().isNoContent());
		verify(service).delete(any(CurrentUser.class), eq(snippetId), eq(testId));
	}

	@Test
	void runReturnsResult() throws Exception {
		UUID snippetId = UUID.randomUUID();
		UUID testId = UUID.randomUUID();
		given(service.run(any(CurrentUser.class), eq(snippetId), eq(testId)))
			.willReturn(new TestRunResult(testId, TestStatus.PASSED, List.of("Hola Ana"), List.of("Hola Ana"), null));

		mvc.perform(post("/snippets/" + snippetId + "/tests/" + testId + "/run").header("X-User-Id", "user-ana"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PASSED"))
			.andExpect(jsonPath("$.actualOutputs[0]").value("Hola Ana"));
	}

	@Test
	void runOnTestOfAnotherSnippetIsNotFound() throws Exception {
		UUID snippetId = UUID.randomUUID();
		UUID testId = UUID.randomUUID();
		given(service.run(any(CurrentUser.class), eq(snippetId), eq(testId))).willThrow(new NotFoundException("nope"));

		mvc.perform(post("/snippets/" + snippetId + "/tests/" + testId + "/run").header("X-User-Id", "user-ana"))
			.andExpect(status().isNotFound());
	}

	@Test
	void missingHeaderIsUnauthenticated() throws Exception {
		mvc.perform(get("/snippets/" + UUID.randomUUID() + "/tests"))
			.andExpect(status().isUnauthorized());
	}

}
