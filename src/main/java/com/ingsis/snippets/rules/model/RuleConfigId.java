package com.ingsis.snippets.rules.model;

import java.io.Serializable;
import java.util.Objects;

/** Clave compuesta de {@link RuleConfig}: (userId, language, kind). */
public record RuleConfigId(String userId, String language, RuleKind kind) implements Serializable {

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof RuleConfigId other)) {
      return false;
    }
    return Objects.equals(userId, other.userId)
        && Objects.equals(language, other.language)
        && kind == other.kind;
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, language, kind);
  }
}
