package com.ingsis.snippets.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ingsis.snippets.snippet.model.FormatStatus;
import com.ingsis.snippets.snippet.model.LintStatus;
import com.ingsis.snippets.snippet.model.Snippet;
import com.ingsis.snippets.snippet.repository.SnippetRepository;
import com.ingsis.snippets.snippet.storage.SnippetStorage;
import com.ingsis.snippets.tasks.TaskResultEvent.ResultError;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResultProcessorTest {

  private static final UUID LINT_TASK = UUID.randomUUID();

  private static final UUID FORMAT_TASK = UUID.randomUUID();

  @Mock private SnippetRepository snippets;

  @Mock private SnippetStorage storage;

  private ResultProcessor processor;

  private Snippet snippet;

  @BeforeEach
  void setUp() {
    processor = new ResultProcessor(snippets, storage);
    lenient().when(storage.get(ArgumentMatchers.any())).thenReturn("from-storage");
    snippet = new Snippet(UUID.randomUUID(), "n", null, "printscript", "1.1", "code", "ana");
    snippet.markLintPending(java.time.Instant.now(), 0, LINT_TASK);
    snippet.markFormatPending(java.time.Instant.now(), 0, FORMAT_TASK);
    given(snippets.findById(snippet.getId())).willReturn(Optional.of(snippet));
  }

  private TaskResultEvent result(
      TaskType type, ResultStatus status, List<ResultError> errors, String formatted) {
    return new TaskResultEvent(
        type == TaskType.FORMAT ? FORMAT_TASK : LINT_TASK,
        snippet.getId(),
        type,
        ContentHash.sha256("from-storage"),
        1,
        status,
        status == ResultStatus.OK,
        errors,
        formatted,
        null,
        null,
        null);
  }

  @Test
  void lintOkWithoutErrorsIsCompliant() {
    assertThat(processor.apply(result(TaskType.LINT, ResultStatus.OK, List.of(), null))).isTrue();

    assertThat(snippet.getLintStatus()).isEqualTo(LintStatus.COMPLIANT);
    assertThat(snippet.getLintViolations()).isEmpty();
    verify(snippets).save(snippet);
  }

  @Test
  void lintOkWithErrorsIsNonCompliantWithViolations() {
    processor.apply(
        result(TaskType.LINT, ResultStatus.OK, List.of(new ResultError("r", "m", 1, null)), null));

    assertThat(snippet.getLintStatus()).isEqualTo(LintStatus.NON_COMPLIANT);
    assertThat(snippet.getLintViolations()).hasSize(1);
    assertThat(snippet.getLintViolations().get(0).rule()).isEqualTo("r");
  }

  @Test
  void lintInvalidSourceAndFailedAreFailed() {
    processor.apply(
        result(
            TaskType.LINT,
            ResultStatus.INVALID_SOURCE,
            List.of(new ResultError("syntax", "bad", 1, 1)),
            null));
    assertThat(snippet.getLintStatus()).isEqualTo(LintStatus.FAILED);
    assertThat(snippet.getLintViolations()).hasSize(1);

    processor.apply(result(TaskType.LINT, ResultStatus.FAILED, null, null));
    assertThat(snippet.getLintStatus()).isEqualTo(LintStatus.FAILED);
  }

  @Test
  void formatOkStoresFormattedContent() {
    processor.apply(result(TaskType.FORMAT, ResultStatus.OK, null, "pretty"));

    assertThat(snippet.getFormattedContent()).isEqualTo("pretty");
    assertThat(snippet.getFormatStatus()).isEqualTo(FormatStatus.DONE);
  }

  @Test
  void formatNotOkOrWithoutContentIsFailed() {
    processor.apply(result(TaskType.FORMAT, ResultStatus.INVALID_SOURCE, null, null));
    assertThat(snippet.getFormatStatus()).isEqualTo(FormatStatus.FAILED);

    snippet.setFormatStatus(FormatStatus.PENDING);
    processor.apply(result(TaskType.FORMAT, ResultStatus.OK, null, null));
    assertThat(snippet.getFormatStatus()).isEqualTo(FormatStatus.FAILED);
    assertThat(snippet.getFormattedContent()).isNull();
  }

  @Test
  void applyingTwiceIsHarmless() {
    TaskResultEvent event = result(TaskType.FORMAT, ResultStatus.OK, null, "pretty");
    processor.apply(event);
    processor.apply(event);

    assertThat(snippet.getFormattedContent()).isEqualTo("pretty");
    assertThat(snippet.getFormatStatus()).isEqualTo(FormatStatus.DONE);
  }

  @Test
  void staleResultIsIgnored() {
    TaskResultEvent stale =
        new TaskResultEvent(
            UUID.randomUUID(),
            snippet.getId(),
            TaskType.LINT,
            ContentHash.sha256("old code"),
            1,
            ResultStatus.OK,
            true,
            List.of(),
            null,
            null,
            null,
            null);

    assertThat(processor.apply(stale)).isFalse();
    assertThat(snippet.getLintStatus()).isEqualTo(LintStatus.PENDING);
    verify(snippets, never()).save(any());
  }

  @Test
  void deletedSnippetAndOtherTypesAreIgnored() {
    UUID gone = UUID.randomUUID();
    given(snippets.findById(gone)).willReturn(Optional.empty());
    TaskResultEvent deleted =
        new TaskResultEvent(
            UUID.randomUUID(),
            gone,
            TaskType.LINT,
            "h",
            1,
            ResultStatus.OK,
            true,
            null,
            null,
            null,
            null,
            null);

    assertThat(processor.apply(deleted)).isFalse();
    assertThat(processor.apply(result(TaskType.TEST, ResultStatus.OK, null, null))).isFalse();
    verify(snippets, never()).save(any());
  }

  @Test
  void resultOfAReplacedTaskIsIgnored() {
    TaskResultEvent old =
        new TaskResultEvent(
            UUID.randomUUID(),
            snippet.getId(),
            TaskType.LINT,
            ContentHash.sha256("from-storage"),
            1,
            ResultStatus.OK,
            true,
            List.of(),
            null,
            null,
            null,
            null);

    assertThat(processor.apply(old)).isFalse();
    assertThat(snippet.getLintStatus()).isEqualTo(LintStatus.PENDING);
    verify(snippets, never()).save(any());
  }
}
