# Snippet Service · Plan de implementación

Fuente: `../SnippetsSearcher-design/specs/snippet-service.md`. Decisiones: `design-decisions.md` (se citan como `Dxx`).

Cada paso implementa **una capacidad** completa: endpoints, lógica, persistencia y tests. Se puede mergear por separado, y al terminarlo el servicio compila, pasa los tests y mantiene coverage ≥ 80 %.

## Definition of Done (aplica a todos los pasos)

- [ ] Endpoints del paso con el contrato exacto del spec (paths, body, códigos, formato de error D03).
- [ ] Tests unitarios + de controller (+ repositorio si hay persistencia nueva).
- [ ] `./gradlew build` en verde, con la verificación de JaCoCo ≥ 80 %.
- [ ] Decisiones nuevas o cambiadas, anotadas en `design-decisions.md`.
- [ ] `docs/step-<n>.md`: un párrafo con lo que se buscaba en el paso, más lo central de lo implementado (componentes, cómo funciona, tests, cómo verificarlo).
- [ ] Checkbox del paso marcado en este archivo.

## Mapa

A partir del paso 3, los pasos se **agrupan por PR** cuando comparten la misma
superficie de código (mismo controller/servicio/repositorio). Cada grupo es una
branch y un PR; adentro conserva los pasos originales como sub-tareas, con su
contrato y sus tests. El Definition of Done aplica al **PR** (compila, pasa tests,
coverage ≥ 80 %).

| PR | Capacidad | Pasos | US | Branch | Depende de |
|---|---|---|---|---|---|
| — | Base del proyecto | 0 | — | `feature/project-setup` | — |
| — | Identidad y errores | 1 | — | `feature/identity-and-errors` | 0 |
| — | Clientes Language/Permission | 2 | — | `feature/service-clients` | 1 |
| — | Lenguajes disponibles | 3 | 3 | `feature/languages-endpoint` | 2 |
| A | Persistencia + `SnippetStorage` | 5 | — | `feature/snippet-persistence` | 0 |
| B | Reglas de lint/format | 4 | 11, 14 | `feature/rules-config` | 1 |
| C | Snippet CRUD + re-lint/format | 6, 7, 8, 9, 10, 15 | 1–6, 12, 15 | `feature/snippet-crud` | 2, B, A |
| D | Compartir | 11 | 7 | `feature/share-snippet` | C |
| E | Descargar | 12 | 13 | `feature/download-snippet` | B, C |
| F | Tests de snippet | 13, 14 | 8, 9, 16 | `feature/snippet-tests` | C |
| G | Cierre: README, contrato, coverage | 16 | — | `feature/release-closeout` | todos |

**Agrupaciones y por qué:**
- **C (6–10 + 15):** crear/ver/listar/actualizar/borrar comparten `SnippetController`,
  `SnippetService`, `SnippetRepository` y `SnippetDetail`. El paso 15 (re-lint/format
  masivo) se pega acá porque necesita el listado de snippets OWNER (dependía del 8),
  que ya vive en este grupo; así la branch de reglas (B) no arrastra esa dependencia.
- **B (4) queda solo:** entrega `GET|PUT /rules` guardando la config. El disparo del
  re-lint/format desde el `PUT` se implementa en C, donde ya está `RuleApplier`.
- **F (13 + 14):** CRUD de tests y su ejecución comparten `SnippetTestController`; el 14
  además engancha el hook de contenido del paso 9 (en C).
- **A (5), D (11), E (12), G (16)** quedan como PRs propios: A es el cimiento del CRUD;
  D y E son endpoints acotados con lógica propia; G es el cierre.

Los pasos conservan su numeración original en las secciones de abajo para no perder
trazabilidad con el spec y las decisiones.

---

## Paso 0 · Base del proyecto
**Objetivo:** dejar listo el esqueleto para empezar a escribir features.

- Confirmar D16–D20. Renombrar paquete y aplicación (D17).
- Dependencias (starters modulares de Boot 4): `spring-boot-starter-webmvc`, `-data-jpa`, `-validation`, `-flyway` + `flyway-database-postgresql`, `postgresql`. Para test: `-webmvc-test`, `-data-jpa-test`, `spring-boot-testcontainers`, `testcontainers-postgresql`. WireMock se agrega en el paso 2.
- Plugin `jacoco` con `jacocoTestCoverageVerification` (mínimo 0.80), conectado a `check`.
- `application.yml`: puerto 8080, datasource por env (`DB_URL`, `DB_USER`, `DB_PASSWORD`), `LANGUAGE_SERVICE_URL`, `PERMISSION_SERVICE_URL`.
- Migración `V1__init.sql` vacía o con un esquema mínimo.
- CI (GitHub Actions): `./gradlew build` en cada PR.

