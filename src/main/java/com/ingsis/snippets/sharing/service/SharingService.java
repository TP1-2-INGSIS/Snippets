package com.ingsis.snippets.sharing.service;

import java.util.List;
import java.util.UUID;

import com.ingsis.snippets.authorization.AuthorizationService;
import com.ingsis.snippets.clients.permission.PermissionAction;
import com.ingsis.snippets.clients.permission.PermissionClient;
import com.ingsis.snippets.clients.permission.PermissionRole;
import com.ingsis.snippets.clients.permission.UserSummary;
import com.ingsis.snippets.common.identity.CurrentUser;

import org.springframework.stereotype.Service;

/** Compartir snippets (US 7). La autorización de SHARE la resuelve Permission (D05). */
@Service
public class SharingService {

	private final AuthorizationService authorization;

	private final PermissionClient permissions;

	public SharingService(AuthorizationService authorization, PermissionClient permissions) {
		this.authorization = authorization;
		this.permissions = permissions;
	}

	/** Proxy a Permission para que la UI busque usuarios con quienes compartir. */
	public List<UserSummary> searchUsers(String search, int limit) {
		return permissions.searchUsers(search, limit);
	}

	/**
	 * Comparte el snippet con otro usuario como {@code READ}. Si Permission responde 409,
	 * el cliente ya lo traduce a {@code ConflictException(ALREADY_SHARED)}.
	 */
	public void share(CurrentUser user, UUID snippetId, String targetUserId) {
		authorization.require(user, snippetId, PermissionAction.SHARE);
		permissions.grant(snippetId, targetUserId, PermissionRole.READ);
	}

}
