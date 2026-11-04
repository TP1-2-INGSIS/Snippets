package com.ingsis.snippets.clients.permission;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Permission Service: the source of truth of who can do what on a snippet (D05). */
public interface PermissionClient {

  void grant(UUID snippetId, String userId, PermissionRole role);

  /**
   * @param role only snippets with this role, or {@code null} for all of them
   */
  List<SnippetPermission> idsFor(String userId, @Nullable PermissionRole role);

  PermissionCheck check(String userId, UUID snippetId, PermissionAction action);

  List<UserPermission> usersOf(UUID snippetId);

  void deleteAll(UUID snippetId);

  List<UserSummary> searchUsers(String search, int limit);
}
