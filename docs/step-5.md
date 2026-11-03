# Paso 5 · Persistencia de snippets + `SnippetStorage`

**Qué se buscaba:** dejar el modelo y el almacenamiento listos para el CRUD, sin endpoints. Un snippet se puede guardar, leer y buscar con filtros, orden y paginación desde el repositorio; el contenido queda detrás de una interfaz para poder moverlo a un bucket más adelante (D06).

## Lo central

### Modelo (`snippet.model`)
- Migración `V2__snippet.sql`: tabla `snippet` con todos los campos de §4, `lint_violations` como `jsonb` (D20) e índices en `name`, `language`, `lint_status` y `updated_at` para el listado (D09).
- Entidad `Snippet`: `lintStatus` arranca en `PENDING`; `lintViolations` se mapea con `@JdbcTypeCode(SqlTypes.JSON)` a una lista de `SourceViolation { rule, message, line?, column? }` (un record propio, no el `JsonNode` del cliente, para que el modelo no dependa del paso 2 y Hibernate serialice de forma estable).
- `created_at`/`updated_at` los maneja la entidad con `@PrePersist`/`@PreUpdate`; `owner_id` es desnormalizado y **no** autoriza (D05).
- Enum `LintStatus` (`COMPLIANT`, `NON_COMPLIANT`, `PENDING`, `FAILED`).

### Almacenamiento (`snippet.storage`, D06)
- `SnippetStorage { save, get, remove }`. `DatabaseSnippetStorage` guarda el contenido en la columna `content` de la misma fila. `save` asume la fila ya creada (el snippet nace con su contenido); `get`/`save` sobre un id inexistente fallan con `IllegalStateException`.

### Repositorio (`snippet.repository`, D09)
- `SnippetRepository.search(ids, namePattern, language, lintStatus, Pageable)`: una sola consulta con `id IN (…)` + filtros opcionales + orden + paginación.
- `searchByName(...)`: variante que toma el nombre crudo y arma el patrón "contiene" en minúsculas.

## Nota de implementación (bytea / nulls)
En Postgres, un parámetro `String` nulo dentro de `(:p IS NULL OR …)` se infiere como `bytea` y rompe `lower(…)` (`function lower(bytea) does not exist`). Se resuelve con `CAST(:p AS string)` en ambos usos y pasando el patrón de nombre ya formateado, no concatenado en JPQL.

## Tests (`@DataJpaTest` + Testcontainers)

| Test | Qué cubre |
|---|---|
| `SnippetRepositoryTest` | guardar y releer todos los campos (incluido jsonb), `id IN`, filtro por name/language/lintStatus, orden y paginación |
| `DatabaseSnippetStorageTest` | `get`, `save` (sobrescribe), `remove`, y fallo sobre id inexistente |
| `SnippetTest` | `@PrePersist`/`@PreUpdate` (timestamps) y los mutadores de la entidad |

100 % de líneas en `model`, `repository` y `storage`.

## Cómo verificarlo
```bash
./gradlew build
```
