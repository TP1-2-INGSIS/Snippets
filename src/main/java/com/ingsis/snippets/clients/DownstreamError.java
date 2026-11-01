package com.ingsis.snippets.clients;

import java.util.List;

/** Error body shared by the downstream services: {@code { error, message, details }}. */
public record DownstreamError(String error, String message, List<Object> details) {
}
