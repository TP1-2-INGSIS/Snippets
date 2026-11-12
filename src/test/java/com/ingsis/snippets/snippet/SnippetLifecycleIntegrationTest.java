package com.ingsis.snippets.snippet;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.ingsis.snippets.TestcontainersConfiguration;
import com.ingsis.snippets.snippet.dto.CreateSnippetRequest;
import com.ingsis.snippets.snippet.dto.SnippetDetail;
import com.ingsis.snippets.snippet.dto.UpdateSnippetRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

/** End-to-end HTTP + JPA + PostgreSQL integration test verifying serialized response contracts. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SnippetLifecycleIntegrationTest {

  private static final WireMockServer DOWNSTREAM =
      new WireMockServer(wireMockConfig().dynamicPort());

  static {
    DOWNSTREAM.start();
  }

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void downstreamProperties(DynamicPropertyRegistry registry) {
    registry.add("services.language.url", DOWNSTREAM::baseUrl);
    registry.add("services.permission.url", DOWNSTREAM::baseUrl);
  }

  @AfterAll
  static void stopWireMock() {
    DOWNSTREAM.stop();
  }

  @BeforeEach
  void resetStubs() {
    DOWNSTREAM.resetAll();
  }

  @Test
  void createAndUpdateSnippetReturnNonNullTimestamps() {
    DOWNSTREAM.stubFor(
        post(urlPathEqualTo("/languages/printscript/validate"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"valid\":true,\"errors\":[]}")));
    DOWNSTREAM.stubFor(
        post(urlPathEqualTo("/languages/printscript/lint"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"violations\":[]}")));
    DOWNSTREAM.stubFor(
        post(urlPathEqualTo("/permissions")).willReturn(aResponse().withStatus(201)));
    DOWNSTREAM.stubFor(
        get(urlPathEqualTo("/permissions/check"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"allowed\":true,\"role\":\"OWNER\"}")));
    DOWNSTREAM.stubFor(
        get(com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching(
                "/permissions/snippets/.*"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("[{\"userId\":\"user-ana\",\"role\":\"OWNER\"}]")));

    RestClient client =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultHeader("X-User-Id", "user-ana")
            .build();

    SnippetDetail created =
        client
            .post()
            .uri("/snippets")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                new CreateSnippetRequest(
                    "Integration snippet", "desc", "printscript", "1.1", "println(1);"))
            .retrieve()
            .body(SnippetDetail.class);

    assertThat(created).isNotNull();
    assertThat(created.id()).isNotNull();
    assertThat(created.createdAt()).isNotNull();
    assertThat(created.updatedAt()).isNotNull();

    SnippetDetail updated =
        client
            .put()
            .uri("/snippets/{id}", created.id())
            .contentType(MediaType.APPLICATION_JSON)
            .body(new UpdateSnippetRequest("Updated", null, null, null, "println(2);"))
            .retrieve()
            .body(SnippetDetail.class);

    assertThat(updated).isNotNull();
    assertThat(updated.createdAt()).isNotNull();
    assertThat(updated.updatedAt()).isNotNull();
  }
}
