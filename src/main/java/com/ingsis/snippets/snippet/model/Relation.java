package com.ingsis.snippets.snippet.model;

/** Qué snippets pedir en el listado (US 5, D09). */
public enum Relation {

  /** Solo los que el usuario posee (role OWNER). */
  OWNED,
  /** Solo los compartidos con el usuario (role READ). */
  SHARED,
  /** Todos a los que el usuario tiene acceso. */
  ALL
}
