package com.ingsis.snippets.download;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.ingsis.snippets.common.error.NotFoundException;
import com.ingsis.snippets.common.identity.CurrentUser;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DownloadController.class)
class DownloadControllerTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private DownloadService service;

	@Test
	void returnsPlainTextWithAttachmentHeader() throws Exception {
		UUID id = UUID.randomUUID();
		given(service.download(any(CurrentUser.class), eq(id), eq(false)))
			.willReturn(new DownloadContent("Hola.ps", "println(1);"));

		mvc.perform(get("/snippets/" + id + "/download").header("X-User-Id", "user-ana"))
			.andExpect(status().isOk())
			.andExpect(content().string("println(1);"))
			.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("Hola.ps")))
			.andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/plain")));
	}

	@Test
	void passesFormattedFlag() throws Exception {
		UUID id = UUID.randomUUID();
		given(service.download(any(CurrentUser.class), eq(id), eq(true)))
			.willReturn(new DownloadContent("Hola.ps", "formatted"));

		mvc.perform(get("/snippets/" + id + "/download").param("formatted", "true").header("X-User-Id", "user-ana"))
			.andExpect(status().isOk())
			.andExpect(content().string("formatted"));
	}

	@Test
	void unknownSnippetIsNotFound() throws Exception {
		UUID id = UUID.randomUUID();
		given(service.download(any(CurrentUser.class), eq(id), eq(false))).willThrow(new NotFoundException("nope"));

		mvc.perform(get("/snippets/" + id + "/download").header("X-User-Id", "user-ana"))
			.andExpect(status().isNotFound());
	}

	@Test
	void missingHeaderIsUnauthenticated() throws Exception {
		mvc.perform(get("/snippets/" + UUID.randomUUID() + "/download"))
			.andExpect(status().isUnauthorized());
	}

}
