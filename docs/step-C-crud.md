# Grupo C · Snippet CRUD + re-lint/format (pasos 6–10, 15)

**Qué se buscaba:** el CRUD completo de snippets (crear por JSON y archivo, ver, listar, actualizar, borrar) y, como cierre del tema reglas, aplicar las reglas a todos los snippets del usuario cuando se hace `PUT /rules`.

## Lo central

### `SnippetService`
- **Crear (D10):** `validate` (422 si inválido, sin guardar ni llamar a Permission) → `lint` con las reglas LINT del usuario → guardar → `grant OWNER`. Si el grant falla, se borra el snippet (compensación) y se responde 502.
- **Ver (D04):** `require(READ)` → `SnippetDetail` con el rol; si es OWNER, completa `sharedWith` con `usersOf` (excluyendo al propio owner).
- **Listar (D09):** `relation` → rol (`OWNED`=OWNER, `SHARED`=READ, `ALL`=sin filtro); ids desde Permission; una consulta a la base con `id IN (…)` + filtros + orden + paginación. El rol de cada item sale de Permission.
- **Actualizar:** `require(WRITE)`; si cambian content/language/version, revalida (422) y re-lintea, y publica `SnippetContentChanged` (hook del grupo F). Solo metadata no llama a Language.
- **Borrar:** `require(WRITE)` → borra snippet → `deleteAll` en Permission (idempotente; si falla, se loguea y no se revierte el borrado).

### `RuleApplier` (paso 15, D14)
`relint`/`reformat` sobre los snippets OWNER del usuario en un lenguaje, uno por uno. Lint actualiza el estado (falla → `FAILED`); format reescribe el contenido (falla → sin cambios). Devuelve `{processed, failed}`. El `PUT /rules/{lang}/lint|format` (grupo B) ahora, además de guardar, llama a `RuleApplier` y responde esos contadores.

### Controller
`POST /snippets` y `/snippets/upload`, `GET /snippets/{id}`, `GET /snippets`, `PUT /snippets/{id}` y `/upload`, `DELETE /snippets/{id}`. El `sort` fuera de la lista permitida → 400.

## Tests
`SnippetServiceTest` (12: create válido/inválido/compensación, get owner/reader, list relation/vacío, update metadata/contenido/ inválido, delete feliz/con Permission caído), `RuleApplierTest` (3), `SnippetControllerTest` (10, incl. multipart y validación), `RulesControllerTest` actualizado (5). Cobertura global ~95 %.

## Cómo verificarlo
```bash
./gradlew build
```
