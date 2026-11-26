# Snippet Service

Microservicio central de Snippet Searcher. Es la **única puerta de la UI**: guarda
snippets, tests y reglas, y orquesta a Language Service (validar, formatear, lintear,
testear) y Permission Service (quién puede qué). La UI no conoce a los otros dos
servicios (D01).

Spring Boot 4 · Java 21 · PostgreSQL · Redis Streams · Flyway · Gradle.

## Cómo correr local

Hace falta una base PostgreSQL y las URLs de los otros dos servicios.

1. Levantar PostgreSQL (ejemplo con Docker):
   ```bash
   docker run --name snippet-db -e POSTGRES_DB=snippets \
     -e POSTGRES_USER=snippets -e POSTGRES_PASSWORD=snippets \
     -p 5432:5432 -d postgres:16-alpine
   ```
2. Correr el servicio:
   ```bash
   ./gradlew bootRun
   ```
   Flyway aplica las migraciones (`V1`–`V4`) al arrancar. El servicio queda en
   `http://localhost:8080`.

### Build y tests
```bash
./gradlew build
```
Compila, corre los tests (unitarios con Mockito, controllers con `@WebMvcTest`,
repositorios con Testcontainers + PostgreSQL real, clientes con WireMock) y verifica
cobertura ≥ 80 % (JaCoCo). Requiere Docker para los tests de repositorio.

## Variables de entorno

| Variable | Default | Para qué |
|---|---|---|
| `PORT` | `8080` | Puerto HTTP |
| `DB_URL` | `jdbc:postgresql://localhost:5432/snippets` | Datasource |
| `DB_USER` | `snippets` | Usuario de la base |
| `DB_PASSWORD` | `snippets` | Password de la base |
| `LANGUAGE_SERVICE_URL` | `http://localhost:8082` | Base URL de Language Service |
| `PERMISSION_SERVICE_URL` | `http://localhost:8081` | Base URL de Permission Service |
| `ASSETS_SERVICE_URL` | `http://localhost:8083` | Base URL de Assets Service (solo se usa con `SNIPPET_STORAGE=assets`) |
| `SNIPPET_STORAGE` | `database` | Dónde vive el contenido: `database` (columna `content`) o `assets` (Assets Service, D06) |
| `SNIPPET_MIGRATE_ON_STARTUP` | `true` | En modo `assets`, sube al arrancar el contenido que sigue en la columna (idempotente, en segundo plano) |
| `SNIPPET_MIGRATION_BATCH_SIZE` | `100` | Tamaño de página del migrador |
| `REDIS_URL` | `redis://localhost:6379` | Redis (transporte de las tareas asíncronas) |
| `REDIS_STREAM_TASKS` | `tasks:printscript` | Stream donde se publican las tareas de lint/format |
| `REDIS_STREAM_RESULTS` | `tasks:results` | Stream de resultados de los workers |
| `REDIS_RESULTS_GROUP` | `snippet-service` | Consumer group de los resultados |
| `TASK_TIMEOUT` | `5m` | Tiempo antes de que el reaper re-encole un snippet `PENDING` (máx. 3 veces, luego `FAILED`) |
| `TASK_REAPER_INTERVAL` | `60s` | Cada cuánto corre el reaper |
| `TASK_RETRY_BACKOFF` | `2s` | Espera (multiplicada por el nº de fallos) antes de reintentar un resultado que falló al aplicarse |
| `ENGINE_CHECK_INTERVAL` | `10m` | Cada cuánto se consulta `GET /languages` para detectar una `engineVersion` nueva (también al arrancar). Si cambió, o el lenguaje es nuevo, se re-encola el lint de todos sus snippets |
| `TASKS_CONSUMER_ENABLED` | `true` | Apaga el consumidor de resultados (útil en tests o réplicas sin consumidor) |
| `SERVICES_CONNECT_TIMEOUT` | `2s` | Timeout de conexión a los otros servicios |
| `SERVICES_READ_TIMEOUT` | `10s` | Timeout de lectura (debe superar los 5 s de ejecución de Language) |
| `RUN_IDLE_TIMEOUT` | `6m` | Inactividad máxima del relay WebSocket de ejecución |

Con `SNIPPET_STORAGE=assets` el contenido se guarda en `/v1/assets/{snippetId}` y la columna `content`
queda en `NULL` (Snippets conserva solo la metadata). Las filas viejas se migran al arrancar o, si
no, al leerlas (si Assets responde 404 y la columna tiene texto, se sirve y se sube en el momento).
Volver a `database` después de migrar requiere devolver el contenido a la columna: no es automático.

