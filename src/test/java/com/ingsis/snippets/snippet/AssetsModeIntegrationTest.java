package com.ingsis.snippets.snippet;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.ingsis.snippets.TestcontainersConfiguration;
import com.ingsis.snippets.snippet.dto.CreateSnippetRequest;
import com.ingsis.snippets.snippet.dto.SnippetDetail;
import com.ingsis.snippets.snippet.dto.UpdateSnippetRequest;
import com.ingsis.snippets.snippet.model.Snippet;
import com.ingsis.snippets.snippet.repository.SnippetRepository;
import com.ingsis.snippets.snippet.storage.SnippetContentMigrator;
import com.ingsis.snippets.snippet.storage.SnippetStorage;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

/**
 * SNIP-4: {@code SNIPPET_STORAGE=assets}. Content goes to Assets (WireMock), never into the row;
 * the startup migrator and the lazy 404 fallback move legacy rows.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {"snippets.storage=assets", "snippets.migration.on-startup=false"})
@Import(TestcontainersConfiguration.class)
class AssetsModeIntegrationTest {

  private static final WireMockServer DOWNSTREAM =
      new WireMockServer(wireMockConfig().dynamicPort());

  private static final WireMockServer ASSETS = new WireMockServer(wireMockConfig().dynamicPort());

  static {
    DOWNSTREAM.start();
    ASSETS.start();
  }

  @LocalServerPort private int port;

  @Autowired private SnippetRepository snippets;

  @Autowired private SnippetStorage storage;

  @Autowired private SnippetContentMigrator migrator;

  @DynamicPropertySource
  static void downstreamProperties(DynamicPropertyRegistry registry) {
    registry.add("services.language.url", DOWNSTREAM::baseUrl);
    registry.add("services.permission.url", DOWNSTREAM::baseUrl);
    registry.add("services.assets.url", ASSETS::baseUrl);
  }

  @AfterAll
  static void stop() {
    DOWNSTREAM.stop();
    ASSETS.stop();
  }

  @BeforeEach
  void stubs() {
    DOWNSTREAM.resetAll();
    ASSETS.resetAll();
    DOWNSTREAM.stubFor(
        post(urlPathMatching("/languages/.*/validate"))
            .willReturn(json("{\"valid\":true,\"errors\":[]}")));
    DOWNSTREAM.stubFor(
        post(urlPathMatching("/languages/.*/lint")).willReturn(json("{\"violations\":[]}")));
    DOWNSTREAM.stubFor(
        post(urlPathEqualTo("/permissions")).willReturn(aResponse().withStatus(201)));
    DOWNSTREAM.stubFor(
        get(urlPathEqualTo("/permissions/check"))
            .willReturn(json("{\"allowed\":true,\"role\":\"OWNER\"}")));
    DOWNSTREAM.stubFor(get(urlPathMatching("/permissions/snippets/.*")).willReturn(json("[]")));
    DOWNSTREAM.stubFor(
        delete(urlPathMatching("/permissions/snippets/.*"))
            .willReturn(aResponse().withStatus(204)));
    DOWNSTREAM.stubFor(
        get(urlPathMatching("/rules/.*|/languages/.*/rules.*")).willReturn(json("{}")));
    snippets.deleteAll();
  }

  private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(
      String body) {
    return aResponse().withHeader("Content-Type", "application/json").withBody(body);
  }

  private RestClient api() {
    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultHeader("X-User-Id", "user-ana")
        .build();
  }

  @Test
  void createReadUpdateDeleteKeepTheContentInAssetsOnly() {
    ASSETS.stubFor(post(urlPathMatching("/v1/assets/.*")).willReturn(aResponse().withStatus(201)));
    ASSETS.stubFor(put(urlPathMatching("/v1/assets/.*")).willReturn(aResponse().withStatus(200)));
    ASSETS.stubFor(
        delete(urlPathMatching("/v1/assets/.*")).willReturn(aResponse().withStatus(204)));

    SnippetDetail created =
        api()
            .post()
            .uri("/snippets")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateSnippetRequest("Assets one", null, "printscript", "1.1", "println(1);"))
            .retrieve()
            .body(SnippetDetail.class);

    assertThat(created).isNotNull();
    String path = "/v1/assets/" + created.id();
    ASSETS.verify(postRequestedFor(urlEqualTo(path)).withRequestBody(equalTo("println(1);")));
    assertThat(snippets.findById(created.id()).orElseThrow().getContent()).isNull();

    // read: the detail comes from Assets
    ASSETS.stubFor(
        get(urlEqualTo(path)).willReturn(aResponse().withStatus(200).withBody("println(1);")));
    SnippetDetail read =
        api().get().uri("/snippets/{id}", created.id()).retrieve().body(SnippetDetail.class);
    assertThat(read).isNotNull();
    assertThat(read.content()).isEqualTo("println(1);");

    // update
    SnippetDetail updated =
        api()
            .put()
            .uri("/snippets/{id}", created.id())
            .contentType(MediaType.APPLICATION_JSON)
            .body(new UpdateSnippetRequest(null, null, null, null, "println(2);"))
            .retrieve()
            .body(SnippetDetail.class);
    assertThat(updated).isNotNull();
    assertThat(updated.content()).isEqualTo("println(2);");
    ASSETS.verify(putRequestedFor(urlEqualTo(path)).withRequestBody(equalTo("println(2);")));
    assertThat(snippets.findById(created.id()).orElseThrow().getContent()).isNull();

    // delete
    api().delete().uri("/snippets/{id}", created.id()).retrieve().toBodilessEntity();
    ASSETS.verify(deleteRequestedFor(urlEqualTo(path)));
    assertThat(snippets.findById(created.id())).isEmpty();
  }

  @Test
  void createRollsBackWhenAssetsFails() {
    ASSETS.stubFor(post(urlPathMatching("/v1/assets/.*")).willReturn(aResponse().withStatus(500)));

    var response =
        api()
            .post()
            .uri("/snippets")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateSnippetRequest("Fails", null, "printscript", "1.1", "x"))
            .exchange((req, res) -> res.getStatusCode().value());

    assertThat(response).isEqualTo(502);
    assertThat(snippets.count()).isZero();
    DOWNSTREAM.verify(0, postRequestedFor(urlPathEqualTo("/permissions")));
  }

  @Test
  void migratorMovesLegacyRowsAndIsIdempotent() {
    UUID a = legacyRow("one");
    UUID b = legacyRow("two");
    snippets.save(new Snippet(UUID.randomUUID(), "empty", null, "printscript", "1.1", null, "ana"));
    ASSETS.stubFor(get(urlPathMatching("/v1/assets/.*")).willReturn(aResponse().withStatus(404)));
    ASSETS.stubFor(post(urlPathMatching("/v1/assets/.*")).willReturn(aResponse().withStatus(201)));

    assertThat(migrator.migrateAll()).isEqualTo(2);

    ASSETS.verify(postRequestedFor(urlEqualTo("/v1/assets/" + a)).withRequestBody(equalTo("one")));
    ASSETS.verify(postRequestedFor(urlEqualTo("/v1/assets/" + b)).withRequestBody(equalTo("two")));
    assertThat(snippets.findById(a).orElseThrow().getContent()).isNull();
    assertThat(snippets.findById(b).orElseThrow().getContent()).isNull();
    assertThat(migrator.migrateAll()).isZero();
    ASSETS.verify(2, postRequestedFor(urlPathMatching("/v1/assets/.*")));
  }

  @Test
  void migratorResumesAfterAFailure() {
    UUID a = legacyRow("one");
    ASSETS.stubFor(get(urlPathMatching("/v1/assets/.*")).willReturn(aResponse().withStatus(404)));
    ASSETS.stubFor(post(urlPathMatching("/v1/assets/.*")).willReturn(aResponse().withStatus(503)));

    assertThat(migrator.migrateAll()).isZero();
    assertThat(snippets.findById(a).orElseThrow().getContent()).isEqualTo("one");

    ASSETS.stubFor(post(urlPathMatching("/v1/assets/.*")).willReturn(aResponse().withStatus(201)));
    assertThat(migrator.migrateAll()).isEqualTo(1);
    assertThat(snippets.findById(a).orElseThrow().getContent()).isNull();
  }

  @Test
  void readFallsBackToTheColumnOnA404AndMigratesTheRow() {
    UUID id = legacyRow("legacy text");
    ASSETS.stubFor(get(urlEqualTo("/v1/assets/" + id)).willReturn(aResponse().withStatus(404)));
    ASSETS.stubFor(post(urlEqualTo("/v1/assets/" + id)).willReturn(aResponse().withStatus(201)));

    assertThat(storage.get(id)).isEqualTo("legacy text");

    ASSETS.verify(postRequestedFor(urlEqualTo("/v1/assets/" + id)));
    assertThat(snippets.findById(id).orElseThrow().getContent()).isNull();
  }

  private UUID legacyRow(String content) {
    UUID id = UUID.randomUUID();
    snippets.save(new Snippet(id, "legacy", null, "printscript", "1.1", content, "ana"));
    return id;
  }
}
