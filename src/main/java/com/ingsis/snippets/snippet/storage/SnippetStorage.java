package com.ingsis.snippets.snippet.storage;

import java.util.UUID;

/**
 * El contenido de un snippet se lee y escribe solo a través de esta interfaz (D06, §4.1): puede
 * vivir en la columna {@code content} ({@code SNIPPET_STORAGE=database}) o en el Assets service
 * ({@code assets}, D3).
 */
public interface SnippetStorage {

  /** Guarda el contenido de un snippet nuevo. La fila de metadata ya existe. */
  default void create(UUID snippetId, String content) {
    save(snippetId, content);
  }

  /** Reemplaza (o crea) el contenido de un snippet. */
  void save(UUID snippetId, String content);

  String get(UUID snippetId);

  /**
   * Borra el contenido. Idempotente. La fila de metadata la borra quien llama; en modo {@code
   * database} el contenido se va con ella.
   */
  void remove(UUID snippetId);
}
