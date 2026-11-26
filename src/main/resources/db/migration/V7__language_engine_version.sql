-- Ultima engineVersion (version del artefacto CNC) con la que se revalidaron los snippets de cada
-- lenguaje. NULL = fila creada pero todavia sin revalidar (la fila se toma con FOR UPDATE como
-- guarda de concurrencia entre instancias).
CREATE TABLE language_engine_version (
  language       VARCHAR(100) PRIMARY KEY,
  engine_version VARCHAR(200),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
