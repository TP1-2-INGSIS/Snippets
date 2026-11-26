package com.ingsis.snippets.tasks;

import com.ingsis.snippets.snippet.model.Snippet;
import java.util.List;
import java.util.UUID;

/**
 * Tarea que Snippets publica en {@code tasks:printscript} (contrato LANG-2 ↔ SNIP-3). {@code rules}
 * es la config como JSON en un string.
 */
public record TaskEvent(
    UUID taskId,
    UUID snippetId,
    TaskType type,
    String language,
    String version,
    String content,
    String rules,
    List<String> inputs,
    List<String> expectedOutputs,
    String contentHash) {

  /** Arma la tarea para el contenido actual del snippet, con un {@code taskId} nuevo. */
  public static TaskEvent of(Snippet snippet, TaskType type, String rules) {
    return new TaskEvent(
        UUID.randomUUID(),
        snippet.getId(),
        type,
        snippet.getLanguage(),
        snippet.getVersion(),
        snippet.getContent(),
        rules,
        List.of(),
        List.of(),
        ContentHash.sha256(snippet.getContent()));
  }
}
