package com.ingsis.snippets.rules.controller;

import com.ingsis.snippets.common.identity.CurrentUser;
import com.ingsis.snippets.rules.dto.RuleApplyResponse;
import com.ingsis.snippets.rules.dto.RuleConfigRequest;
import com.ingsis.snippets.rules.dto.RuleConfigResponse;
import com.ingsis.snippets.rules.model.RuleKind;
import com.ingsis.snippets.rules.service.RulesService;
import com.ingsis.snippets.snippet.service.RuleApplier;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reglas de lint/format por usuario y lenguaje (US 11, 14). El {@code PUT} guarda la config y
 * además encola la aplicación a todos los snippets OWNER del usuario en ese lenguaje (US 12, 15):
 * responde 202 y el resultado se procesa en segundo plano (D14).
 */
@RestController
class RulesController {

  private final RulesService rules;

  private final RuleApplier applier;

  RulesController(RulesService rules, RuleApplier applier) {
    this.rules = rules;
    this.applier = applier;
  }

  @GetMapping("/rules/{language}/lint")
  RuleConfigResponse getLint(CurrentUser user, @PathVariable String language) {
    return rules.get(user, language, RuleKind.LINT);
  }

  @GetMapping("/rules/{language}/format")
  RuleConfigResponse getFormat(CurrentUser user, @PathVariable String language) {
    return rules.get(user, language, RuleKind.FORMAT);
  }

  @PutMapping("/rules/{language}/lint")
  @ResponseStatus(HttpStatus.ACCEPTED)
  RuleApplyResponse putLint(
      CurrentUser user,
      @PathVariable String language,
      @Valid @RequestBody RuleConfigRequest request) {
    return applier.saveAndEnqueue(user, language, RuleKind.LINT, request.config());
  }

  @PutMapping("/rules/{language}/format")
  @ResponseStatus(HttpStatus.ACCEPTED)
  RuleApplyResponse putFormat(
      CurrentUser user,
      @PathVariable String language,
      @Valid @RequestBody RuleConfigRequest request) {
    return applier.saveAndEnqueue(user, language, RuleKind.FORMAT, request.config());
  }
}
