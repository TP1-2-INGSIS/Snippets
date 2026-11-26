package com.ingsis.snippets.snippet.service;

import com.ingsis.snippets.clients.permission.PermissionClient;
import com.ingsis.snippets.clients.permission.PermissionRole;
import com.ingsis.snippets.clients.permission.SnippetPermission;
import com.ingsis.snippets.common.identity.CurrentUser;
import com.ingsis.snippets.rules.dto.RuleApplyResponse;
import com.ingsis.snippets.rules.dto.RuleConfigResponse;
import com.ingsis.snippets.rules.model.RuleKind;
import com.ingsis.snippets.rules.service.RulesService;
import com.ingsis.snippets.snippet.model.FormatStatus;
import com.ingsis.snippets.snippet.model.LintStatus;
import com.ingsis.snippets.snippet.model.Snippet;
import com.ingsis.snippets.snippet.repository.SnippetRepository;
import com.ingsis.snippets.tasks.TaskEvent;
import com.ingsis.snippets.tasks.TaskPublisher;
import com.ingsis.snippets.tasks.TaskType;
import com.ingsis.snippets.tasks.TasksProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * Productor de las tareas de lint/format masivos (US 12, 15; D14). En una transacción marca cada
 * snippet OWNER del lenguaje como PENDING y, después del commit, publica una tarea por snippet. El
 * resultado vuelve por {@code tasks:results}. También re-encola lo que quedó colgado (reaper).
 */
@Service
public class RuleApplier {

  private static final Logger log = LoggerFactory.getLogger(RuleApplier.class);

  private final SnippetRepository snippets;

  private final PermissionClient permissions;

  private final RulesService rules;

  private final TaskPublisher publisher;

  private final TasksProperties properties;

  public RuleApplier(
      SnippetRepository snippets,
      PermissionClient permissions,
      RulesService rules,
      TaskPublisher publisher,
      TasksProperties properties) {
    this.snippets = snippets;
    this.permissions = permissions;
    this.rules = rules;
    this.publisher = publisher;
    this.properties = properties;
  }

  /**
   * Guarda la config y marca PENDING los snippets en la misma transacción; las tareas se publican
   * después del commit. Si algo falla, no queda ni la config ni los estados a medias.
   */
  @Transactional
  public RuleApplyResponse saveAndEnqueue(
      CurrentUser user, String languageId, RuleKind kind, JsonNode config) {
    RuleConfigResponse saved = rules.save(user, languageId, kind, config);
    int queued = enqueue(user, languageId, kind, saved.config());
    return new RuleApplyResponse(languageId, kind, saved.config(), queued);
  }

  /**
   * Marca PENDING y encola una tarea por cada snippet OWNER del usuario en el lenguaje.
   *
   * @return cuántas tareas se encolaron
   */
  @Transactional
  public int enqueue(CurrentUser user, String languageId, RuleKind kind, JsonNode config) {
    List<UUID> ownedIds =
        permissions.idsFor(user.id(), PermissionRole.OWNER).stream()
            .map(SnippetPermission::snippetId)
            .toList();
    String rulesJson = config.toString();
    Instant now = Instant.now();
    List<TaskEvent> events = new ArrayList<>();
    for (Snippet snippet : snippets.findAllById(ownedIds)) {
      if (!snippet.getLanguage().equals(languageId)) {
        continue;
      }
      TaskEvent event = TaskEvent.of(snippet, type(kind), rulesJson);
      markPending(snippet, kind, now, 0, event.taskId());
      events.add(event);
    }
    publisher.publishAfterCommit(events);
    return events.size();
  }

  /**
   * Reaper: re-encola los snippets PENDING hace más de {@code tasks.timeout}, hasta {@code
   * tasks.max-requeues} veces; después quedan FAILED.
   *
   * @return cuántos se re-encolaron
   */
  @Transactional
  public int requeueStalled(Instant now) {
    Instant cutoff = now.minus(properties.timeout());
    List<TaskEvent> events = new ArrayList<>();
    for (Snippet s : snippets.findStalledLint(LintStatus.PENDING, cutoff)) {
      requeue(s, RuleKind.LINT, s.getLintAttempts(), now, events);
    }
    for (Snippet s : snippets.findStalledFormat(FormatStatus.PENDING, cutoff)) {
      requeue(s, RuleKind.FORMAT, s.getFormatAttempts(), now, events);
    }
    publisher.publishAfterCommit(events);
    return events.size();
  }

  private void requeue(
      Snippet snippet, RuleKind kind, int attempts, Instant now, List<TaskEvent> events) {
    if (attempts >= properties.maxRequeues()) {
      log.warn("Giving up on {} of snippet {} after {} requeues", kind, snippet.getId(), attempts);
      markFailed(snippet, kind);
      return;
    }
    try {
      JsonNode config =
          rules.get(new CurrentUser(snippet.getOwnerId()), snippet.getLanguage(), kind).config();
      TaskEvent event = TaskEvent.of(snippet, type(kind), config.toString());
      markPending(snippet, kind, now, attempts + 1, event.taskId());
      events.add(event);
    } catch (RuntimeException ex) {
      log.warn("Could not requeue {} of snippet {}", kind, snippet.getId(), ex);
      markFailed(snippet, kind);
    }
  }

  private static void markPending(
      Snippet snippet, RuleKind kind, Instant now, int attempts, UUID taskId) {
    if (kind == RuleKind.LINT) {
      snippet.markLintPending(now, attempts, taskId);
    } else {
      snippet.markFormatPending(now, attempts, taskId);
    }
  }

  private static void markFailed(Snippet snippet, RuleKind kind) {
    if (kind == RuleKind.LINT) {
      snippet.setLintStatus(LintStatus.FAILED);
    } else {
      snippet.setFormatStatus(FormatStatus.FAILED);
    }
  }

  private static TaskType type(RuleKind kind) {
    return kind == RuleKind.LINT ? TaskType.LINT : TaskType.FORMAT;
  }
}
