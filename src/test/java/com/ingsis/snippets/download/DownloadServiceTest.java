package com.ingsis.snippets.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.ingsis.snippets.authorization.AuthorizationService;
import com.ingsis.snippets.clients.language.LanguageClient;
import com.ingsis.snippets.clients.permission.PermissionAction;
import com.ingsis.snippets.clients.permission.PermissionRole;
import com.ingsis.snippets.common.identity.CurrentUser;
import com.ingsis.snippets.rules.dto.RuleConfigResponse;
import com.ingsis.snippets.rules.model.RuleKind;
import com.ingsis.snippets.rules.service.RulesService;
import com.ingsis.snippets.snippet.model.Snippet;
import com.ingsis.snippets.snippet.repository.SnippetRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class DownloadServiceTest {

	private final CurrentUser downloader = new CurrentUser("user-juan");

	@Mock
	private AuthorizationService authorization;

	@Mock
	private SnippetRepository snippets;

	@Mock
	private LanguageClient language;

	@Mock
	private RulesService rules;

	private DownloadService service;

	@BeforeEach
	void setUp() {
		service = new DownloadService(authorization, snippets, language, rules,
			new DownloadProperties(Map.of("printscript", "ps"), "txt"));
	}

	private Snippet ownedByAna(UUID id) {
		return new Snippet(id, "Hola", null, "printscript", "1.1", "println(1);", "user-ana");
	}

	@Test
	void originalDownloadReturnsRawContentAndFilename() {
		UUID id = UUID.randomUUID();
		given(authorization.require(downloader, id, PermissionAction.READ)).willReturn(PermissionRole.READ);
		given(snippets.findById(id)).willReturn(Optional.of(ownedByAna(id)));

		DownloadContent result = service.download(downloader, id, false);

		assertThat(result.content()).isEqualTo("println(1);");
		assertThat(result.filename()).isEqualTo("Hola.ps");
		verify(language, never()).format(any(), any(), any(), any());
	}

	@Test
	void formattedDownloadUsesTheOwnersFormatRulesNotTheDownloaders() {
		UUID id = UUID.randomUUID();
		var ownerRules = new ObjectMapper().readTree("{\"owner\":true}");
		given(authorization.require(downloader, id, PermissionAction.READ)).willReturn(PermissionRole.READ);
		given(snippets.findById(id)).willReturn(Optional.of(ownedByAna(id)));
		given(rules.get(any(CurrentUser.class), eq("printscript"), eq(RuleKind.FORMAT)))
			.willReturn(new RuleConfigResponse("printscript", RuleKind.FORMAT, false, ownerRules));
		given(language.format(eq("printscript"), eq("println(1);"), eq("1.1"), eq(ownerRules)))
			.willReturn("formatted!");

		DownloadContent result = service.download(downloader, id, true);

		assertThat(result.content()).isEqualTo("formatted!");

		// The rules must be read for the OWNER (user-ana), not the downloader (user-juan).
		ArgumentCaptor<CurrentUser> captor = ArgumentCaptor.forClass(CurrentUser.class);
		verify(rules).get(captor.capture(), eq("printscript"), eq(RuleKind.FORMAT));
		assertThat(captor.getValue().id()).isEqualTo("user-ana");
	}

	@Test
	void fallbackExtensionForUnknownLanguage() {
		UUID id = UUID.randomUUID();
		Snippet python = new Snippet(id, "Script", null, "python", "3.12", "print(1)", "user-ana");
		given(authorization.require(downloader, id, PermissionAction.READ)).willReturn(PermissionRole.READ);
		given(snippets.findById(id)).willReturn(Optional.of(python));

		DownloadContent result = service.download(downloader, id, false);

		assertThat(result.filename()).isEqualTo("Script.txt");
	}

}
