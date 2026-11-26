package com.ingsis.snippets.snippet.storage;

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
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.ingsis.snippets.clients.ClientsConfiguration;
import com.ingsis.snippets.clients.ServicesProperties;
import com.ingsis.snippets.common.error.DownstreamException;
import com.ingsis.snippets.snippet.model.Snippet;
import com.ingsis.snippets.snippet.repository.SnippetRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.client.RestClient;

/** The Assets contract ({@code /v1/assets/{id}}) against WireMock; the repository is mocked. */
class AssetSnippetStorageTest {

  private static final WireMockServer ASSETS = new WireMockServer(wireMockConfig().dynamicPort());

  private final UUID id = UUID.randomUUID();

  private final String path = "/v1/assets/" + id;

  private SnippetRepository snippets;

  private AssetSnippetStorage storage;

  @BeforeAll
  static void start() {
    ASSETS.start();
  }

  @AfterAll
  static void stop() {
    ASSETS.stop();
  }

  @BeforeEach
  void setUp() {
    ASSETS.resetAll();
    snippets = mock(SnippetRepository.class);
    ServicesProperties.Endpoint endpoint = new ServicesProperties.Endpoint(ASSETS.baseUrl());
    ServicesProperties properties =
        new ServicesProperties(
            endpoint, endpoint, endpoint, Duration.ofSeconds(1), Duration.ofSeconds(2));
    RestClient client =
        ClientsConfiguration.restClient(RestClient.builder(), properties.assets(), properties);
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    given(transactions.getTransaction(any())).willReturn(new SimpleTransactionStatus());
    storage = new AssetSnippetStorage(client, snippets, transactions);
  }

  private Snippet row(String content) {
    return new Snippet(id, "n", null, "printscript", "1.1", content, "ana");
  }

  @Test
  void createPostsTheRawBytes() {
    ASSETS.stubFor(post(urlEqualTo(path)).willReturn(aResponse().withStatus(201)));

    storage.create(id, "println(\"ñ\");");

    ASSETS.verify(
        postRequestedFor(urlEqualTo(path))
            .withHeader("Content-Type", equalTo("text/plain;charset=UTF-8"))
            .withRequestBody(equalTo("println(\"ñ\");")));
  }

  @Test
  void savePutsTheBytesAndClearsTheLegacyColumn() {
    ASSETS.stubFor(put(urlEqualTo(path)).willReturn(aResponse().withStatus(200)));
    Snippet legacy = row("old");
    given(snippets.findById(id)).willReturn(Optional.of(legacy));

    storage.save(id, "new");

    ASSETS.verify(putRequestedFor(urlEqualTo(path)).withRequestBody(equalTo("new")));
    assertThat(legacy.getContent()).isNull();
  }

  @Test
  void getReturnsTheBlobAsUtf8() {
    ASSETS.stubFor(
        get(urlEqualTo(path))
            .willReturn(
                aResponse().withStatus(200).withBody("hola ñ".getBytes(StandardCharsets.UTF_8))));

    assertThat(storage.get(id)).isEqualTo("hola ñ");
    verify(snippets, never()).findById(any());
  }

  @Test
  void getReturnsAnEmptyBlobAsEmptyString() {
    ASSETS.stubFor(get(urlEqualTo(path)).willReturn(aResponse().withStatus(200)));

    assertThat(storage.get(id)).isEmpty();
  }

  @Test
  void get404WithoutLegacyContentIsADownstreamError() {
    ASSETS.stubFor(get(urlEqualTo(path)).willReturn(aResponse().withStatus(404)));
    given(snippets.findById(id)).willReturn(Optional.of(row(null)));

    assertThatThrownBy(() -> storage.get(id))
        .isInstanceOf(DownstreamException.class)
        .hasMessageContaining("no content");
  }

  @Test
  void get404WithLegacyContentServesItAndMigratesTheRow() {
    ASSETS.stubFor(get(urlEqualTo(path)).willReturn(aResponse().withStatus(404)));
    ASSETS.stubFor(post(urlEqualTo(path)).willReturn(aResponse().withStatus(201)));
    Snippet legacy = row("legacy");
    given(snippets.findById(id)).willReturn(Optional.of(legacy));

    assertThat(storage.get(id)).isEqualTo("legacy");

    ASSETS.verify(postRequestedFor(urlEqualTo(path)).withRequestBody(equalTo("legacy")));
    assertThat(legacy.getContent()).isNull();
  }

