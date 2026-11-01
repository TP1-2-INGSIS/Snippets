package com.ingsis.snippets.rules.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import com.ingsis.snippets.common.error.UnsupportedLanguageException;
import com.ingsis.snippets.common.identity.CurrentUser;
import com.ingsis.snippets.rules.dto.RuleConfigResponse;
import com.ingsis.snippets.rules.model.RuleConfig;
import com.ingsis.snippets.rules.model.RuleKind;
import com.ingsis.snippets.rules.repository.RuleConfigRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RulesServiceTest {

	private final CurrentUser ana = new CurrentUser("user-ana");

	@Mock
	private RuleConfigRepository repository;

	private RulesService service;

	@BeforeEach
	void setUp() {
		service = new RulesService(repository, new ObjectMapper());
	}

	@Test
	void getReturnsTheServiceDefaultWhenNothingIsSaved() {
		given(repository.findByUserIdAndLanguageAndKind("user-ana", "printscript", RuleKind.LINT))
			.willReturn(Optional.empty());

		RuleConfigResponse response = service.get(ana, "printscript", RuleKind.LINT);

		assertThat(response.isDefault()).isTrue();
		assertThat(response.config().has("identifierFormat")).isTrue();
	}

	@Test
	void getReturnsTheSavedConfigWhenPresent() {
		var saved = new ObjectMapper().readTree("{\"identifierFormat\":\"snake case\"}");
		given(repository.findByUserIdAndLanguageAndKind("user-ana", "printscript", RuleKind.LINT))
			.willReturn(Optional.of(new RuleConfig("user-ana", "printscript", RuleKind.LINT, saved)));

		RuleConfigResponse response = service.get(ana, "printscript", RuleKind.LINT);

		assertThat(response.isDefault()).isFalse();
		assertThat(response.config().get("identifierFormat").asString()).isEqualTo("snake case");
	}

	@Test
	void getOnUnsupportedLanguageFails() {
		given(repository.findByUserIdAndLanguageAndKind("user-ana", "cobol", RuleKind.LINT))
			.willReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(ana, "cobol", RuleKind.LINT))
			.isInstanceOf(UnsupportedLanguageException.class);
	}

	@Test
	void saveUpsertsAndReturnsNonDefault() {
		var config = new ObjectMapper().readTree("{\"identifierFormat\":\"snake case\"}");
		given(repository.findByUserIdAndLanguageAndKind("user-ana", "printscript", RuleKind.LINT))
			.willReturn(Optional.empty());

		RuleConfigResponse response = service.save(ana, "printscript", RuleKind.LINT, config);

		assertThat(response.isDefault()).isFalse();
		assertThat(response.config()).isEqualTo(config);
		verify(repository).save(any(RuleConfig.class));
	}

	@Test
	void saveOnUnsupportedLanguageDoesNotPersist() {
		var config = new ObjectMapper().readTree("{\"x\":1}");

		assertThatThrownBy(() -> service.save(ana, "cobol", RuleKind.LINT, config))
			.isInstanceOf(UnsupportedLanguageException.class);
		verify(repository, never()).save(any());
	}

}