**Tests:** `contextLoads` con Testcontainers.
**Listo cuando:** `./gradlew build` pasa localmente y en CI, con el reporte de JaCoCo generado.

- [x] Paso 0 — 2026-10-27: build verde, `contextLoads` contra Postgres 16 (Testcontainers), Flyway aplica V1, JaCoCo ≥ 80 % conectado a `check`, workflow `.github/workflows/ci.yml`.

## Paso 1 · Identidad y errores transversales
**Objetivo:** que cualquier endpoint futuro tenga ya resueltos el usuario y el formato de error.

- `CurrentUser` + resolver o filtro de `X-User-Id` → 401 `UNAUTHENTICATED` si falta (D02).
- `ErrorResponse { error, message, details }` + `@RestControllerAdvice` (D03).
- Excepciones de dominio: `NotFoundException`, `ForbiddenException`, `InvalidSnippetException(details)`, `UnsupportedLanguageException`, `ConflictException(code)`, `DownstreamException`.
- Errores de Bean Validation → 400 `BAD_REQUEST` con los campos en `details`.

**Tests:** `@WebMvcTest` con un controller de prueba: con header, sin header, cada excepción mapeada a su código.
**Listo cuando:** toda excepción de dominio sale con el código HTTP y el `error` de la tabla de D03.

- [x] Paso 1 — 2026-10-27: `common.identity` + `common.error`; 18 tests nuevos (`@WebMvcTest` con controllers de prueba), 100 % de líneas.

## Paso 2 · Clientes de Language y Permission
**Objetivo:** hablar con los otros servicios con contratos tipados y errores traducidos (D11).

- `LanguageClient`: `languages()`, `validate`, `lint`, `format`, `test` (`execute` no se usa en este paso).
- `PermissionClient`: `grant`, `idsFor(userId, role?)`, `check(userId, snippetId, action)`, `usersOf(snippetId)`, `deleteAll(snippetId)`, `searchUsers(search, limit)`.
- DTOs de request/response según §6 del spec, más `SourceError { rule, message, line, column }`.
- Implementaciones con `RestClient`. Traducción de errores: 5xx o timeout → `DownstreamException`; `404 LANGUAGE_NOT_FOUND` / `400 UNSUPPORTED_VERSION` → `UnsupportedLanguageException`; `409` de Permission → `ConflictException`.
- Timeouts de conexión y lectura configurables.
- `AuthorizationService.require(user, snippetId, action)`: aplica D04 (404 vs 403) sobre `check`.

**Tests:** WireMock por cada endpoint (caso feliz + cada error traducido). Unitarios de `AuthorizationService`.
**Listo cuando:** los dos clientes cubren el contrato de §6, y el contrato está probado contra stubs.

- [x] Paso 2 — 2026-10-30: `clients.language`, `clients.permission`, `authorization.AuthorizationService`; tests con WireMock y Mockito.

## Paso 3 · Lenguajes disponibles
**Objetivo:** que la UI pueda armar el selector de lenguaje y versión.

- `GET /languages` → proxy a `LanguageClient.languages()`.

**Tests:** controller con el cliente mockeado. Si Language falla → 502.
**Listo cuando:** devuelve `[{id, versions}]` tal cual.

- [x] Paso 3 — 2026-11-01: `languages.LanguageController`, `GET /languages` como proxy a `LanguageClient.languages()`; `@WebMvcTest` con el cliente mockeado (caso feliz, 502 si Language falla, 401 sin header), 100 % de líneas.

## Paso 4 · Reglas de lint/format (configuración)
**Objetivo:** que cada usuario pueda ver y cambiar sus reglas (US 11, 14). En este paso **no** se aplican todavía a los snippets.

- Migración `rule_config (user_id, language, kind, config jsonb, updated_at)` con PK compuesta.
- `resources/rules/printscript-lint.json` y `printscript-format.json` (D07).
- `RulesService.get(user, lang, kind)` → la guardada o el default (`isDefault`). `RulesService.save(...)` hace upsert.
- `GET|PUT /rules/{language}/lint` y `GET|PUT /rules/{language}/format`. Un lenguaje sin archivo de defaults → 400 `UNSUPPORTED_LANGUAGE`.

**Tests:** repo (Testcontainers), servicio (default vs guardada), controller.
**Listo cuando:** `GET` sin datos devuelve el default con `isDefault: true`, y después de un `PUT` devuelve lo guardado con `isDefault: false`.

