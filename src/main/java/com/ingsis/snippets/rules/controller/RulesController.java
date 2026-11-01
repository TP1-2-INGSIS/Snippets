package com.ingsis.snippets.rules.controller;

import jakarta.validation.Valid;

import com.ingsis.snippets.common.identity.CurrentUser;
import com.ingsis.snippets.rules.dto.RuleConfigRequest;
import com.ingsis.snippets.rules.dto.RuleConfigResponse;
import com.ingsis.snippets.rules.model.RuleKind;
import com.ingsis.snippets.rules.service.RulesService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reglas de lint/format por usuario y lenguaje (US 11, 14). En este paso el {@code PUT}
 * solo guarda; aplicar las reglas a todos los snippets (US 12, 15) se agrega en el grupo C.
 */
@RestController
class RulesController {

	private final RulesService rules;

	RulesController(RulesService rules) {
		this.rules = rules;
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
	RuleConfigResponse putLint(CurrentUser user, @PathVariable String language,
			@Valid @RequestBody RuleConfigRequest request) {
		return rules.save(user, language, RuleKind.LINT, request.config());
	}

	@PutMapping("/rules/{language}/format")
	RuleConfigResponse putFormat(CurrentUser user, @PathVariable String language,
			@Valid @RequestBody RuleConfigRequest request) {
		return rules.save(user, language, RuleKind.FORMAT, request.config());
	}

}
