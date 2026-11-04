package com.ingsis.snippets.languages;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ingsis.snippets.clients.language.LanguageClient;
import com.ingsis.snippets.clients.language.LanguageInfo;
import com.ingsis.snippets.common.error.DownstreamException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LanguageController.class)
class LanguageControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private LanguageClient languages;

  @Test
  void returnsTheLanguagesFromTheClient() throws Exception {
    given(languages.languages())
        .willReturn(
            List.of(
                new LanguageInfo("printscript", List.of("1.0", "1.1")),
                new LanguageInfo("python", List.of("3.12"))));

    mvc.perform(get("/languages").header("X-User-Id", "user-ana"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].id").value("printscript"))
        .andExpect(jsonPath("$[0].versions[0]").value("1.0"))
        .andExpect(jsonPath("$[0].versions[1]").value("1.1"))
        .andExpect(jsonPath("$[1].id").value("python"))
        .andExpect(jsonPath("$[1].versions[0]").value("3.12"));
  }

  @Test
  void whenLanguageServiceFailsRespondsBadGateway() throws Exception {
    given(languages.languages())
        .willThrow(new DownstreamException("Language Service unavailable", null));

    mvc.perform(get("/languages").header("X-User-Id", "user-ana"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error").value("DOWNSTREAM_ERROR"));
  }

  @Test
  void missingHeaderIsUnauthenticated() throws Exception {
    mvc.perform(get("/languages"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
  }
}
