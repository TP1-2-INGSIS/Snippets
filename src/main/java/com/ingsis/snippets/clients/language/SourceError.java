package com.ingsis.snippets.clients.language;

/** A problem in a source file: a validation error, a lint violation or a runtime error. */
public record SourceError(String rule, String message, int line, int column) {}
