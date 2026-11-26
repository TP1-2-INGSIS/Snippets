-- Formateo automatico (US12) sin pisar el original (US13): el resultado del
-- formateo se guarda aparte. NULL = todavia no formateado (o contenido cambio).
ALTER TABLE snippet ADD COLUMN formatted_content TEXT;
