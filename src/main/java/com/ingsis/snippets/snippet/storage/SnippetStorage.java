package com.ingsis.snippets.snippet.storage;

import java.util.UUID;

/**
 * El contenido de un snippet se lee y escribe solo a través de esta interfaz (D06, §4.1),
 * para poder moverlo a un bucket (asset service) más adelante sin tocar la lógica.
 */
public interface SnippetStorage {

	void save(UUID snippetId, String content);

	String get(UUID snippetId);

	void remove(UUID snippetId);

}
