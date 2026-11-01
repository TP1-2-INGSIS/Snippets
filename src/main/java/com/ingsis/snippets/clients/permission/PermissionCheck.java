package com.ingsis.snippets.clients.permission;

import org.jspecify.annotations.Nullable;

/** {@code role} is null when the user has no role at all on the snippet. */
public record PermissionCheck(boolean allowed, @Nullable PermissionRole role) {
}
