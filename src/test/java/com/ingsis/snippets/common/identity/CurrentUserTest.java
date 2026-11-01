package com.ingsis.snippets.common.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(IdentityProbeController.class)
class CurrentUserTest {

	@Autowired
	private MockMvc mvc;

	@Test
	void injectsTheUserFromTheHeader() throws Exception {
		mvc.perform(get("/probe/me").header("X-User-Id", " user-ana "))
			.andExpect(status().isOk())
			.andExpect(content().string("user-ana"));
	}

	@Test
	void missingHeaderIsUnauthenticated() throws Exception {
		mvc.perform(get("/probe/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("UNAUTHENTICATED"))
			.andExpect(jsonPath("$.message").value("Missing X-User-Id header"));
	}

	@Test
	void blankHeaderIsUnauthenticated() throws Exception {
		mvc.perform(get("/probe/me").header("X-User-Id", "   "))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
	}

	@Test
	void everyRouteRequiresTheHeaderEvenUnknownOnes() throws Exception {
		mvc.perform(get("/nope"))
			.andExpect(status().isUnauthorized());
	}

}
