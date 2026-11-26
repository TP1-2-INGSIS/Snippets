package com.ingsis.snippets.snippet.controller;

import com.ingsis.snippets.common.identity.CurrentUser;
import com.ingsis.snippets.snippet.service.RuleApplier;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Re-encola la validación (lint) de los snippets propios; útil para demos de la revalidación. */
@RestController
class RevalidationController {

  private final RuleApplier applier;

  RevalidationController(RuleApplier applier) {
    this.applier = applier;
  }

  @PostMapping("/snippets/revalidate")
  @ResponseStatus(HttpStatus.ACCEPTED)
  Map<String, Integer> revalidate(CurrentUser user) {
    return Map.of("queued", applier.revalidateOwned(user));
  }
}
