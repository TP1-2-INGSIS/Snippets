package com.ingsis.snippets.tasks;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.ingsis.snippets.TestcontainersConfiguration;
import com.ingsis.snippets.snippet.model.LintStatus;
import com.ingsis.snippets.snippet.model.Snippet;
import com.ingsis.snippets.snippet.repository.SnippetRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** Real Postgres + Redis, WireMock as Language and Permission: engine version revalidation. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EngineVersionWatcherIntegrationTest {

  private static final WireMockServer DOWNSTREAM =
      new WireMockServer(wireMockConfig().dynamicPort());

  static {
    DOWNSTREAM.start();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("services.permission.url", DOWNSTREAM::baseUrl);
    registry.add("services.language.url", DOWNSTREAM::baseUrl);
    registry.add("engine.page-size", () -> "3");
    registry.add("tasks.reaper-interval", () -> "1h");
    registry.add("tasks.consumer-enabled", () -> "false");
  }

  @AfterAll
  static void stopWireMock() {
    DOWNSTREAM.stop();
  }

  @Autowired private EngineVersionWatcher watcher;

  @Autowired private SnippetRepository snippets;

  @Autowired private StringRedisTemplate redis;

  @Autowired private TasksProperties props;

  @Autowired private JdbcTemplate jdbc;

  @Autowired private ObjectMapper mapper;

  @Autowired private MockMvc mvc;

  @BeforeEach
  void reset() {
    snippets.deleteAll();
    jdbc.update("DELETE FROM language_engine_version");
    redis.delete(props.stream());
    DOWNSTREAM.resetAll();
  }

  private List<Snippet> saveSnippets(int count, String language) {
    List<Snippet> saved = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      Snippet s =
          new Snippet(UUID.randomUUID(), "n" + i, null, language, "1.1", "println(1);", "user-ana");
      s.setLintStatus(LintStatus.COMPLIANT);
      saved.add(snippets.save(s));
    }
    return saved;
  }

  private void stubLanguages(String engineVersion) {
    DOWNSTREAM.stubFor(
        get(urlPathEqualTo("/languages"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "[{\"id\":\"printscript\",\"versions\":[\"1.1\"],\"engineVersion\":\""
                            + engineVersion
                            + "\"}]")));
  }

  private long queuedTasks() {
    return redis.opsForStream().range(props.stream(), Range.unbounded()).size();
  }

  private void assertAllPending(List<Snippet> saved) {
    for (Snippet s : saved) {
      Snippet reloaded = snippets.findById(s.getId()).orElseThrow();
      assertThat(reloaded.getLintStatus()).isEqualTo(LintStatus.PENDING);
      assertThat(reloaded.getLintTaskId()).isNotNull();
    }
  }

  private String storedVersion() {
    return jdbc.queryForObject(
        "SELECT engine_version FROM language_engine_version WHERE language = 'printscript'",
        String.class);
  }

  @Test
  void firstSightEnqueuesEverySnippetAcrossPages() {
    List<Snippet> saved = saveSnippets(7, "printscript");
    saveSnippets(2, "python");
    stubLanguages("1.0.0");

    assertThat(watcher.check()).isEqualTo(7);

    assertAllPending(saved);
    assertThat(queuedTasks()).isEqualTo(7);
    var task = redis.opsForStream().range(props.stream(), Range.unbounded()).get(0);
    var json = mapper.readTree(String.valueOf(task.getValue().get("data")));
    assertThat(json.get("type").asString()).isEqualTo("LINT");
    assertThat(json.get("language").asString()).isEqualTo("printscript");
    assertThat(storedVersion()).isEqualTo("1.0.0");
  }

  @Test
  void sameVersionEnqueuesNothing() {
    saveSnippets(4, "printscript");
    stubLanguages("1.0.0");
    watcher.check();
    redis.delete(props.stream());

    assertThat(watcher.check()).isZero();

    assertThat(queuedTasks()).isZero();
  }

  @Test
  void versionChangeReenqueuesEverything() {
    List<Snippet> saved = saveSnippets(5, "printscript");
    stubLanguages("1.0.0");
    watcher.check();
    redis.delete(props.stream());
    saved.forEach(s -> {
      Snippet r = snippets.findById(s.getId()).orElseThrow();
      r.setLintStatus(LintStatus.COMPLIANT);
      snippets.save(r);
    });
    stubLanguages("1.1.0");

    assertThat(watcher.check()).isEqualTo(5);

    assertAllPending(saved);
    assertThat(queuedTasks()).isEqualTo(5);
    assertThat(storedVersion()).isEqualTo("1.1.0");
  }

  @Test
  void languageDownDoesNotCrashAndRetriesNextTick() {
    saveSnippets(2, "printscript");
    DOWNSTREAM.stubFor(get(urlPathEqualTo("/languages")).willReturn(aResponse().withStatus(503)));

    assertThat(watcher.check()).isZero();
    assertThat(queuedTasks()).isZero();

    stubLanguages("1.0.0");
    assertThat(watcher.check()).isEqualTo(2);
    assertThat(queuedTasks()).isEqualTo(2);
  }

  @Test
  void languageWithoutEngineVersionIsIgnored() {
    saveSnippets(2, "printscript");
    DOWNSTREAM.stubFor(
        get(urlPathEqualTo("/languages"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("[{\"id\":\"printscript\",\"versions\":[\"1.1\"]}]")));

    assertThat(watcher.check()).isZero();
  }

  @Test
  void concurrentInstancesRevalidateOnlyOnce() throws Exception {
    saveSnippets(9, "printscript");
    stubLanguages("2.0.0");
    int instances = 4;
    ExecutorService pool = Executors.newFixedThreadPool(instances);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Integer>> results = new ArrayList<>();
    for (int i = 0; i < instances; i++) {
      results.add(
          pool.submit(() -> {
            start.await();
            return watcher.check();
          }));
    }
    start.countDown();
    int total = 0;
    for (Future<Integer> f : results) {
      total += f.get();
    }
    pool.shutdown();

    assertThat(total).isEqualTo(9);
    assertThat(queuedTasks()).isEqualTo(9);
  }

  @Test
  void revalidateEndpointRequeuesCallersSnippets() throws Exception {
    List<Snippet> saved = saveSnippets(3, "printscript");
    String body =
        mapper.writeValueAsString(
            saved.stream()
                .map(s -> Map.of("snippetId", s.getId().toString(), "role", "OWNER"))
                .toList());
    DOWNSTREAM.stubFor(
        get(urlPathEqualTo("/permissions"))
            .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(body)));

    mvc.perform(post("/snippets/revalidate").header("X-User-Id", "user-ana"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.queued").value(3));

    assertAllPending(saved);
    assertThat(queuedTasks()).isEqualTo(3);
  }
}