Las extensiones de archivo para la descarga se configuran en `application.yml`
(`download.extensions`, con `download.fallback`).

## Autenticación

Los endpoints requieren un access token de Auth0 (`Authorization: Bearer <jwt>`). Se valida la
firma (JWKS), el issuer, la audience y el vencimiento; el usuario es el `sub` del token. Sin
identidad o con un token inválido, el servicio responde `401 UNAUTHENTICATED` (D02).

| Variable | Default | Descripción |
|---|---|---|
| `AUTH0_ISSUER_URI` | (vacío) | Issuer del tenant, con `/` final: `https://tenant.auth0.com/` |
| `AUTH0_AUDIENCE` | (vacío) | API identifier de Auth0 (la audience del token) |
| `AUTH_HEADER_IDENTITY_ENABLED` | `false` | Si es `true`, una request sin Bearer puede identificarse con `X-User-Id: <userId>`. Solo tests, local y e2e; nunca en prod |
| `AUTH0_JWKS_URI` | derivado del issuer | Opcional, solo para tests |

Hay que configurar `AUTH0_ISSUER_URI` + `AUTH0_AUDIENCE`, `AUTH_HEADER_IDENTITY_ENABLED=true`, o
ambos; si no, la app no arranca. Con el header habilitado y sin `AUTH0_*`, arranca y acepta solo
`X-User-Id`. El decoder no consulta a Auth0 al arrancar.

## Endpoints

Todos requieren identidad (ver Autenticación). Errores con el formato común
`{ "error": "CODE", "message": "...", "details": [] }` (D03).

### Snippets
| Método | Path | Descripción |
|---|---|---|
| `POST` | `/snippets` | Crear desde editor (JSON) |
| `POST` | `/snippets/revalidate` | Re-encola el lint de todos tus snippets (OWNER) con tus reglas. Responde **202** `{queued}`. Útil para demos de la revalidación |
| `POST` | `/snippets/format` | Formatear contenido `{content, language, version}` con tus reglas FORMAT → `{content}`; 422 si es inválido (D21) |
| `POST` | `/snippets/upload` | Crear desde archivo (multipart) |
| `GET` | `/snippets/{id}` | Ver un snippet (incluye `sharedWith` si sos OWNER) |
| `GET` | `/snippets` | Listar con `relation`, `name`, `language`, `lintStatus`, `sort`, `order`, `page`, `size` |
| `PUT` | `/snippets/{id}` | Actualizar (editor); revalida y re-lintea si cambia el contenido |
| `PUT` | `/snippets/{id}/upload` | Actualizar (archivo) |
| `DELETE` | `/snippets/{id}` | Borrar (snippet + tests + permisos) |
| `GET` | `/snippets/{id}/download?formatted` | Descargar original o formateado (`formatted_content` guardado, o al vuelo con reglas del owner, D15/D21) |

### Ejecución interactiva (WebSocket)
| Protocolo | Path | Descripción |
|---|---|---|
| `WS` | `/snippets/{id}/run?access_token=<JWT>` | Ejecuta el snippet y hace de relay con Language (US10). En prod: `wss://<host>/api/snippets/{id}/run` |

- Autenticación: los browsers no pueden mandar headers en un WebSocket, así que el JWT va en
  `?access_token=` (solo en esta ruta; el resto de la API lo ignora). Con
  `AUTH_HEADER_IDENTITY_ENABLED=true` se acepta `?userId=<id>` (o `X-User-Id`). Sin identidad o con
  un token inválido el handshake responde 401. No se loguea el token.
- Autorización: permiso READ sobre el snippet. Si falla (o el snippet no existe) se cierra con
  **1008** sin abrir sesión con Language.
- Relay hacia `ws://<LANGUAGE_SERVICE_URL>/languages/{language}/run`: el primer mensaje a Language es
  `{type:"start", content, version, env:{}}`. Mensajes JSON sin modificar:
  UI → Language `{type:"input", value}` y `{type:"stop"}`;
  Language → UI `{type:"output", value}`, `{type:"input-request", prompt}`, `{type:"finished"}` y
  `{type:"error", message, line?, column?}`.
