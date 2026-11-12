package com.ingsis.snippets.snippet.service;

import com.ingsis.snippets.authorization.AuthorizationService;
import com.ingsis.snippets.clients.language.LanguageClient;
import com.ingsis.snippets.clients.language.SourceError;
import com.ingsis.snippets.clients.language.ValidationResult;
import com.ingsis.snippets.clients.permission.PermissionAction;
import com.ingsis.snippets.clients.permission.PermissionClient;
import com.ingsis.snippets.clients.permission.PermissionRole;
import com.ingsis.snippets.clients.permission.SnippetPermission;
import com.ingsis.snippets.common.error.DownstreamException;
import com.ingsis.snippets.common.error.InvalidSnippetException;
import com.ingsis.snippets.common.error.NotFoundException;
import com.ingsis.snippets.common.identity.CurrentUser;
import com.ingsis.snippets.rules.model.RuleKind;
import com.ingsis.snippets.rules.service.RulesService;
import com.ingsis.snippets.snippet.dto.SnippetDetail;
import com.ingsis.snippets.snippet.dto.SnippetPage;
import com.ingsis.snippets.snippet.dto.SnippetSummary;
import com.ingsis.snippets.snippet.dto.UpdateSnippetRequest;
import com.ingsis.snippets.snippet.event.SnippetContentChanged;
import com.ingsis.snippets.snippet.model.Relation;
import com.ingsis.snippets.snippet.model.Snippet;
import com.ingsis.snippets.snippet.repository.SnippetRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta el CRUD de snippets sobre Language y Permission. La autorización siempre se consulta a
 * Permission (D05); {@code owner_id} es solo informativo.
 */
@Service
public class SnippetService {

  private final SnippetRepository snippets;

  private final LanguageClient language;

  private final PermissionClient permissions;

  private final AuthorizationService authorization;

  private final RulesService rules;

  private final ApplicationEventPublisher events;

  public SnippetService(
      SnippetRepository snippets,
      LanguageClient language,
      PermissionClient permissions,
      AuthorizationService authorization,
      RulesService rules,
      ApplicationEventPublisher events) {
    this.snippets = snippets;
    this.language = language;
    this.permissions = permissions;
    this.authorization = authorization;
    this.rules = rules;
    this.events = events;
  }

  /** Crea con compensación (D10): validar → lintear → guardar → grant OWNER. */
  @Transactional
  public SnippetDetail create(
      CurrentUser user,
      String name,
      String description,
      String languageId,
      String version,
      String content) {
    validate(languageId, content, version);

    Snippet snippet =
        new Snippet(UUID.randomUUID(), name, description, languageId, version, content, user.id());
    List<SourceError> violations =
        language.lint(languageId, content, version, lintRules(user, languageId));
    LintResults.apply(snippet, violations);
    snippet = snippets.save(snippet);

    try {
      permissions.grant(snippet.getId(), user.id(), PermissionRole.OWNER);
    } catch (RuntimeException ex) {
      snippets.delete(snippet);
      throw new DownstreamException("Could not grant ownership; snippet rolled back", ex);
    }
    return detail(snippet, PermissionRole.OWNER);
  }

  @Transactional(readOnly = true)
  public SnippetDetail get(CurrentUser user, UUID id) {
    PermissionRole role = authorization.require(user, id, PermissionAction.READ);
    Snippet snippet = load(id);
    return detail(snippet, role);
  }

  @Transactional(readOnly = true)
  public SnippetPage list(
      CurrentUser user,
      Relation relation,
      String name,
      String languageId,
      com.ingsis.snippets.snippet.model.LintStatus lintStatus,
      Pageable pageable) {
    PermissionRole filterRole =
        switch (relation) {
          case OWNED -> PermissionRole.OWNER;
          case SHARED -> PermissionRole.READ;
          case ALL -> null;
        };
    List<SnippetPermission> granted = permissions.idsFor(user.id(), filterRole);
    var roleById =
        granted.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    SnippetPermission::snippetId, SnippetPermission::role, (a, b) -> a));
    if (roleById.isEmpty()) {
      return new SnippetPage(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0);
    }
    Page<Snippet> page =
        snippets.searchByName(roleById.keySet(), name, languageId, lintStatus, pageable);
    List<SnippetSummary> items =
        page.getContent().stream()
            .map(s ->
                new SnippetSummary(
                    s.getId(),
                    s.getName(),
                    s.getLanguage(),
                    s.getVersion(),
                    s.getOwnerId(),
                    roleById.get(s.getId()),
                    s.getLintStatus(),
                    s.getUpdatedAt()))
            .toList();
    return new SnippetPage(items, page.getNumber(), page.getSize(), page.getTotalElements());
  }

  @Transactional
  public SnippetDetail update(CurrentUser user, UUID id, UpdateSnippetRequest request) {
    PermissionRole role = authorization.require(user, id, PermissionAction.WRITE);
    Snippet snippet = load(id);

    boolean contentChanged = false;
    if (request.name() != null) {
      snippet.setName(request.name());
    }
    if (request.description() != null) {
      snippet.setDescription(request.description());
    }
    if (request.language() != null) {
      snippet.setLanguage(request.language());
      contentChanged = true;
    }
    if (request.version() != null) {
      snippet.setVersion(request.version());
      contentChanged = true;
    }
    if (request.content() != null) {
      snippet.setContent(request.content());
      contentChanged = true;
    }

    if (contentChanged) {
      validate(snippet.getLanguage(), snippet.getContent(), snippet.getVersion());
      List<SourceError> violations =
          language.lint(
              snippet.getLanguage(),
              snippet.getContent(),
              snippet.getVersion(),
              lintRules(user, snippet.getLanguage()));
      LintResults.apply(snippet, violations);
    }
    snippet = snippets.save(snippet);

    if (contentChanged) {
      events.publishEvent(new SnippetContentChanged(snippet.getId()));
    }
    return detail(snippet, role);
  }

  @Transactional
  public void delete(CurrentUser user, UUID id) {
    authorization.require(user, id, PermissionAction.WRITE);
    Snippet snippet = load(id);
    snippets.delete(snippet);
    try {
      permissions.deleteAll(id);
    } catch (RuntimeException ex) {
      // Permission es idempotente; se puede reintentar. No se revierte el borrado.
      org.slf4j.LoggerFactory.getLogger(SnippetService.class)
          .warn("Snippet {} deleted but Permission cleanup failed", id, ex);
    }
  }

  private void validate(String languageId, String content, String version) {
    ValidationResult result = language.validate(languageId, content, version);
    if (!result.valid()) {
      throw new InvalidSnippetException(result.errors());
    }
  }

  private tools.jackson.databind.JsonNode lintRules(CurrentUser user, String languageId) {
    return rules.get(user, languageId, RuleKind.LINT).config();
  }

  private Snippet load(UUID id) {
    return snippets
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Snippet " + id + " not found"));
  }

  private SnippetDetail detail(Snippet s, PermissionRole role) {
    List<String> sharedWith = null;
    if (role == PermissionRole.OWNER) {
      sharedWith =
          permissions.usersOf(s.getId()).stream()
              .filter(u -> u.role() != PermissionRole.OWNER)
              .map(com.ingsis.snippets.clients.permission.UserPermission::userId)
              .toList();
    }
    return new SnippetDetail(
        s.getId(),
        s.getName(),
        s.getDescription(),
        s.getLanguage(),
        s.getVersion(),
        s.getContent(),
        s.getOwnerId(),
        role,
        s.getLintStatus(),
        s.getLintViolations(),
        sharedWith,
        s.getCreatedAt(),
        s.getUpdatedAt());
  }
}
