-- Paso 13: tests por snippet (§4). FK con borrado en cascada: al borrar el snippet
-- se borran sus tests. inputs/expected_outputs son jsonb (string[]).
CREATE TABLE snippet_test (
    id               UUID PRIMARY KEY,
    snippet_id       UUID         NOT NULL REFERENCES snippet (id) ON DELETE CASCADE,
    name             VARCHAR(255) NOT NULL,
    inputs           JSONB        NOT NULL DEFAULT '[]'::jsonb,
    expected_outputs JSONB        NOT NULL DEFAULT '[]'::jsonb,
    last_status      VARCHAR(10)  NOT NULL,
    last_run_at      TIMESTAMPTZ
);

CREATE INDEX idx_snippet_test_snippet_id ON snippet_test (snippet_id);
