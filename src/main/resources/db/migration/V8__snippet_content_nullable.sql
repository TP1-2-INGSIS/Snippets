-- SNIP-4 (D3): el contenido se mueve a Assets. La columna pasa a nullable: en modo `assets`
-- el migrador la deja en NULL fila por fila y los snippets nuevos no la escriben. Se conserva
-- (no se borra) por un tiempo, por si hay que volver atras.
ALTER TABLE snippet ALTER COLUMN content DROP NOT NULL;
