package com.ingsis.snippets.sharing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ingsis.snippets.authorization.AuthorizationService;
import com.ingsis.snippets.clients.permission.PermissionAction;
import com.ingsis.snippets.clients.permission.PermissionClient;
import com.ingsis.snippets.clients.permission.PermissionRole;
import com.ingsis.snippets.clients.permission.UserSummary;
import com.ingsis.snippets.common.error.ConflictException;
import com.ingsis.snippets.common.error.ErrorCode;
import com.ingsis.snippets.common.error.ForbiddenException;
import com.ingsis.snippets.common.identity.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SharingServiceTest {

  private final CurrentUser ana = new CurrentUser("user-ana");

  @Mock private AuthorizationService authorization;

  @Mock private PermissionClient permissions;

  private SharingService service;

  @BeforeEach
  void setUp() {
    service = new SharingService(authorization, permissions);
  }

  @Test
  void searchUsersProxiesToPermission() {
    given(permissions.searchUsers("an", 10))
        .willReturn(List.of(new UserSummary("user-ana", "Ana", "a@x.com")));

    assertThat(service.searchUsers("an", 10))
        .singleElement()
        .satisfies(u -> assertThat(u.id()).isEqualTo("user-ana"));
  }

  @Test
  void shareGrantsReadAfterCheckingShare() {
    UUID id = UUID.randomUUID();
    given(authorization.require(ana, id, PermissionAction.SHARE)).willReturn(PermissionRole.OWNER);

    service.share(ana, id, "user-juan");

    verify(permissions).grant(id, "user-juan", PermissionRole.READ);
  }

  @Test
  void shareWithoutSharePermissionDoesNotGrant() {
    UUID id = UUID.randomUUID();
    given(authorization.require(ana, id, PermissionAction.SHARE))
        .willThrow(new ForbiddenException("Role READ can't SHARE this snippet"));

    assertThatThrownBy(() -> service.share(ana, id, "user-juan"))
        .isInstanceOf(ForbiddenException.class);
    verify(permissions, never())
        .grant(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void shareAlreadySharedPropagatesConflict() {
    UUID id = UUID.randomUUID();
    given(authorization.require(ana, id, PermissionAction.SHARE)).willReturn(PermissionRole.OWNER);
    org.mockito.BDDMockito.willThrow(new ConflictException(ErrorCode.ALREADY_SHARED, "already"))
        .given(permissions)
        .grant(id, "user-juan", PermissionRole.READ);

    assertThatThrownBy(() -> service.share(ana, id, "user-juan"))
        .isInstanceOf(ConflictException.class);
  }
}
