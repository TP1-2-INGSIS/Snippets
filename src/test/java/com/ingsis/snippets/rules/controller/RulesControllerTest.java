package com.ingsis.snippets.rules.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ingsis.snippets.common.error.UnsupportedLanguageException;
import com.ingsis.snippets.common.identity.CurrentUser;
import com.ingsis.snippets.rules.dto.RuleConfigResponse;
import com.ingsis.snippets.rules.model.RuleKind;
import com.ingsis.snippets.rules.service.RulesService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(RulesController.class)
class RulesControllerTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private RulesService rules;

	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void getLintReturnsTheDefaultWithIsDefaultTrue() throws Exception {
		given(rules.get(any(CurrentUser.class), eq("printscript"), eq(RuleKind.LINT)))
			.willReturn(new RuleConfigResponse("printscript", RuleKind.LINT, true,
				mapper.readTree("{\"identifierFormat\":\"camel case\"}")));

		mvc.perform(get("/rules/printscript/lint").header("X-User-Id", "user-ana"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.language").value("printscript"))
			.andExpect(jsonPath("$.kind").value("LINT"))
			.andExpect(jsonPath("$.isDefault").value(true))
			.andExpect(jsonPath("$.config.identifierFormat").value("camel case"));
	}

	@Test
	void putLintSavesAndReturnsIsDefaultFalse() throws Exception {
		given(rules.save(any(CurrentUser.class), eq("printscript"), eq(RuleKind.LINT), any()))
			.willReturn(new RuleConfigResponse("printscript", RuleKind.LINT, false,
				mapper.readTree("{\"identifierFormat\":\"snake case\"}")));

		mvc.perform(put("/rules/printscript/lint").header("X-User-Id", "user-ana")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"config\":{\"identifierFormat\":\"snake case\"}}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.isDefault").value(false))
			.andExpect(jsonPath("$.config.identifierFormat").value("snake case"));
	}

	@Test
	void getFormatDelegatesWithFormatKind() throws Exception {
		given(rules.get(any(CurrentUser.class), eq("printscript"), eq(RuleKind.FORMAT)))
			.willReturn(new RuleConfigResponse("printscript", RuleKind.FORMAT, true, mapper.readTree("{}")));

		mvc.perform(get("/rules/printscript/format").header("X-User-Id", "user-ana"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.kind").value("FORMAT"));
	}

	@Test
	void unsupportedLanguageIsBadRequest() throws Exception {
		given(rules.get(any(CurrentUser.class), eq("cobol"), eq(RuleKind.LINT)))
			.willThrow(new UnsupportedLanguageException("Language cobol is not supported"));

		mvc.perform(get("/rules/cobol/lint").header("X-User-Id", "user-ana"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("UNSUPPORTED_LANGUAGE"));
	}

	@Test
	void missingHeaderIsUnauthenticated() throws Exception {
		mvc.perform(get("/rules/printscript/lint"))
			.andExpect(status().isUnauthorized());
	}

}
