package com.ingsis.snippets.snippet.event;

import java.util.UUID;

/**
 * Se publica después de un update que cambió el contenido de un snippet. El grupo F
 * (tests) escucha este evento para correr los tests del snippet (US 16, D13). Mantener
 * el hook como evento desacopla el CRUD de la ejecución de tests.
 */
public record SnippetContentChanged(UUID snippetId) {
}
