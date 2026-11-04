package com.ingsis.snippets.sharing.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ingsis.snippets.clients.permission.UserSummary;
import com.ingsis.snippets.common.error.ConflictException;
import com.ingsis.snippets.common.error.ErrorCode;
import com.ingsis.snippets.common.identity.CurrentUser;
import com.ingsis.snippets.sharing.service.SharingService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SharingController.class)
class SharingControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private SharingService sharing;

  @Test
  void searchUsersReturnsSummaries() throws Exception {
    given(sharing.searchUsers(eq("an"), any(Integer.class)))
        .willReturn(List.of(new UserSummary("user-ana", "Ana", "ana@x.com")));

    mvc.perform(get("/users").param("search", "an").header("X-User-Id", "user-ana"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("user-ana"))
        .andExpect(jsonPath("$[0].email").value("ana@x.com"));
  }

  @Test
  void shareReturns204() throws Exception {
    UUID id = UUID.randomUUID();

    mvc.perform(
            post("/snippets/" + id + "/share")
                .header("X-User-Id", "user-ana")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"user-juan\"}"))
        .andExpect(status().isNoContent());
    verify(sharing).share(any(CurrentUser.class), eq(id), eq("user-juan"));
  }

  @Test
  void shareAlreadySharedIsConflict() throws Exception {
    UUID id = UUID.randomUUID();
    org.mockito.BDDMockito.willThrow(
            new ConflictException(ErrorCode.ALREADY_SHARED, "Already shared"))
        .given(sharing)
        .share(any(CurrentUser.class), eq(id), eq("user-juan"));

    mvc.perform(
            post("/snippets/" + id + "/share")
                .header("X-User-Id", "user-ana")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"user-juan\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("ALREADY_SHARED"));
  }

  @Test
  void shareWithBlankUserIdIsBadRequest() throws Exception {
    mvc.perform(
            post("/snippets/" + UUID.randomUUID() + "/share")
                .header("X-User-Id", "user-ana")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
  }

  @Test
  void missingHeaderIsUnauthenticated() throws Exception {
    mvc.perform(get("/users").param("search", "x")).andExpect(status().isUnauthorized());
  }
}
