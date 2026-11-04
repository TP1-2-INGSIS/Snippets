package com.ingsis.snippets.authorization;

import com.ingsis.snippets.clients.permission.PermissionAction;
import com.ingsis.snippets.clients.permission.PermissionCheck;
import com.ingsis.snippets.clients.permission.PermissionClient;
import com.ingsis.snippets.clients.permission.PermissionRole;
import com.ingsis.snippets.common.error.ForbiddenException;
import com.ingsis.snippets.common.error.NotFoundException;
import com.ingsis.snippets.common.identity.CurrentUser;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Guards snippet operations with Permission Service (D05). */
@Service
public class AuthorizationService {

  private final PermissionClient permissions;

  public AuthorizationService(PermissionClient permissions) {
    this.permissions = permissions;
  }

  /**
   * @return the user's role on the snippet
   * @throws NotFoundException if the user has no role, so that a foreign snippet looks like a
   *     missing one (D04)
   * @throws ForbiddenException if the user's role is not enough for the action
   */
  public PermissionRole require(CurrentUser user, UUID snippetId, PermissionAction action) {
    PermissionCheck check = permissions.check(user.id(), snippetId, action);
    if (check.role() == null) {
      throw new NotFoundException("Snippet " + snippetId + " not found");
    }
    if (!check.allowed()) {
      throw new ForbiddenException("Role " + check.role() + " can't " + action + " this snippet");
    }
    return check.role();
  }
}
