package com.ingsis.snippets.clients.permission;

import java.util.UUID;

public record SnippetPermission(UUID snippetId, PermissionRole role) {
}
