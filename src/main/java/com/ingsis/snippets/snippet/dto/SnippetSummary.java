package com.ingsis.snippets.snippet.dto;

import java.time.Instant;
import java.util.UUID;

import com.ingsis.snippets.clients.permission.PermissionRole;
import com.ingsis.snippets.snippet.model.LintStatus;

/** Item del listado (§5). */
public record SnippetSummary(UUID id, String name, String language, String version, String ownerId,
		PermissionRole role, LintStatus lintStatus, Instant updatedAt) {
}
