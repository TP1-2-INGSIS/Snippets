package com.ingsis.snippets.snippet.storage;

import com.ingsis.snippets.snippet.model.Snippet;
import com.ingsis.snippets.snippet.repository.SnippetRepository;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Modo {@code database} (default): el contenido es la columna {@code content} de la fila del
 * snippet. {@code save} asume que la fila ya existe; acá solo se reescribe.
 */
@Component
@ConditionalOnProperty(name = "snippets.storage", havingValue = "database", matchIfMissing = true)
class DatabaseSnippetStorage implements SnippetStorage {

  private final SnippetRepository snippets;

  DatabaseSnippetStorage(SnippetRepository snippets) {
    this.snippets = snippets;
  }

  @Override
  public void save(UUID snippetId, String content) {
    Snippet snippet = require(snippetId);
    snippet.setContent(content);
    snippets.save(snippet);
  }

  @Override
  public String get(UUID snippetId) {
    String content = require(snippetId).getContent();
    if (content == null) {
      throw new IllegalStateException(
          "Snippet " + snippetId + " has no content in the database (was it moved to Assets?)");
    }
    return content;
  }

  /** El contenido vive en la fila: se va cuando quien llama borra el snippet. */
  @Override
  public void remove(UUID snippetId) {
    // nada que borrar aparte de la fila
  }

  private Snippet require(UUID snippetId) {
    return snippets
        .findById(snippetId)
        .orElseThrow(() -> new IllegalStateException("No snippet row for " + snippetId));
  }
}