  @Test
  void lazyMigrationFailureStillServesTheLegacyContent() {
    ASSETS.stubFor(get(urlEqualTo(path)).willReturn(aResponse().withStatus(404)));
    ASSETS.stubFor(post(urlEqualTo(path)).willReturn(aResponse().withStatus(500)));
    Snippet legacy = row("legacy");
    given(snippets.findById(id)).willReturn(Optional.of(legacy));

    assertThat(storage.get(id)).isEqualTo("legacy");
    assertThat(legacy.getContent()).isEqualTo("legacy");
  }

  @Test
  void getServerErrorIsADownstreamError() {
    ASSETS.stubFor(get(urlEqualTo(path)).willReturn(aResponse().withStatus(500)));

    assertThatThrownBy(() -> storage.get(id))
        .isInstanceOf(DownstreamException.class)
        .hasMessageContaining("500");
  }

  @Test
  void removeDeletes() {
    ASSETS.stubFor(delete(urlEqualTo(path)).willReturn(aResponse().withStatus(204)));

    storage.remove(id);

    ASSETS.verify(deleteRequestedFor(urlEqualTo(path)));
  }

  @Test
  void removeOfAMissingBlobIsIdempotent() {
    ASSETS.stubFor(delete(urlEqualTo(path)).willReturn(aResponse().withStatus(404)));

    storage.remove(id);
  }

  @Test
  void removeServerErrorIsADownstreamError() {
    ASSETS.stubFor(delete(urlEqualTo(path)).willReturn(aResponse().withStatus(503)));

    assertThatThrownBy(() -> storage.remove(id)).isInstanceOf(DownstreamException.class);
  }

  @Test
  void createServerErrorAndSaveErrorAreDownstreamErrors() {
    ASSETS.stubFor(post(urlEqualTo(path)).willReturn(aResponse().withStatus(500)));
    ASSETS.stubFor(put(urlEqualTo(path)).willReturn(aResponse().withStatus(400)));

    assertThatThrownBy(() -> storage.create(id, "x")).isInstanceOf(DownstreamException.class);
    assertThatThrownBy(() -> storage.save(id, "x")).isInstanceOf(DownstreamException.class);
  }

  @Test
  void unreachableAssetsIsADownstreamError() {
    ASSETS.stubFor(
        get(urlEqualTo(path)).willReturn(aResponse().withFixedDelay(3000).withStatus(200)));

    assertThatThrownBy(() -> storage.get(id))
        .isInstanceOf(DownstreamException.class)
        .hasMessageContaining("unavailable");
  }

  @Test
  void migrateUploadsWhenAssetsHasNoContentAndClearsTheColumn() {
    ASSETS.stubFor(get(urlEqualTo(path)).willReturn(aResponse().withStatus(404)));
    ASSETS.stubFor(post(urlEqualTo(path)).willReturn(aResponse().withStatus(201)));
    Snippet legacy = row("legacy");
    given(snippets.findById(id)).willReturn(Optional.of(legacy));

    assertThat(storage.migrate(id)).isTrue();

    assertThat(legacy.getContent()).isNull();
    ASSETS.verify(postRequestedFor(urlEqualTo(path)));
  }

  @Test
  void migrateDoesNotOverwriteWhatAssetsAlreadyHas() {
    ASSETS.stubFor(get(urlEqualTo(path)).willReturn(aResponse().withStatus(200).withBody("newer")));
    Snippet legacy = row("stale");
    given(snippets.findById(id)).willReturn(Optional.of(legacy));

    assertThat(storage.migrate(id)).isTrue();

    assertThat(legacy.getContent()).isNull();
    ASSETS.verify(0, postRequestedFor(urlEqualTo(path)));
  }

  @Test
  void migrateOfARowWithoutContentIsANoOp() {
    given(snippets.findById(id)).willReturn(Optional.of(row(null)));

    assertThat(storage.migrate(id)).isFalse();
    assertThat(ASSETS.getAllServeEvents()).isEmpty();
  }

  @Test
  void migrateOfAMissingRowIsANoOp() {
    given(snippets.findById(id)).willReturn(Optional.empty());

    assertThat(storage.migrate(id)).isFalse();
  }
}
