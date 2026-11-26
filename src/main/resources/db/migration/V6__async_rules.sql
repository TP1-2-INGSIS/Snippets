-- Reglas asincronas (US12/US15): una fila de estado por snippet y por tarea.
-- format_status: PENDING / DONE / FAILED. Los snippets existentes quedan DONE.
-- *_requested_at y *_attempts los usa el reaper para re-encolar tareas colgadas.
ALTER TABLE snippet ADD COLUMN format_status       VARCHAR(20) NOT NULL DEFAULT 'DONE';
ALTER TABLE snippet ADD COLUMN lint_requested_at   TIMESTAMPTZ;
ALTER TABLE snippet ADD COLUMN lint_attempts       INT         NOT NULL DEFAULT 0;
ALTER TABLE snippet ADD COLUMN format_requested_at TIMESTAMPTZ;
ALTER TABLE snippet ADD COLUMN format_attempts     INT         NOT NULL DEFAULT 0;
-- Ultima tarea encolada por tipo: un resultado con otro taskId es viejo y se descarta.
ALTER TABLE snippet ADD COLUMN lint_task_id        UUID;
ALTER TABLE snippet ADD COLUMN format_task_id      UUID;

CREATE INDEX idx_snippet_format_status ON snippet (format_status);
