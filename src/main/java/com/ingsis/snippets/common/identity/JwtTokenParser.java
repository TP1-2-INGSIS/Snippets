package com.ingsis.snippets.common.identity;

import com.ingsis.snippets.common.error.UnauthenticatedException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Parses and extracts the subject claim from Bearer JWT tokens. */
class JwtTokenParser {

  private final ObjectMapper objectMapper;

  JwtTokenParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  String extractSubject(String token) {
    if (token == null || token.isBlank()) {
      throw new UnauthenticatedException("Empty bearer token");
    }
    String[] parts = token.split("\\.");
    if (parts.length != 3) {
      throw new UnauthenticatedException("Invalid bearer token format");
    }
    try {
      byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
      String json = new String(decoded, StandardCharsets.UTF_8);
      JsonNode root = objectMapper.readTree(json);
      JsonNode subNode = root.get("sub");
      if (subNode == null || subNode.asText().isBlank()) {
        throw new UnauthenticatedException("Missing sub claim in token");
      }
      return subNode.asText().strip();
    } catch (IllegalArgumentException | JacksonException e) {
      throw new UnauthenticatedException("Malformed bearer token payload");
    }
  }
}
