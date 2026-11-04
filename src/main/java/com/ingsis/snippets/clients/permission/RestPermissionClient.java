package com.ingsis.snippets.clients.permission;

import com.ingsis.snippets.clients.DownstreamCalls;
import com.ingsis.snippets.clients.DownstreamError;
import com.ingsis.snippets.common.error.ApiException;
import com.ingsis.snippets.common.error.ConflictException;
import com.ingsis.snippets.common.error.ErrorCode;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class RestPermissionClient implements PermissionClient {

  private static final String SERVICE = "Permission Service";

  private final RestClient client;

  public RestPermissionClient(RestClient client) {
    this.client = client;
  }

  @Override
  public void grant(UUID snippetId, String userId, PermissionRole role) {
    call(() ->
        client
            .post()
            .uri("/permissions")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new GrantRequest(snippetId, userId, role))
            .retrieve()
            .toBodilessEntity());
  }

  @Override
  public List<SnippetPermission> idsFor(String userId, @Nullable PermissionRole role) {
    return call(() -> {
      RestClient.RequestHeadersSpec<?> request =
          role == null
              ? client.get().uri("/permissions?userId={userId}", userId)
              : client.get().uri("/permissions?userId={userId}&role={role}", userId, role);
      return request.retrieve().body(new ParameterizedTypeReference<List<SnippetPermission>>() {});
    });
  }

  @Override
  public PermissionCheck check(String userId, UUID snippetId, PermissionAction action) {
    return call(() ->
        client
            .get()
            .uri(
                "/permissions/check?userId={userId}&snippetId={snippetId}&action={action}",
                userId,
                snippetId,
                action)
            .retrieve()
            .body(PermissionCheck.class));
  }

  @Override
  public List<UserPermission> usersOf(UUID snippetId) {
    return call(() ->
        client
            .get()
            .uri("/permissions/snippets/{snippetId}", snippetId)
            .retrieve()
            .body(new ParameterizedTypeReference<List<UserPermission>>() {}));
  }

  @Override
  public void deleteAll(UUID snippetId) {
    call(() ->
        client
            .delete()
            .uri("/permissions/snippets/{snippetId}", snippetId)
            .retrieve()
            .toBodilessEntity());
  }

  @Override
  public List<UserSummary> searchUsers(String search, int limit) {
    return call(() ->
        client
            .get()
            .uri("/users?search={search}&limit={limit}", search, limit)
            .retrieve()
            .body(new ParameterizedTypeReference<List<UserSummary>>() {}));
  }

  private static <T> T call(Supplier<T> request) {
    return DownstreamCalls.call(SERVICE, request, RestPermissionClient::translate);
  }

  /**
   * Only {@code ALREADY_EXISTS} is expected. {@code OWNER_ALREADY_EXISTS} can't happen for a
   * freshly generated snippet id, so it stays a downstream failure.
   */
  private static @Nullable ApiException translate(DownstreamError error) {
    if ("ALREADY_EXISTS".equals(error.error())) {
      return new ConflictException(ErrorCode.ALREADY_SHARED, error.message());
    }
    return null;
  }

  private record GrantRequest(UUID snippetId, String userId, PermissionRole role) {}
}
