package com.ingsis.snippets.common.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(IdentityProbeController.class)
class CurrentUserTest {

  @Autowired private MockMvc mvc;

  private static String createJwt(String payloadJson) {
    String header =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
    String payload =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
    return header + "." + payload + ".sig";
  }

  @Test
  void injectsTheUserFromTheHeader() throws Exception {
    mvc.perform(get("/probe/me").header("X-User-Id", " user-ana "))
        .andExpect(status().isOk())
        .andExpect(content().string("user-ana"));
  }

  @Test
  void injectsUserFromBearerToken() throws Exception {
    String token = createJwt("{\"sub\":\"auth0|usr_12345\"}");
    mvc.perform(get("/probe/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(content().string("auth0|usr_12345"));
  }

  @Test
  void invalidBearerTokenFormatIsUnauthenticated() throws Exception {
    mvc.perform(get("/probe/me").header("Authorization", "Bearer not-a-valid-jwt"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"))
        .andExpect(jsonPath("$.message").value("Invalid bearer token format"));
  }

  @Test
  void bearerTokenMissingSubIsUnauthenticated() throws Exception {
    String token = createJwt("{\"iss\":\"auth0\"}");
    mvc.perform(get("/probe/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"))
        .andExpect(jsonPath("$.message").value("Missing sub claim in token"));
  }

  @Test
  void malformedPayloadIsUnauthenticated() throws Exception {
    mvc.perform(get("/probe/me").header("Authorization", "Bearer aaa.invalidjson.ccc"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"))
        .andExpect(jsonPath("$.message").value("Malformed bearer token payload"));
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
    mvc.perform(get("/nope")).andExpect(status().isUnauthorized());
  }
}