- [x] Paso 4 (PR B) — 2026-11-01: `rules.*` (`RuleConfig`+`RuleConfigId`, `RuleKind`, repo, `RulesService`, `RulesController`), migración `V3`, defaults de printscript. El `PUT` guarda; el re-lint/format masivo se hace en el grupo C. Tests de repo/servicio/controller.

## Paso 5 · Persistencia de snippets + `SnippetStorage`
**Objetivo:** el modelo y el almacenamiento listos, sin endpoints.

- Migración `snippet` (todos los campos de §4), con índices para `name`, `language`, `lint_status` y `updated_at`.
- Entidad `Snippet` + enum `LintStatus`. `lint_violations` en jsonb (D20).
- `SnippetStorage` + `DatabaseSnippetStorage` (D06).
- `SnippetRepository` con consultas por `id IN (...)` + filtros + `Pageable`.

**Tests:** `@DataJpaTest` + Testcontainers: guardar, leer y filtrar por cada campo y orden.
**Listo cuando:** se pueden guardar y consultar snippets con filtros y paginación desde el repositorio.

- [x] Paso 5 (PR A) — 2026-11-01: `snippet.model` (`Snippet`, `LintStatus`, `SourceViolation`), `snippet.storage` (`SnippetStorage` + `DatabaseSnippetStorage`), `snippet.repository` (`search`/`searchByName`), migración `V2__snippet.sql`. `@DataJpaTest` + Testcontainers; 100 % de líneas en los tres paquetes.

## Paso 6 · Crear snippet
**Objetivo:** US 1 (archivo) y US 3 (editor).

- `POST /snippets` (JSON) y `POST /snippets/upload` (multipart: `file`, `name`, `description`, `language`, `version`). Los dos llaman a un mismo `SnippetService.create`.
- Flujo (D10): validar → si no es válido, 422 `INVALID_SNIPPET` → lintear con las reglas LINT del usuario → guardar → `grant OWNER` → si falla, compensar y responder 502.
- Response `SnippetDetail` (sin `sharedWith` todavía).

**Tests:** servicio con mocks: válido, inválido (no guarda ni llama a Permission), falla Permission (compensa), falla Language (502). Controller: JSON y multipart.
**Listo cuando:** se cumplen los dos primeros criterios de aceptación del spec (§9).

- [x] Paso 6 (PR C) — 2026-11-01: ver docs/step-C-crud.md

## Paso 7 · Ver un snippet
**Objetivo:** US 6.

- `GET /snippets/{id}` → `require(READ)` → `SnippetDetail` con `role`. Si el usuario es OWNER, completa `sharedWith` con `usersOf`.

**Tests:** OWNER (con `sharedWith`), READ (sin `sharedWith`), sin rol (404), inexistente (404).
**Listo cuando:** D04 se cumple en el endpoint.

- [x] Paso 7 (PR C) — 2026-11-01: ver docs/step-C-crud.md

## Paso 8 · Listar snippets
**Objetivo:** US 5.

- `GET /snippets?relation&name&language&lintStatus&sort&order&page&size` según la tabla del spec (D09).
- `relation` → `OWNED` = role OWNER, `SHARED` = role READ, `ALL` = sin role. El `role` de cada item sale de la respuesta de Permission.
- Validación de params (`sort` fuera de la lista → 400).

**Tests:** servicio (mapeo de relation, merge de roles), repo (cada filtro y orden), controller (defaults y validación).
**Listo cuando:** filtra, ordena y pagina según el criterio del spec.

- [x] Paso 8 (PR C) — 2026-11-01: ver docs/step-C-crud.md

## Paso 9 · Actualizar snippet
**Objetivo:** US 2 (archivo) y US 4 (editor).

- `PUT /snippets/{id}` (JSON, campos opcionales) y `PUT /snippets/{id}/upload` (multipart) → `require(WRITE)`.
- Si cambia `content`, `language` o `version`: revalidar (422) y re-lintear. Si solo cambia nombre o descripción, no se llama a Language.
- Dejar un punto de extensión "después de cambiar el contenido" (`ContentChangedHook` o un evento interno de Spring), que en el paso 14 va a correr los tests.

**Tests:** solo metadata (no llama a Language), contenido válido, contenido inválido (no persiste), usuario READ (403).
**Listo cuando:** solo el owner puede editar, y un snippet nunca queda guardado como inválido.

- [x] Paso 9 (PR C) — 2026-11-01: ver docs/step-C-crud.md

## Paso 10 · Borrar snippet
- `DELETE /snippets/{id}` → `require(WRITE)` → borra el snippet (los tests se borran en cascada) → `storage.remove` → `deleteAll` en Permission → 204.
- Si Permission falla después de borrar: se loguea y se responde 204. Es idempotente del lado de Permission, así que se puede reintentar.

