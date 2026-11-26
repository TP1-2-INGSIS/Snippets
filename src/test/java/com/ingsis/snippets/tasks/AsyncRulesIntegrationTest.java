package com.ingsis.snippets.tasks;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.ingsis.snippets.TestcontainersConfiguration;
import com.ingsis.snippets.snippet.model.FormatStatus;
import com.ingsis.snippets.snippet.model.LintStatus;
import com.ingsis.snippets.snippet.model.Snippet;
import com.ingsis.snippets.snippet.repository.SnippetRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Real Postgres + Redis: PUT rules enqueues tasks, a simulated worker answers on tasks:results and
 * the consumer updates the snippets; the reaper re-enqueues what got lost.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AsyncRulesIntegrationTest {

  private static final String ANA = "user-ana";

  private static final WireMockServer DOWNSTREAM =
      new WireMockServer(wireMockConfig().dynamicPort());

  static {
    DOWNSTREAM.start();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("services.permission.url", DOWNSTREAM::baseUrl);
    registry.add("services.language.url", DOWNSTREAM::baseUrl);
  }

  @AfterAll
  static void stopWireMock() {
    DOWNSTREAM.stop();
  }

  @Autowired private MockMvc mvc;

  @Autowired private SnippetRepository snippets;

  @Autowired private StringRedisTemplate redis;

  @Autowired private ObjectMapper mapper;

  @Autowired private TasksProperties props;

  @Autowired private ResultsConsumer consumer;

  @Autowired private TaskReaper reaper;

  @BeforeEach
  void reset() {
    snippets.deleteAll();
    redis.delete(props.stream());
    if (!consumer.isRunning()) {
      consumer.start();
    }
  }

  private Snippet save(String language) {
    return snippets.save(
        new Snippet(UUID.randomUUID(), "n", null, language, "1.1", "println(1);", ANA));
  }

  private void stubOwned(Snippet... owned) {
    String body =
        mapper.writeValueAsString(
            java.util.Arrays.stream(owned)
                .map(s -> Map.of("snippetId", s.getId().toString(), "role", "OWNER"))
                .toList());
    DOWNSTREAM.stubFor(
        get(urlPathEqualTo("/permissions"))
            .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(body)));
  }

  private void putRules(String kind, int expectedQueued) throws Exception {
    mvc.perform(
            put("/rules/printscript/" + kind)
                .header("X-User-Id", ANA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"config\":{\"identifierFormat\":\"snake case\"}}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.language").value("printscript"))
        .andExpect(jsonPath("$.kind").value(kind.toUpperCase()))
        .andExpect(jsonPath("$.config.identifierFormat").value("snake case"))
        .andExpect(jsonPath("$.queued").value(expectedQueued));
  }

  private List<JsonNode> tasks() {
    List<MapRecord<String, Object, Object>> records =
        redis
            .opsForStream()
            .range(props.stream(), org.springframework.data.domain.Range.unbounded());
    return records.stream()
        .map(r -> mapper.readTree(String.valueOf(r.getValue().get("data"))))
        .toList();
  }

  private void sendResult(
      Snippet snippet,
      String type,
      String hash,
      String status,
      String errorsJson,
      String formatted) {
    String json =
        "{\"taskId\":\"%s\",\"snippetId\":\"%s\",\"type\":\"%s\",\"contentHash\":\"%s\",\"attempts\":1,\"status\":\"%s\",\"success\":%s,\"errors\":%s,\"formattedContent\":%s}"
            .formatted(
                UUID.randomUUID(),
                snippet.getId(),
                type,
                hash,
                status,
                status.equals("OK"),
                errorsJson,
                formatted == null ? "null" : "\"" + formatted + "\"");
    redis
        .opsForStream()
        .add(StreamRecords.string(Map.of("data", json)).withStreamKey(props.resultsStream()));
  }

  private static String hash(Snippet s) {
    return ContentHash.sha256(s.getContent());
  }

  private void await(BooleanSupplier condition) throws InterruptedException {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
    while (!condition.getAsBoolean()) {
      assertThat(Instant.now()).isBefore(deadline);
      Thread.sleep(100);
    }
  }

  private Snippet reload(Snippet s) {
    return snippets.findById(s.getId()).orElseThrow();
  }

  private long pendingEntries() {
    var summary = redis.opsForStream().pending(props.resultsStream(), props.resultsGroup());
    return summary == null ? 0 : summary.getTotalPendingMessages();
  }

  @Test
  void putLintReturns202MarksPendingAndPublishesOneTaskPerSnippet() throws Exception {
    Snippet a = save("printscript");
    Snippet b = save("printscript");
    Snippet py = save("python");
    stubOwned(a, b, py);

    putRules("lint", 2);

    assertThat(reload(a).getLintStatus()).isEqualTo(LintStatus.PENDING);
    assertThat(reload(a).getLintRequestedAt()).isNotNull();
    assertThat(reload(py).getLintRequestedAt()).isNull();
    List<JsonNode> tasks = tasks();
    assertThat(tasks).hasSize(2);
    JsonNode task = tasks.get(0);
    assertThat(task.get("type").asString()).isEqualTo("LINT");
    assertThat(task.get("language").asString()).isEqualTo("printscript");
    assertThat(task.get("version").asString()).isEqualTo("1.1");
    assertThat(task.get("content").asString()).isEqualTo("println(1);");
    assertThat(mapper.readTree(task.get("rules").asString()).get("identifierFormat").asString())
        .isEqualTo("snake case");
    assertThat(task.get("contentHash").asString()).isEqualTo(hash(a));
    assertThat(UUID.fromString(task.get("taskId").asString())).isNotNull();
    assertThat(task.get("inputs").size()).isZero();
    assertThat(tasks.stream().map(t -> t.get("snippetId").asString()).toList())
        .containsExactlyInAnyOrder(a.getId().toString(), b.getId().toString());
  }

  @Test
  void putFormatMarksFormatPendingAndPublishesFormatTasks() throws Exception {
    Snippet a = save("printscript");
    stubOwned(a);

    putRules("format", 1);

    assertThat(reload(a).getFormatStatus()).isEqualTo(FormatStatus.PENDING);
    assertThat(tasks().get(0).get("type").asString()).isEqualTo("FORMAT");
  }

  @Test
  void workerResultsUpdateSnippetStatusesAndAreAcked() throws Exception {
    Snippet ok = save("printscript");
    Snippet bad = save("printscript");
    Snippet invalid = save("printscript");
    Snippet fmt = save("printscript");
    Snippet fmtFail = save("printscript");
    for (Snippet s : List.of(ok, bad, invalid)) {
      s.markLintPending(Instant.now(), 0);
      snippets.save(s);
    }
    for (Snippet s : List.of(fmt, fmtFail)) {
      s.markFormatPending(Instant.now(), 0);
      snippets.save(s);
    }

    sendResult(ok, "LINT", hash(ok), "OK", "[]", null);
    sendResult(
        bad,
        "LINT",
        hash(bad),
        "OK",
        "[{\"rule\":\"identifierFormat\",\"message\":\"bad name\",\"line\":1,\"column\":5}]",
        null);
    sendResult(
        invalid,
        "LINT",
        hash(invalid),
        "INVALID_SOURCE",
        "[{\"rule\":\"syntax\",\"message\":\"unexpected\",\"line\":1,\"column\":1}]",
        null);
    sendResult(fmt, "FORMAT", hash(fmt), "OK", "[]", "println(1);\\n");
    sendResult(fmtFail, "FORMAT", hash(fmtFail), "FAILED", "[]", null);

    await(() -> reload(fmtFail).getFormatStatus() == FormatStatus.FAILED);
    await(() -> reload(fmt).getFormatStatus() == FormatStatus.DONE);
    await(() -> reload(invalid).getLintStatus() == LintStatus.FAILED);
    await(() -> reload(ok).getLintStatus() == LintStatus.COMPLIANT);
    await(() -> reload(bad).getLintStatus() == LintStatus.NON_COMPLIANT);

    assertThat(reload(bad).getLintViolations()).hasSize(1);
    assertThat(reload(bad).getLintViolations().get(0).message()).isEqualTo("bad name");
    assertThat(reload(invalid).getLintViolations().get(0).rule()).isEqualTo("syntax");
    assertThat(reload(fmt).getFormattedContent()).isEqualTo("println(1);\n");
    assertThat(reload(fmt).getContent()).isEqualTo("println(1);");
    await(() -> pendingEntries() == 0);
  }

  @Test
  void staleResultIsIgnoredAndAcked() throws Exception {
    Snippet s = save("printscript");
    s.markLintPending(Instant.now(), 0);
    snippets.save(s);
    Snippet marker = save("printscript");
    marker.markLintPending(Instant.now(), 0);
    snippets.save(marker);

    sendResult(s, "LINT", ContentHash.sha256("old content"), "OK", "[]", null);
    sendResult(marker, "LINT", hash(marker), "OK", "[]", null);

    await(() -> reload(marker).getLintStatus() == LintStatus.COMPLIANT);
    assertThat(reload(s).getLintStatus()).isEqualTo(LintStatus.PENDING);
    await(() -> pendingEntries() == 0);
  }

  @Test
  void resultOfADeletedSnippetIsAcked() throws Exception {
    Snippet gone = save("printscript");
    Snippet marker = save("printscript");
    marker.markLintPending(Instant.now(), 0);
    snippets.save(marker);
    snippets.delete(gone);

    sendResult(gone, "LINT", hash(gone), "OK", "[]", null);
    sendResult(marker, "LINT", hash(marker), "OK", "[]", null);

    await(() -> reload(marker).getLintStatus() == LintStatus.COMPLIANT);
    await(() -> pendingEntries() == 0);
  }

  @Test
  void pendingEntriesAreProcessedAfterAConsumerRestart() throws Exception {
    Snippet s = save("printscript");
    s.markLintPending(Instant.now(), 0);
    snippets.save(s);

    consumer.stop();
    sendResult(s, "LINT", hash(s), "OK", "[]", null);
    // The "crashed" process read the entry but never acked it.
    var read =
        redis
            .opsForStream()
            .read(
                Consumer.from(props.resultsGroup(), consumer.consumerName()),
                StreamOffset.create(props.resultsStream(), ReadOffset.lastConsumed()));
    assertThat(read).isNotEmpty();
    assertThat(reload(s).getLintStatus()).isEqualTo(LintStatus.PENDING);

    consumer.start();

    await(() -> reload(s).getLintStatus() == LintStatus.COMPLIANT);
    await(() -> pendingEntries() == 0);
  }

  @Test
  void reaperReEnqueuesStalledTasksAndEventuallyFailsThem() throws Exception {
    Snippet s = save("printscript");
    s.markLintPending(Instant.now().minus(Duration.ofHours(1)), 0);
    snippets.save(s);
    Instant later = Instant.now().plus(Duration.ofHours(1));

    assertThat(reaper.reap(Instant.now().minus(Duration.ofHours(2)))).isZero();
    assertThat(reaper.reap(later)).isEqualTo(1);

    assertThat(tasks()).hasSize(1);
    assertThat(tasks().get(0).get("type").asString()).isEqualTo("LINT");
    assertThat(reload(s).getLintAttempts()).isEqualTo(1);
    assertThat(reload(s).getLintStatus()).isEqualTo(LintStatus.PENDING);

    Instant muchLater = later.plus(Duration.ofHours(1));
    assertThat(reaper.reap(muchLater)).isEqualTo(1);
    assertThat(reaper.reap(muchLater.plus(Duration.ofHours(1)))).isEqualTo(1);
    assertThat(reaper.reap(muchLater.plus(Duration.ofHours(2)))).isZero();

    assertThat(reload(s).getLintStatus()).isEqualTo(LintStatus.FAILED);
    assertThat(tasks()).hasSize(3);
  }
}
