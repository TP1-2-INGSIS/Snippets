package com.ingsis.snippets.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(ErrorProbeController.class)
class GlobalExceptionHandlerTest {

	@Autowired
	private MockMvc mvc;

	private static MockHttpServletRequestBuilder withUser(MockHttpServletRequestBuilder request) {
		return request.header("X-User-Id", "user-ana");
	}

	@ParameterizedTest
	@CsvSource({
		"/probe/not-found,            404, NOT_FOUND,            Snippet not found",
		"/probe/forbidden,            403, FORBIDDEN,            Not allowed",
		"/probe/unsupported-language, 400, UNSUPPORTED_LANGUAGE, Language cobol is not supported",
		"/probe/bad-request,          400, BAD_REQUEST,          Invalid rules",
		"/probe/conflict,             409, ALREADY_SHARED,       Already shared",
		"/probe/downstream,           502, DOWNSTREAM_ERROR,     Language Service unavailable",
	})
	void mapsDomainExceptionsToTheirStatusAndCode(String path, int status, String code, String message)
			throws Exception {
		mvc.perform(withUser(get(path)))
			.andExpect(status().is(status))
			.andExpect(jsonPath("$.error").value(code))
			.andExpect(jsonPath("$.message").value(message))
			.andExpect(jsonPath("$.details", hasSize(0)));
	}

	@Test
	void invalidSnippetCarriesSourceErrorsAsDetails() throws Exception {
		mvc.perform(withUser(get("/probe/invalid-snippet")))
			.andExpect(status().is(422))
			.andExpect(jsonPath("$.error").value("INVALID_SNIPPET"))
			.andExpect(jsonPath("$.details[0].rule").value("UNEXPECTED_TOKEN"))
			.andExpect(jsonPath("$.details[0].line").value(1))
			.andExpect(jsonPath("$.details[0].column").value(16));
	}

	@Test
	void unexpectedExceptionIs500WithoutLeakingItsMessage() throws Exception {
		mvc.perform(withUser(get("/probe/unexpected")))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
			.andExpect(jsonPath("$.message", not(containsString("secret"))));
	}

	@Test
	void beanValidationErrorsListTheInvalidFields() throws Exception {
		mvc.perform(withUser(post("/probe/body")).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("BAD_REQUEST"))
			.andExpect(jsonPath("$.details[0].field").value("name"))
			.andExpect(jsonPath("$.details[0].message").isNotEmpty());
	}

	@Test
	void malformedBodyIsBadRequest() throws Exception {
		mvc.perform(withUser(post("/probe/body")).contentType(MediaType.APPLICATION_JSON).content("{not json"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("BAD_REQUEST"));
	}

	@Test
	void missingRequestParamIsBadRequest() throws Exception {
		mvc.perform(withUser(get("/probe/param")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("BAD_REQUEST"))
			.andExpect(jsonPath("$.message").isNotEmpty());
	}

	@Test
	void unknownRouteIsNotFoundWithOurBody() throws Exception {
		mvc.perform(withUser(get("/nope")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("NOT_FOUND"));
	}

	@Test
	void unsupportedMethodKeepsSpringStatusWithOurBody() throws Exception {
		mvc.perform(withUser(delete("/probe/not-found")))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
	}

}