**Tests:** owner, READ (403), sin rol (404).

- [x] Paso 10 (PR C) — 2026-11-01: ver docs/step-C-crud.md

## Paso 11 · Compartir
**Objetivo:** US 7.

- `GET /users?search` → proxy a `searchUsers`.
- `POST /snippets/{id}/share {userId}` → `require(SHARE)` → `grant READ` → 204. Un 409 de Permission → 409 `ALREADY_SHARED`.

**Tests:** owner comparte, READ intenta compartir (403), ya compartido (409).
**Listo cuando:** el usuario con quien se compartió ve el snippet en `GET /snippets?relation=SHARED`.

- [x] Paso 11 (PR D) — 2026-11-01: sharing.SharingService/Controller; GET /users proxy, POST /snippets/{id}/share (SHARE->grant READ->204, 409 ALREADY_SHARED). Tests servicio+controller.

## Paso 12 · Descargar
**Objetivo:** US 13.

- `GET /snippets/{id}/download?formatted` → `require(READ)` → `text/plain` + `Content-Disposition: attachment; filename="<name>.<ext>"` (extensión según el lenguaje, por config).
- `formatted=true` → `LanguageClient.format` con las reglas FORMAT **del owner** (D15).

**Tests:** original, formateado (verifica que se usan las reglas del owner aunque descargue otro usuario), headers.

- [x] Paso 12 (PR E) — 2026-11-01: download.DownloadService/Controller/Properties; GET /snippets/{id}/download?formatted, reglas del owner (D15), extension por config. Tests servicio+controller.

## Paso 13 · Gestión de tests
**Objetivo:** US 8.

- Migración `snippet_test` (FK con `ON DELETE CASCADE`, `inputs`/`expected_outputs` jsonb, `last_status`, `last_run_at`).
- `POST/PUT/DELETE /snippets/{id}/tests[/{testId}]` → `require(WRITE)`. `GET /snippets/{id}/tests` → `require(READ)`.
- Se aceptan tests que no van a pasar (US 8). Un test nuevo arranca con `last_status = NOT_RUN`.

**Tests:** CRUD, permisos, test de otro snippet (404).

- [x] Paso 13 (PR F) — 2026-11-01: tests.* CRUD + migracion V4 snippet_test (FK cascade).

## Paso 14 · Correr tests
**Objetivo:** US 9 (manual) y US 16 (automático al modificar).

- `POST /snippets/{id}/tests/{testId}/run` → `require(READ)` → `LanguageClient.test(content, version, inputs, expectedOutputs)` → guarda `last_status` y `last_run_at` → responde el resultado (D12).
- Conectar al hook del paso 9: correr todos los tests del snippet uno por uno. Un error en uno no corta los demás, y el `PUT` responde igual (D13).

**Tests:** passed → PASSED, no passed → FAILED, falla Language en el run manual (502), falla en el automático (no afecta el PUT y sigue con el siguiente test).

- [x] Paso 14 (PR F) — 2026-11-01: run manual + ContentChangedTestRunner (auto-run D13).

## Paso 15 · Aplicar reglas a todos los snippets
**Objetivo:** US 12 y US 15, en versión sincrónica (D14).

- `RuleApplier.relint(user, lang)` y `RuleApplier.reformat(user, lang)`: snippets OWNER del usuario (ids de Permission + filtro por lenguaje) → uno por uno → `lint` (actualiza el estado; si falla, `FAILED`) o `format` (reescribe el contenido; si falla, lo deja sin cambios).
- `PUT /rules/{lang}/lint|format` (del paso 4) ahora, además de guardar, llama a `RuleApplier` y responde `{ ..., processed, failed }`.

**Tests:** N snippets con uno que falla (se procesan todos y `failed = 1`), solo afecta snippets del lenguaje y del owner.
**Listo cuando:** se cumple el criterio "PUT /rules/{lang}/lint actualiza el lint_status..." del spec.

- [x] Paso 15 (PR C) — 2026-11-01: ver docs/step-C-crud.md

## Paso 16 · Cierre
- README: cómo correr local (Postgres + URLs de los otros servicios), tabla de endpoints y variables de entorno.
- Recorrer los criterios de aceptación de §9 uno por uno, con un test que cubra cada uno.
- Coverage final ≥ 80 %. Revisar que `design-decisions.md` refleje lo implementado.

- [x] Paso 16 (PR G) — 2026-11-01: README completo, criterios §9 revisados (todos con test), build final 141 tests verdes 92.9%% cobertura. Integra C+D+E+F.
