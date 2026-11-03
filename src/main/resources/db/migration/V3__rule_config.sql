-- Paso 4: reglas de lint/format por (usuario, lenguaje, tipo) (§4, D07).
-- config es jsonb opaco: este servicio no lo interpreta, lo reenvía a Language Service.
CREATE TABLE rule_config (
    user_id    VARCHAR(255) NOT NULL,
    language   VARCHAR(100) NOT NULL,
    kind       VARCHAR(10)  NOT NULL,
    config     JSONB        NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (user_id, language, kind)
);
