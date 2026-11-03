package com.ingsis.snippets.snippet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ingsis.snippets.authorization.AuthorizationService;
import com.ingsis.snippets.clients.language.LanguageClient;
import com.ingsis.snippets.clients.language.SourceError;
import com.ingsis.snippets.clients.language.ValidationResult;
import com.ingsis.snippets.clients.permission.PermissionAction;
import com.ingsis.snippets.clients.permission.PermissionClient;
import com.ingsis.snippets.clients.permission.PermissionRole;
import com.ingsis.snippets.clients.permission.SnippetPermission;
import com.ingsis.snippets.clients.permission.UserPermission;
import com.ingsis.snippets.common.error.DownstreamException;
import com.ingsis.snippets.common.error.InvalidSnippetException;
import com.ingsis.snippets.common.identity.CurrentUser;
import com.ingsis.snippets.rules.dto.RuleConfigResponse;
import com.ingsis.snippets.rules.model.RuleKind;
import com.ingsis.snippets.rules.service.RulesService;
import com.ingsis.snippets.snippet.dto.SnippetDetail;
import com.ingsis.snippets.snippet.dto.UpdateSnippetRequest;
import com.ingsis.snippets.snippet.event.SnippetContentChanged;
import com.ingsis.snippets.snippet.model.LintStatus;
import com.ingsis.snippets.snippet.model.Relation;
import com.ingsis.snippets.snippet.model.Snippet;
import com.ingsis.snippets.snippet.repository.SnippetRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class SnippetServiceTest {

	private final CurrentUser ana = new CurrentUser("user-ana");

	@Mock
	private SnippetRepository snippets;

	@Mock
	private LanguageClient language;

	@Mock
	private PermissionClient permissions;

	@Mock
	private AuthorizationService authorization;

	@Mock
	private RulesService rules;

	@Mock
	private ApplicationEventPublisher events;

	private SnippetService service;

	@BeforeEach
	void setUp() {
		service = new SnippetService(snippets, language, permissions, authorization, rules, events);
	}

	private void stubLintRules() {
		given(rules.get(any(CurrentUser.class), any(), eq(RuleKind.LINT)))
			.willReturn(new RuleConfigResponse("printscript", RuleKind.LINT, true, new ObjectMapper().readTree("{}")));
	}

	@Test
	void createValidSnippetLintsSavesAndGrantsOwner() {
		given(language.validate("printscript", "println(1);", "1.1")).willReturn(new ValidationResult(true, List.of()));
		stubLintRules();
		given(language.lint(eq("printscript"), any(), any(), any())).willReturn(List.of());
		given(snippets.save(any(Snippet.class))).willAnswer(i -> i.getArgument(0));

		SnippetDetail detail = service.create(ana, "Hola", "d", "printscript", "1.1", "println(1);");

		assertThat(detail.ownerId()).isEqualTo("user-ana");
		assertThat(detail.role()).isEqualTo(PermissionRole.OWNER);
		assertThat(detail.lintStatus()).isEqualTo(LintStatus.COMPLIANT);
		verify(permissions).grant(any(UUID.class), eq("user-ana"), eq(PermissionRole.OWNER));
	}

	@Test
	void createInvalidSnippetDoesNotSaveNorGrant() {
		given(language.validate("printscript", "bad", "1.1"))
			.willReturn(new ValidationResult(false, List.of(new SourceError("UNEXPECTED_TOKEN", "boom", 1, 2))));

		assertThatThrownBy(() -> service.create(ana, "Hola", null, "printscript", "1.1", "bad"))
			.isInstanceOf(InvalidSnippetException.class);
		verify(snippets, never()).save(any());
		verify(permissions, never()).grant(any(), any(), any());
	}

	@Test
	void createCompensatesWhenGrantFails() {
		given(language.validate(any(), any(), any())).willReturn(new ValidationResult(true, List.of()));
		stubLintRules();
		given(language.lint(any(), any(), any(), any())).willReturn(List.of());
		given(snippets.save(any(Snippet.class))).willAnswer(i -> i.getArgument(0));
		org.mockito.BDDMockito.willThrow(new DownstreamException("perm down", null))
			.given(permissions)
			.grant(any(), any(), any());

		assertThatThrownBy(() -> service.create(ana, "Hola", null, "printscript", "1.1", "println(1);"))
			.isInstanceOf(DownstreamException.class);
		verify(snippets).delete(any(Snippet.class));
	}

	@Test
	void getAsOwnerIncludesSharedWith() {
		UUID id = UUID.randomUUID();
		given(authorization.require(ana, id, PermissionAction.READ)).willReturn(PermissionRole.OWNER);
		given(snippets.findById(id)).willReturn(Optional.of(snippet(id)));
		given(permissions.usersOf(id)).willReturn(List.of(new UserPermission("user-ana", PermissionRole.OWNER),
			new UserPermission("user-juan", PermissionRole.READ)));

		SnippetDetail detail = service.get(ana, id);

		assertThat(detail.sharedWith()).containsExactly("user-juan");
	}

	@Test
	void getAsReaderHasNoSharedWith() {
		UUID id = UUID.randomUUID();
		given(authorization.require(ana, id, PermissionAction.READ)).willReturn(PermissionRole.READ);
		given(snippets.findById(id)).willReturn(Optional.of(snippet(id)));

		SnippetDetail detail = service.get(ana, id);

		assertThat(detail.sharedWith()).isNull();
		verify(permissions, never()).usersOf(any());
	}

	@Test
	void listMapsRelationOwnedToOwnerRole() {
		UUID id = UUID.randomUUID();
		given(permissions.idsFor("user-ana", PermissionRole.OWNER))
			.willReturn(List.of(new SnippetPermission(id, PermissionRole.OWNER)));
		given(snippets.searchByName(any(), any(), any(), any(), any()))
			.willReturn(new PageImpl<>(List.of(snippet(id))));

		var page = service.list(ana, Relation.OWNED, null, null, null, PageRequest.of(0, 20));

		assertThat(page.items()).singleElement()
			.satisfies(item -> assertThat(item.role()).isEqualTo(PermissionRole.OWNER));
	}

	@Test
	void listReturnsEmptyWhenUserHasNoSnippets() {
		given(permissions.idsFor("user-ana", null)).willReturn(List.of());

		var page = service.list(ana, Relation.ALL, null, null, null, PageRequest.of(0, 20));

		assertThat(page.items()).isEmpty();
		assertThat(page.total()).isZero();
		verify(snippets, never()).searchByName(any(), any(), any(), any(), any());
	}

	@Test
	void updateMetadataOnlyDoesNotCallLanguage() {
		UUID id = UUID.randomUUID();
		given(authorization.require(ana, id, PermissionAction.WRITE)).willReturn(PermissionRole.OWNER);
		given(snippets.findById(id)).willReturn(Optional.of(snippet(id)));
		given(snippets.save(any(Snippet.class))).willAnswer(i -> i.getArgument(0));

		service.update(ana, id, new UpdateSnippetRequest("new name", null, null, null, null));

		verify(language, never()).validate(any(), any(), any());
		verify(events, never()).publishEvent(any());
	}

	@Test
	void updateContentRevalidatesRelintsAndPublishesEvent() {
		UUID id = UUID.randomUUID();
		given(authorization.require(ana, id, PermissionAction.WRITE)).willReturn(PermissionRole.OWNER);
		given(snippets.findById(id)).willReturn(Optional.of(snippet(id)));
		given(language.validate(any(), any(), any())).willReturn(new ValidationResult(true, List.of()));
		stubLintRules();
		given(language.lint(any(), any(), any(), any())).willReturn(List.of());
		given(snippets.save(any(Snippet.class))).willAnswer(i -> i.getArgument(0));

		service.update(ana, id, new UpdateSnippetRequest(null, null, null, null, "println(2);"));

		verify(language).validate("printscript", "println(2);", "1.1");
		verify(events).publishEvent(any(SnippetContentChanged.class));
	}

	@Test
	void updateContentInvalidDoesNotPersist() {
		UUID id = UUID.randomUUID();
		given(authorization.require(ana, id, PermissionAction.WRITE)).willReturn(PermissionRole.OWNER);
		given(snippets.findById(id)).willReturn(Optional.of(snippet(id)));
		given(language.validate(any(), any(), any()))
			.willReturn(new ValidationResult(false, List.of(new SourceError("E", "m", 1, 1))));

		assertThatThrownBy(() -> service.update(ana, id, new UpdateSnippetRequest(null, null, null, null, "oops")))
			.isInstanceOf(InvalidSnippetException.class);
		verify(snippets, never()).save(any());
	}

	@Test
	void deleteRemovesSnippetAndPermissions() {
		UUID id = UUID.randomUUID();
		given(authorization.require(ana, id, PermissionAction.WRITE)).willReturn(PermissionRole.OWNER);
		given(snippets.findById(id)).willReturn(Optional.of(snippet(id)));

		service.delete(ana, id);

		verify(snippets).delete(any(Snippet.class));
		verify(permissions).deleteAll(id);
	}

	@Test
	void deleteStillSucceedsWhenPermissionCleanupFails() {
		UUID id = UUID.randomUUID();
		given(authorization.require(ana, id, PermissionAction.WRITE)).willReturn(PermissionRole.OWNER);
		given(snippets.findById(id)).willReturn(Optional.of(snippet(id)));
		org.mockito.BDDMockito.willThrow(new DownstreamException("perm down", null))
			.given(permissions)
			.deleteAll(id);

		service.delete(ana, id);

		verify(snippets).delete(any(Snippet.class));
	}

	private Snippet snippet(UUID id) {
		Snippet s = new Snippet(id, "Hola", "d", "printscript", "1.1", "println(1);", "user-ana");
		s.setLintStatus(LintStatus.COMPLIANT);
		return s;
	}

}
