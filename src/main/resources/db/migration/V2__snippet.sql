-- Paso 5: tabla snippet (§4 del spec).
-- content vive acá por ahora (D06, SnippetStorage); más adelante puede moverse a un bucket.
-- lint_violations es jsonb (D20). owner_id es desnormalizado: NO autoriza (D05).
CREATE TABLE snippet (
    id              UUID PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    description     VARCHAR(1000),
    language        VARCHAR(100) NOT NULL,
    version         VARCHAR(50)  NOT NULL,
    content         TEXT         NOT NULL,
    owner_id        VARCHAR(255) NOT NULL,
    lint_status     VARCHAR(20)  NOT NULL,
    lint_violations JSONB        NOT NULL DEFAULT '[]'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL
);

-- Índices para los filtros y el orden del listado (US 5, D09).
CREATE INDEX idx_snippet_name        ON snippet (name);
CREATE INDEX idx_snippet_language    ON snippet (language);
CREATE INDEX idx_snippet_lint_status ON snippet (lint_status);
CREATE INDEX idx_snippet_updated_at  ON snippet (updated_at);
