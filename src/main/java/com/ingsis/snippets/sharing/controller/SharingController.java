package com.ingsis.snippets.sharing.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.ingsis.snippets.clients.permission.UserSummary;
import com.ingsis.snippets.common.identity.CurrentUser;
import com.ingsis.snippets.sharing.dto.ShareRequest;
import com.ingsis.snippets.sharing.service.SharingService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Compartir snippets y buscar usuarios (US 7). */
@RestController
class SharingController {

	private final SharingService sharing;

	SharingController(SharingService sharing) {
		this.sharing = sharing;
	}

	@GetMapping("/users")
	List<UserSummary> searchUsers(@RequestParam(required = false, defaultValue = "") String search,
			@RequestParam(defaultValue = "10") int limit) {
		return sharing.searchUsers(search, limit);
	}

	@PostMapping("/snippets/{id}/share")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void share(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody ShareRequest request) {
		sharing.share(user, id, request.userId());
	}

}
