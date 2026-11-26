package com.ingsis.snippets.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import com.ingsis.snippets.snippet.model.LintStatus;
import com.ingsis.snippets.snippet.model.Snippet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class TaskMappingTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private Snippet snippet() {
    return new Snippet(UUID.randomUUID(), "n", null, "printscript", "1.1", "println(1);", "ana");
  }

  @Test
  void sha256IsHexOfTheUtf8Content() {
    assertThat(ContentHash.sha256("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    assertThat(ContentHash.sha256("ñ")).isNotEqualTo(ContentHash.sha256("n"));
  }

  @Test
  void taskEventSerializesTheAgreedFields() {
    Snippet snippet = snippet();
    TaskEvent event = TaskEvent.of(snippet, "println(1);", TaskType.LINT, "{\"a\":1}");

    JsonNode json = mapper.readTree(mapper.writeValueAsString(event));

    assertThat(json.get("taskId").asString()).isEqualTo(event.taskId().toString());
    assertThat(json.get("snippetId").asString()).isEqualTo(snippet.getId().toString());
    assertThat(json.get("type").asString()).isEqualTo("LINT");
    assertThat(json.get("language").asString()).isEqualTo("printscript");
    assertThat(json.get("version").asString()).isEqualTo("1.1");
    assertThat(json.get("content").asString()).isEqualTo("println(1);");
    assertThat(json.get("rules").asString()).isEqualTo("{\"a\":1}");
    assertThat(json.get("inputs").size()).isZero();
    assertThat(json.get("expectedOutputs").size()).isZero();
    assertThat(json.get("contentHash").asString()).isEqualTo(ContentHash.sha256("println(1);"));
  }

  @Test
  void resultEventParsesTheWorkerPayloadAndToleratesUnknownFields() {
    UUID id = UUID.randomUUID();
    String payload =
        "{\"taskId\":\"%s\",\"snippetId\":\"%s\",\"type\":\"LINT\",\"contentHash\":\"h\","
                .formatted(id, id)
            + "\"attempts\":1,\"status\":\"OK\",\"success\":true,\"extra\":1,"
            + "\"errors\":[{\"rule\":\"r\",\"message\":\"m\",\"line\":2,\"column\":3}]}";

    TaskResultEvent event = mapper.readValue(payload, TaskResultEvent.class);

    assertThat(event.effectiveStatus()).isEqualTo(ResultStatus.OK);
    assertThat(event.errorsOrEmpty()).hasSize(1);
    assertThat(event.errorsOrEmpty().get(0).line()).isEqualTo(2);
  }

  @Test
  void effectiveStatusFallsBackToSuccess() {
    UUID id = UUID.randomUUID();
    TaskResultEvent ok =
        new TaskResultEvent(
            id, id, TaskType.LINT, "h", null, null, true, null, null, null, null, null);
    TaskResultEvent failed =
        new TaskResultEvent(
            id, id, TaskType.LINT, "h", null, null, false, null, null, null, null, null);

    assertThat(ok.effectiveStatus()).isEqualTo(ResultStatus.OK);
    assertThat(failed.effectiveStatus()).isEqualTo(ResultStatus.FAILED);
    assertThat(ok.errorsOrEmpty()).isEmpty();
  }

  @Test
  void staleDetectionComparesTheContentHash() {
    Snippet snippet = snippet();
    UUID id = snippet.getId();
    String current = ContentHash.sha256(snippet.getContent());
    TaskResultEvent fresh =
        new TaskResultEvent(
            id,
            id,
            TaskType.LINT,
            current,
            null,
            ResultStatus.OK,
            true,
            null,
            null,
            null,
            null,
            null);
    TaskResultEvent old =
        new TaskResultEvent(
            id,
            id,
            TaskType.LINT,
            "old",
            null,
            ResultStatus.OK,
            true,
            null,
            null,
            null,
            null,
            null);

    assertThat(ResultProcessor.isStale(snippet, "println(1);", fresh)).isFalse();
    assertThat(ResultProcessor.isStale(snippet, "println(1);", old)).isTrue();
    assertThat(snippet.getLintStatus()).isEqualTo(LintStatus.PENDING);
  }
}
