package com.ingsis.snippets.clients.language;

import java.util.List;

public record ValidationResult(boolean valid, List<SourceError> errors) {}
