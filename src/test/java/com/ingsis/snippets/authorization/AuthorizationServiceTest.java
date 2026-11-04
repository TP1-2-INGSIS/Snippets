package com.ingsis.snippets.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ingsis.snippets.clients.permission.PermissionAction;
import com.ingsis.snippets.clients.permission.PermissionCheck;
import com.ingsis.snippets.clients.permission.PermissionClient;
import com.ingsis.snippets.clients.permission.PermissionRole;
import com.ingsis.snippets.common.error.ForbiddenException;
import com.ingsis.snippets.common.error.NotFoundException;
import com.ingsis.snippets.common.identity.CurrentUser;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthorizationServiceTest {

  private static final CurrentUser USER = new CurrentUser("user-ana");
  private static final UUID SNIPPET = UUID.randomUUID();

  private final PermissionClient permissions = mock(PermissionClient.class);
  private final AuthorizationService service = new AuthorizationService(permissions);

  private void givenCheck(PermissionAction action, boolean allowed, PermissionRole role) {
    when(permissions.check(USER.id(), SNIPPET, action))
        .thenReturn(new PermissionCheck(allowed, role));
  }

  @Test
  void allowedReturnsTheRole() {
    givenCheck(PermissionAction.READ, true, PermissionRole.READ);

    assertThat(service.require(USER, SNIPPET, PermissionAction.READ))
        .isEqualTo(PermissionRole.READ);
  }

  @Test
  void noRoleIsNotFound() {
    givenCheck(PermissionAction.READ, false, null);

    assertThatThrownBy(() -> service.require(USER, SNIPPET, PermissionAction.READ))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void insufficientRoleIsForbidden() {
    givenCheck(PermissionAction.WRITE, false, PermissionRole.READ);

    assertThatThrownBy(() -> service.require(USER, SNIPPET, PermissionAction.WRITE))
        .isInstanceOf(ForbiddenException.class);
  }
}
