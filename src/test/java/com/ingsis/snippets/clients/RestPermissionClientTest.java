package com.ingsis.snippets.clients;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.ingsis.snippets.clients.permission.PermissionAction;
import com.ingsis.snippets.clients.permission.PermissionCheck;
import com.ingsis.snippets.clients.permission.PermissionClient;
import com.ingsis.snippets.clients.permission.PermissionRole;
import com.ingsis.snippets.clients.permission.SnippetPermission;
import com.ingsis.snippets.clients.permission.UserPermission;
import com.ingsis.snippets.clients.permission.UserSummary;
import com.ingsis.snippets.common.error.ConflictException;
import com.ingsis.snippets.common.error.DownstreamException;
import com.ingsis.snippets.common.error.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class RestPermissionClientTest {

  private static final UUID SNIPPET = UUID.fromString("6f1c0000-0000-0000-0000-000000000001");

  @RegisterExtension
  static WireMockExtension server =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private PermissionClient client;

  @BeforeEach
  void setUp() {
    client = WireMockClients.permission(server.baseUrl());
  }

  private static void stubGrantConflict(String code) {
    server.stubFor(
        post("/permissions")
            .willReturn(
                aResponse()
                    .withStatus(409)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"error\":\"" + code + "\",\"message\":\"already\",\"details\":[]}")));
  }

  @Test
  void grantPostsThePermission() {
    server.stubFor(post("/permissions").willReturn(aResponse().withStatus(201)));

    client.grant(SNIPPET, "user-ana", PermissionRole.OWNER);

    server.verify(
        postRequestedFor(urlEqualTo("/permissions"))
            .withRequestBody(
                equalToJson(
                    "{\"snippetId\":\""
                        + SNIPPET
                        + "\",\"userId\":\"user-ana\",\"role\":\"OWNER\"}")));
  }

  @Test
  void grantOfAnExistingPermissionIsAlreadyShared() {
    stubGrantConflict("ALREADY_EXISTS");

    assertThatThrownBy(() -> client.grant(SNIPPET, "user-juan", PermissionRole.READ))
        .isInstanceOfSatisfying(
            ConflictException.class,
            ex -> assertThat(ex.code()).isEqualTo(ErrorCode.ALREADY_SHARED));
  }

  @Test
  void secondOwnerIsADownstreamFailure() {
    stubGrantConflict("OWNER_ALREADY_EXISTS");

    assertThatThrownBy(() -> client.grant(SNIPPET, "user-juan", PermissionRole.OWNER))
        .isInstanceOf(DownstreamException.class);
  }

  @Test
  void idsForFiltersByRole() {
    server.stubFor(
        get(urlPathEqualTo("/permissions"))
            .withQueryParam("userId", equalTo("user-ana"))
            .withQueryParam("role", equalTo("READ"))
            .willReturn(okJson("[{\"snippetId\":\"" + SNIPPET + "\",\"role\":\"READ\"}]")));

    assertThat(client.idsFor("user-ana", PermissionRole.READ))
        .containsExactly(new SnippetPermission(SNIPPET, PermissionRole.READ));
  }

  @Test
  void idsForWithoutRoleDoesNotSendIt() {
    server.stubFor(get(urlEqualTo("/permissions?userId=user-ana")).willReturn(okJson("[]")));

    assertThat(client.idsFor("user-ana", null)).isEmpty();
  }

  @Test
  void userIdIsEncodedInTheQuery() {
    server.stubFor(
        get(urlPathEqualTo("/permissions"))
            .withQueryParam("userId", equalTo("auth0|a&b+c"))
            .willReturn(okJson("[]")));

    assertThat(client.idsFor("auth0|a&b+c", null)).isEmpty();
  }

  @Test
  void checkAllowed() {
    server.stubFor(
        get(urlPathEqualTo("/permissions/check"))
            .withQueryParam("userId", equalTo("user-ana"))
            .withQueryParam("snippetId", equalTo(SNIPPET.toString()))
            .withQueryParam("action", equalTo("WRITE"))
            .willReturn(okJson("{\"allowed\":true,\"role\":\"OWNER\"}")));

    assertThat(client.check("user-ana", SNIPPET, PermissionAction.WRITE))
        .isEqualTo(new PermissionCheck(true, PermissionRole.OWNER));
  }

  @Test
  void checkWithoutRole() {
    server.stubFor(
        get(urlPathEqualTo("/permissions/check"))
            .willReturn(okJson("{\"allowed\":false,\"role\":null}")));

    assertThat(client.check("user-ana", SNIPPET, PermissionAction.READ))
        .isEqualTo(new PermissionCheck(false, null));
  }

  @Test
  void usersOf() {
    server.stubFor(
        get("/permissions/snippets/" + SNIPPET)
            .willReturn(okJson("[{\"userId\":\"user-juan\",\"role\":\"READ\"}]")));

    assertThat(client.usersOf(SNIPPET))
        .containsExactly(new UserPermission("user-juan", PermissionRole.READ));
  }

  @Test
  void deleteAllRemovesThePermissionsOfTheSnippet() {
    server.stubFor(delete("/permissions/snippets/" + SNIPPET).willReturn(noContent()));

    client.deleteAll(SNIPPET);

    server.verify(deleteRequestedFor(urlEqualTo("/permissions/snippets/" + SNIPPET)));
  }

  @Test
  void searchUsers() {
    server.stubFor(
        get(urlPathEqualTo("/users"))
            .withQueryParam("search", equalTo("ju"))
            .withQueryParam("limit", equalTo("10"))
            .willReturn(
                okJson("[{\"id\":\"user-juan\",\"name\":\"Juan\",\"email\":\"juan@mail.com\"}]")));

    assertThat(client.searchUsers("ju", 10))
        .containsExactly(new UserSummary("user-juan", "Juan", "juan@mail.com"));
  }

  @Test
  void serverErrorIsDownstream() {
    server.stubFor(get(urlPathEqualTo("/permissions/check")).willReturn(serverError()));

    assertThatThrownBy(() -> client.check("user-ana", SNIPPET, PermissionAction.READ))
        .isInstanceOf(DownstreamException.class)
        .hasMessage("Permission Service responded 500");
  }
}
