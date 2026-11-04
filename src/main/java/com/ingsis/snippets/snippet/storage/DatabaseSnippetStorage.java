package com.ingsis.snippets.snippet.storage;

import com.ingsis.snippets.snippet.model.Snippet;
import com.ingsis.snippets.snippet.repository.SnippetRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implementación de este paso (D06): el contenido es la columna {@code content} de la fila del
 * snippet. {@code save} asume que la fila ya existe (el snippet se crea con su contenido inicial);
 * acá solo se reescribe.
 */
@Component
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
    return require(snippetId).getContent();
  }

  @Override
  public void remove(UUID snippetId) {
    snippets.deleteById(snippetId);
  }

  private Snippet require(UUID snippetId) {
    return snippets
        .findById(snippetId)
        .orElseThrow(() -> new IllegalStateException("No snippet row for " + snippetId));
  }
}
