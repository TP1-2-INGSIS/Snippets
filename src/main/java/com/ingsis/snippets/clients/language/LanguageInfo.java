package com.ingsis.snippets.clients.language;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * {@code engineVersion} es la versión del artefacto CNC; {@code null} si Language no la informa.
 */
public record LanguageInfo(String id, List<String> versions, @Nullable String engineVersion) {}
