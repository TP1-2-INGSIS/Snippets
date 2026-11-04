package com.ingsis.snippets.snippet.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ingsis.snippets.clients.permission.PermissionRole;
import com.ingsis.snippets.common.error.NotFoundException;
import com.ingsis.snippets.common.identity.CurrentUser;
import com.ingsis.snippets.snippet.dto.SnippetDetail;
import com.ingsis.snippets.snippet.dto.SnippetPage;
import com.ingsis.snippets.snippet.dto.SnippetSummary;
import com.ingsis.snippets.snippet.model.LintStatus;
import com.ingsis.snippets.snippet.model.Relation;
import com.ingsis.snippets.snippet.service.SnippetService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SnippetController.class)
class SnippetControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private SnippetService service;

  private SnippetDetail detail(UUID id) {
    return new SnippetDetail(
        id,
        "Hola",
        "d",
        "printscript",
        "1.1",
        "println(1);",
        "user-ana",
        PermissionRole.OWNER,
        LintStatus.COMPLIANT,
        List.of(),
        List.of("user-juan"),
        Instant.now(),
        Instant.now());
  }

  @Test
  void createReturns201WithDetail() throws Exception {
    UUID id = UUID.randomUUID();
    given(
            service.create(
                any(CurrentUser.class), eq("Hola"), any(), eq("printscript"), eq("1.1"), any()))
        .willReturn(detail(id));

    mvc.perform(
            post("/snippets")
                .header("X-User-Id", "user-ana")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Hola\",\"description\":\"d\",\"language\":\"printscript\","
                        + "\"version\":\"1.1\",\"content\":\"println(1);\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.role").value("OWNER"));
  }

  @Test
  void createWithBlankNameIsBadRequest() throws Exception {
    mvc.perform(
            post("/snippets")
                .header("X-User-Id", "user-ana")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"\",\"language\":\"printscript\",\"version\":\"1.1\",\"content\":\"x\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
  }

  @Test
  void uploadCreatesFromMultipart() throws Exception {
    UUID id = UUID.randomUUID();
    given(
            service.create(
                any(CurrentUser.class),
                eq("Hola"),
                any(),
                eq("printscript"),
                eq("1.1"),
                eq("println(1);")))
        .willReturn(detail(id));
    var file = new MockMultipartFile("file", "hola.ps", "text/plain", "println(1);".getBytes());

    mvc.perform(
            multipart("/snippets/upload")
                .file(file)
                .param("name", "Hola")
                .param("language", "printscript")
                .param("version", "1.1")
                .header("X-User-Id", "user-ana"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(id.toString()));
  }

  @Test
  void getReturnsDetail() throws Exception {
    UUID id = UUID.randomUUID();
    given(service.get(any(CurrentUser.class), eq(id))).willReturn(detail(id));

    mvc.perform(get("/snippets/" + id).header("X-User-Id", "user-ana"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sharedWith[0]").value("user-juan"));
  }

  @Test
  void getUnknownIsNotFound() throws Exception {
    UUID id = UUID.randomUUID();
    given(service.get(any(CurrentUser.class), eq(id))).willThrow(new NotFoundException("nope"));

    mvc.perform(get("/snippets/" + id).header("X-User-Id", "user-ana"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }

  @Test
  void listUsesDefaultsAndReturnsPage() throws Exception {
    UUID id = UUID.randomUUID();
    var summary =
        new SnippetSummary(
            id,
            "Hola",
            "printscript",
            "1.1",
            "user-ana",
            PermissionRole.OWNER,
            LintStatus.COMPLIANT,
            Instant.now());
    given(service.list(any(CurrentUser.class), eq(Relation.ALL), any(), any(), any(), any()))
        .willReturn(new SnippetPage(List.of(summary), 0, 20, 1));

    mvc.perform(get("/snippets").header("X-User-Id", "user-ana"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(id.toString()))
        .andExpect(jsonPath("$.total").value(1));
  }

  @Test
  void listWithInvalidSortIsBadRequest() throws Exception {
    mvc.perform(get("/snippets").param("sort", "evil").header("X-User-Id", "user-ana"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
  }

  @Test
  void updateReturnsDetail() throws Exception {
    UUID id = UUID.randomUUID();
    given(service.update(any(CurrentUser.class), eq(id), any())).willReturn(detail(id));

    mvc.perform(
            put("/snippets/" + id)
                .header("X-User-Id", "user-ana")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"nuevo\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()));
  }

  @Test
  void deleteReturns204() throws Exception {
    UUID id = UUID.randomUUID();

    mvc.perform(delete("/snippets/" + id).header("X-User-Id", "user-ana"))
        .andExpect(status().isNoContent());
    verify(service).delete(any(CurrentUser.class), eq(id));
  }

  @Test
  void missingHeaderIsUnauthenticated() throws Exception {
    mvc.perform(get("/snippets/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
  }
}
