package com.ingsis.snippets.snippet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import com.ingsis.snippets.clients.permission.PermissionClient;
import com.ingsis.snippets.clients.permission.PermissionRole;
import com.ingsis.snippets.clients.permission.SnippetPermission;
import com.ingsis.snippets.common.error.UnsupportedLanguageException;
import com.ingsis.snippets.common.identity.CurrentUser;
import com.ingsis.snippets.rules.dto.RuleConfigResponse;
import com.ingsis.snippets.rules.model.RuleKind;
import com.ingsis.snippets.rules.service.RulesService;
import com.ingsis.snippets.snippet.model.FormatStatus;
import com.ingsis.snippets.snippet.model.LintStatus;
import com.ingsis.snippets.snippet.model.Snippet;
import com.ingsis.snippets.snippet.repository.SnippetRepository;
import com.ingsis.snippets.snippet.storage.SnippetStorage;
import com.ingsis.snippets.tasks.ContentHash;
import com.ingsis.snippets.tasks.TaskEvent;
import com.ingsis.snippets.tasks.TaskPublisher;
import com.ingsis.snippets.tasks.TaskType;
import com.ingsis.snippets.tasks.TasksProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RuleApplierTest {

  private final CurrentUser ana = new CurrentUser("user-ana");

  private final JsonNode config = new ObjectMapper().readTree("{\"a\":1}");

  @Mock private SnippetRepository snippets;

  @Mock private PermissionClient permissions;

  @Mock private RulesService rules;

  @Mock private TaskPublisher publisher;

  private RuleApplier applier;

  @Mock private SnippetStorage storage;

  @BeforeEach
  void setUp() {
    TasksProperties props =
        new TasksProperties(
            "tasks:printscript",
            "tasks:results",
            "snippet-service",
            Duration.ofMinutes(5),
            3,
            false,
            Duration.ofMillis(10),
            Duration.ofSeconds(60),
            Duration.ofSeconds(30),
            5,
            Duration.ofSeconds(2));
    applier = new RuleApplier(snippets, permissions, rules, publisher, props, storage);
    lenient().when(storage.get(ArgumentMatchers.any())).thenReturn("from-storage");
  }

  private Snippet snippet(String lang) {
    return new Snippet(UUID.randomUUID(), "n", null, lang, "1.1", "code", "user-ana");
  }

  @SuppressWarnings("unchecked")
  private List<TaskEvent> published() {
    ArgumentCaptor<List<TaskEvent>> captor = ArgumentCaptor.forClass(List.class);
    verify(publisher).publishAfterCommit(captor.capture());
    return captor.getValue();
  }

  @Test
  void enqueueMarksOwnedSnippetsOfTheLanguagePendingAndPublishesOneTaskEach() {
    Snippet a = snippet("printscript");
    Snippet b = snippet("printscript");
    Snippet other = snippet("python");
    given(permissions.idsFor("user-ana", PermissionRole.OWNER))
        .willReturn(
            List.of(
                new SnippetPermission(a.getId(), PermissionRole.OWNER),
                new SnippetPermission(b.getId(), PermissionRole.OWNER),
                new SnippetPermission(other.getId(), PermissionRole.OWNER)));
    given(snippets.findAllById(any())).willReturn(List.of(a, b, other));

    int queued = applier.enqueue(ana, "printscript", RuleKind.LINT, config);

    assertThat(queued).isEqualTo(2);
    assertThat(a.getLintStatus()).isEqualTo(LintStatus.PENDING);
    assertThat(a.getLintRequestedAt()).isNotNull();
    assertThat(other.getLintRequestedAt()).isNull();
    List<TaskEvent> events = published();
    assertThat(events).hasSize(2);
    assertThat(events).allMatch(e -> e.type() == TaskType.LINT && e.rules().equals("{\"a\":1}"));
    assertThat(events)
        .allMatch(e ->
            e.content().equals("from-storage")
                && e.contentHash().equals(ContentHash.sha256("from-storage")));
  }

  @Test
  void saveAndEnqueueSavesTheConfigAndEnqueuesWithWhatWasSaved() {
    Snippet a = snippet("printscript");
    given(rules.save(ana, "printscript", RuleKind.LINT, config))
        .willReturn(new RuleConfigResponse("printscript", RuleKind.LINT, false, config));
    given(permissions.idsFor("user-ana", PermissionRole.OWNER))
        .willReturn(List.of(new SnippetPermission(a.getId(), PermissionRole.OWNER)));
    given(snippets.findAllById(any())).willReturn(List.of(a));

    var response = applier.saveAndEnqueue(ana, "printscript", RuleKind.LINT, config);

    assertThat(response.queued()).isEqualTo(1);
    assertThat(response.kind()).isEqualTo(RuleKind.LINT);
    assertThat(response.config()).isEqualTo(config);
    assertThat(a.getLintTaskId()).isEqualTo(published().get(0).taskId());
  }

  @Test
  void enqueueFormatMarksFormatPending() {
    Snippet a = snippet("printscript");
    given(permissions.idsFor("user-ana", PermissionRole.OWNER))
        .willReturn(List.of(new SnippetPermission(a.getId(), PermissionRole.OWNER)));
    given(snippets.findAllById(any())).willReturn(List.of(a));

    applier.enqueue(ana, "printscript", RuleKind.FORMAT, config);

    assertThat(a.getFormatStatus()).isEqualTo(FormatStatus.PENDING);
    assertThat(a.getLintStatus()).isEqualTo(LintStatus.PENDING); // default of a new snippet
    assertThat(published().get(0).type()).isEqualTo(TaskType.FORMAT);
  }

  @Test
  void requeueStalledReEnqueuesAndCountsAttempts() {
    Snippet lint = snippet("printscript");
    lint.markLintPending(Instant.now().minusSeconds(3600), 1, UUID.randomUUID());
    Snippet format = snippet("printscript");
    format.markFormatPending(Instant.now().minusSeconds(3600), 0, UUID.randomUUID());
    UUID oldLintTask = lint.getLintTaskId();
    given(snippets.findStalledLint(any(), any())).willReturn(List.of(lint));
    given(snippets.findStalledFormat(any(), any())).willReturn(List.of(format));
    given(rules.get(any(CurrentUser.class), any(), any()))
        .willReturn(new RuleConfigResponse("printscript", RuleKind.LINT, false, config));

    int requeued = applier.requeueStalled(Instant.now());

    assertThat(requeued).isEqualTo(2);
    assertThat(lint.getLintAttempts()).isEqualTo(2);
    assertThat(lint.getLintTaskId()).isNotEqualTo(oldLintTask);
    assertThat(format.getFormatAttempts()).isEqualTo(1);
    assertThat(published()).hasSize(2);
  }

  @Test
  void requeueStalledGivesUpAfterMaxRequeues() {
    Snippet lint = snippet("printscript");
    lint.markLintPending(Instant.now().minusSeconds(3600), 3, UUID.randomUUID());
    Snippet format = snippet("printscript");
    format.markFormatPending(Instant.now().minusSeconds(3600), 3, UUID.randomUUID());
    given(snippets.findStalledLint(any(), any())).willReturn(List.of(lint));
    given(snippets.findStalledFormat(any(), any())).willReturn(List.of(format));

    assertThat(applier.requeueStalled(Instant.now())).isZero();

    assertThat(lint.getLintStatus()).isEqualTo(LintStatus.FAILED);
    assertThat(format.getFormatStatus()).isEqualTo(FormatStatus.FAILED);
    verify(publisher).publishAfterCommit(anyList());
  }

  @Test
  void requeueStalledFailsTheSnippetWhenItsRulesCannotBeLoaded() {
    Snippet lint = snippet("cobol");
    lint.markLintPending(Instant.now().minusSeconds(3600), 0, UUID.randomUUID());
    given(snippets.findStalledLint(any(), any())).willReturn(List.of(lint));
    given(snippets.findStalledFormat(any(), any())).willReturn(List.of());
    given(rules.get(any(CurrentUser.class), any(), any()))
        .willThrow(new UnsupportedLanguageException("nope"));

    assertThat(applier.requeueStalled(Instant.now())).isZero();

    assertThat(lint.getLintStatus()).isEqualTo(LintStatus.FAILED);
  }
}
