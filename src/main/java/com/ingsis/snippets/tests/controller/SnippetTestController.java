package com.ingsis.snippets.tests.controller;

import com.ingsis.snippets.common.identity.CurrentUser;
import com.ingsis.snippets.tests.dto.TestRequest;
import com.ingsis.snippets.tests.dto.TestRunResult;
import com.ingsis.snippets.tests.dto.TestView;
import com.ingsis.snippets.tests.service.SnippetTestService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** CRUD y ejecución de tests por snippet (US 8, 9). */
@RestController
class SnippetTestController {

  private final SnippetTestService tests;

  SnippetTestController(SnippetTestService tests) {
    this.tests = tests;
  }

  @PostMapping("/snippets/{id}/tests")
  @ResponseStatus(HttpStatus.CREATED)
  TestView create(
      CurrentUser user, @PathVariable UUID id, @Valid @RequestBody TestRequest request) {
    return tests.create(user, id, request);
  }

  @GetMapping("/snippets/{id}/tests")
  List<TestView> list(CurrentUser user, @PathVariable UUID id) {
    return tests.list(user, id);
  }

  @PutMapping("/snippets/{id}/tests/{testId}")
  TestView update(
      CurrentUser user,
      @PathVariable UUID id,
      @PathVariable UUID testId,
      @Valid @RequestBody TestRequest request) {
    return tests.update(user, id, testId, request);
  }

  @DeleteMapping("/snippets/{id}/tests/{testId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(CurrentUser user, @PathVariable UUID id, @PathVariable UUID testId) {
    tests.delete(user, id, testId);
  }

  @PostMapping("/snippets/{id}/tests/{testId}/run")
  TestRunResult run(CurrentUser user, @PathVariable UUID id, @PathVariable UUID testId) {
    return tests.run(user, id, testId);
  }
}
