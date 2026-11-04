package com.ingsis.snippets.snippet.dto;

import com.ingsis.snippets.clients.permission.PermissionRole;
import com.ingsis.snippets.snippet.model.LintStatus;
import com.ingsis.snippets.snippet.model.SourceViolation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Respuesta detallada de un snippet (§5). {@code sharedWith} solo se completa cuando quien pide es
 * {@code OWNER}; en otros casos es {@code null}.
 */
public record SnippetDetail(
    UUID id,
    String name,
    @Nullable String description,
    String language,
    String version,
    String content,
    String ownerId,
    PermissionRole role,
    LintStatus lintStatus,
    List<SourceViolation> lintViolations,
    @Nullable List<String> sharedWith,
    Instant createdAt,
    Instant updatedAt) {}