- Cierre: el código de Language se reenvía a la UI sin cambios (1000 en `finished`, `stop`, errores
  del programa y timeout de input; 1008 por violación de protocolo; 1011 por error interno, "busy"
  o el límite de 5 min). Snippets usa 1008 si falla la identidad o el permiso y 1011 si Language
  no responde, corta sin código o el relay falla (antes manda un `error`). Cerrar un lado cierra el otro.
- `RUN_IDLE_TIMEOUT` (`run.idle-timeout`, default `6m`): red de seguridad si no pasa ningún mensaje.

### Compartir
| Método | Path | Descripción |
|---|---|---|
| `GET` | `/users?search&limit` | Buscar usuarios (proxy a Permission) |
| `POST` | `/snippets/{id}/share` | Compartir con `{ userId }` como READ |

### Tests
| Método | Path | Descripción |
|---|---|---|
| `POST` | `/snippets/{id}/tests` | Crear un test |
| `GET` | `/snippets/{id}/tests` | Listar tests |
| `PUT` | `/snippets/{id}/tests/{testId}` | Actualizar un test |
| `DELETE` | `/snippets/{id}/tests/{testId}` | Borrar un test |
| `POST` | `/snippets/{id}/tests/{testId}/run` | Correr un test (delega el assert en Language) |

Al actualizar el contenido de un snippet, todos sus tests se corren automáticamente
(US 16, D13).

### Reglas y lenguajes
| Método | Path | Descripción |
|---|---|---|
| `GET` | `/rules/{language}/lint` | Ver reglas de lint (default si no hay guardadas) |
| `PUT` | `/rules/{language}/lint` | Guardar reglas de lint y encolar el re-lint de los snippets del lenguaje. Responde **202** `{language, kind, config, queued}` |
| `GET` | `/rules/{language}/format` | Ver reglas de formato |
| `PUT` | `/rules/{language}/format` | Guardar reglas de formato y encolar el re-formateo. Responde **202** `{language, kind, config, queued}` |
| `GET` | `/languages` | Lenguajes y versiones disponibles (proxy) |

El lint y el formato masivos son asíncronos (D14): los snippets quedan `PENDING`
(`lint_status` / `format_status`), los workers de Language procesan las tareas desde Redis
Streams y Snippets consume los resultados. Ver `docs/design-decisions.md`.
Los demás parámetros (`tasks.claim-idle`, `tasks.claim-interval`, `tasks.max-deliveries`,
`tasks.max-requeues`) están en `application.yml` y se pueden pisar con las variables de entorno
de Spring (por ejemplo `TASKS_MAX_DELIVERIES`).

### Revalidación por versión del motor

Al arrancar y cada `ENGINE_CHECK_INTERVAL`, `EngineVersionWatcher` compara la `engineVersion` de cada
lenguaje (`GET /languages`) con la última guardada en `language_engine_version`. Si difiere o no hay
fila (lenguaje visto por primera vez), encola un LINT por snippet con las reglas de su owner por el
mismo pipeline asíncrono de las reglas (pasan a `PENDING`; un código inválido termina en `FAILED`
con las violaciones de sintaxis) y guarda la versión nueva. Si Language no responde, reintenta en el
próximo tick.

## Códigos de error

| HTTP | `error` | Cuándo |
|---|---|---|
| 400 | `BAD_REQUEST` | body / params inválidos |
| 400 | `UNSUPPORTED_LANGUAGE` | lenguaje o versión no soportados |
| 401 | `UNAUTHENTICATED` | sin identidad o token inválido |
| 403 | `FORBIDDEN` | tiene rol pero no alcanza para la acción |
| 404 | `NOT_FOUND` | snippet / test inexistente, o sin ningún rol (D04) |
| 409 | `ALREADY_SHARED` | ya compartido |
| 422 | `INVALID_SNIPPET` | el código no pasa `validate` (`details` con los errores) |
| 502 | `DOWNSTREAM_ERROR` | Language o Permission caídos o con 5xx |

## Arquitectura

Un paquete por capacidad (`snippet`, `rules`, `sharing`, `download`, `tests`,
`languages`), más `common` (identidad y errores), `clients` (Language, Permission) y
`authorization`. El contenido de los snippets se accede solo vía `SnippetStorage` (D06)
para poder moverlo a un bucket más adelante. La autorización siempre se consulta a
Permission Service (D05). Las decisiones de diseño están en `docs/design-decisions.md`
y el plan de implementación en `docs/implementation-plan.md`.
