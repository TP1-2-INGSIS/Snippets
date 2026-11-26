# Snippet Service · Decisiones de diseño

Fuente: `../SnippetsSearcher-design/specs/snippet-service.md` (spec del paso 1: microservicios + HTTP naive).
Este archivo junta las decisiones importantes. Cada una tiene contexto, decisión, consecuencias y estado.

Estados: **Decidida** (viene del spec o ya está acordada) · **Propuesta** (hay que confirmarla antes del paso que la usa) · **Reemplazada** (se deja como historial).

---

## D01 · Snippet Service es la única puerta de la UI
**Estado:** Decidida (spec §1)
**Contexto:** La UI necesita snippets, permisos y operaciones del lenguaje.
**Decisión:** La UI solo le habla a este servicio. Las llamadas a Language Service y Permission Service salen siempre desde acá. Para que la UI no tenga que conocerlos, este servicio hace de proxy de `GET /users` y `GET /languages`.
**Consecuencias:** Este servicio orquesta y también maneja las fallas de los otros dos (ver D03 y D11).

## D02 · Identidad: JWT de Auth0 (con `X-User-Id` solo para tests/local)
**Estado:** Decidida (reemplaza la versión temporal con header)
**Contexto:** La UI (Auth0 SPA) manda un access token pedido con la audience de la API. nginx solo expone Snippets, así que es el único servicio que valida JWT; Permissions, Language y Assets son internos a la red del stack.
**Decisión:** Spring Security como *resource server*. Se valida la firma (JWKS), `iss`, `aud` y `exp`; el `CurrentUser` es el `sub` del token validado. El resto del código nunca lee headers ni tokens: recibe un `CurrentUser` como argumento del controller.
- `X-User-Id` solo se acepta con `AUTH_HEADER_IDENTITY_ENABLED=true` (tests, local, e2e) y solo si la request no trae `Authorization`. Nunca en prod.
- Si no hay config de Auth0 ni header habilitado, la app **no arranca** (mensaje claro). Con header habilitado y sin `AUTH0_*`, arranca y acepta solo `X-User-Id`.
- El `JwtDecoder` se arma con `withJwkSetUri(<issuer>.well-known/jwks.json)`: no hace llamadas de red al arrancar (CI y e2e arrancan sin Auth0); las claves se piden con el primer token.
- Toda ruta requiere identidad, salvo `/error` y `/actuator/health`. Sin identidad o con token inválido: 401 con el formato D03 (`UNAUTHENTICATED`). No hay CORS (mismo origen).
**Implementación:** `common.identity`:
- `SecurityConfig`: cadena de filtros, `JwtDecoder` (issuer + audience + timestamps), filtro del header y *entry point* 401. Valida la configuración al construirse.
- `CurrentUserArgumentResolver` inyecta `CurrentUser` desde el `SecurityContext`.
- Efecto lateral aceptado: una ruta inexistente sin identidad responde 401, no 404.
- Env: `AUTH0_ISSUER_URI` (con `/` final, debe coincidir con el `iss`), `AUTH0_AUDIENCE`, `AUTH_HEADER_IDENTITY_ENABLED` (default `false`) y `AUTH0_JWKS_URI` (opcional; solo para tests, por defecto se deriva del issuer).

## D03 · Formato de error común
**Estado:** Decidida (spec §5)
**Decisión:** Todas las respuestas de error usan `{ "error": "CODE", "message": "...", "details": [] }`. Las arma un `@RestControllerAdvice` a partir de excepciones de dominio:

| Excepción | HTTP | `error` |
|---|---|---|
| validación de request, o reglas rechazadas por Language (`BadRequestException`) | 400 | `BAD_REQUEST` |
| lenguaje o versión no soportados | 400 | `UNSUPPORTED_LANGUAGE` |
| sin `X-User-Id` | 401 | `UNAUTHENTICATED` |
| tiene rol, pero no alcanza para la acción | 403 | `FORBIDDEN` |
| no existe, o no tiene ningún rol | 404 | `NOT_FOUND` |
| ya compartido | 409 | `ALREADY_SHARED` |
| el código no pasa `validate` | 422 | `INVALID_SNIPPET` (`details` = `{rule, message, line, column}[]`) |
| Language/Permission caído o responde 5xx | 502 | `DOWNSTREAM_ERROR` |
| cualquier otra excepción no prevista | 500 | `INTERNAL_ERROR` (sin exponer el mensaje interno) |

**Implementación (paso 1):** `common.error`:
- `ApiException` (base) con `ErrorCode`, del que salen el status y el código. Subclases: `NotFound`, `Forbidden`, `Unauthenticated`, `UnsupportedLanguage`, `InvalidSnippet(errors)`, `Conflict(code)` (solo acepta códigos 409), `Downstream(cause)`.
- `GlobalExceptionHandler` extiende `ResponseEntityExceptionHandler`. Así, las excepciones propias de Spring MVC (body mal formado, param faltante, ruta inexistente, método no soportado…) **mantienen el status de Spring pero usan nuestro body**: todo 400 sale como `BAD_REQUEST` y el resto con el nombre del status (`NOT_FOUND`, `METHOD_NOT_ALLOWED`, …).
- Los errores de Bean Validation en el body ponen en `details` una lista `[{field, message}]`.
- Los errores 5xx se loguean; los 4xx no.

## D04 · 404 en vez de 403 si el usuario no tiene ningún rol
**Estado:** Decidida (spec §5)
**Decisión:** `allowed: false` sin rol → 404. `allowed: false` con rol `READ`, en una acción que pide `WRITE` o `SHARE` → 403.
**Consecuencias:** Un usuario no puede averiguar si existe un snippet ajeno.

## D05 · Autorización siempre consultada a Permission Service
**Estado:** Decidida (spec §4, §6.2)
**Decisión:** La fuente de verdad del permiso es Permission Service (`GET /permissions/check`). `owner_id` se guarda desnormalizado en `snippet` solo para mostrarlo y ordenar. **No se usa para autorizar**.
**Consecuencias:** Cada operación sobre un snippet hace al menos una llamada a Permission Service. Por ahora se acepta ese costo, sin cache.

## D06 · Contenido detrás de `SnippetStorage`
**Estado:** Decidida (spec §4.1)
**Decisión:** El contenido se lee y escribe solo a través de la interfaz `SnippetStorage` (`save`, `get`, `remove`). La implementación de este paso guarda en la columna `content` de Postgres.
**Consecuencias:** Más adelante se podrá pasar al asset service (bucket) sin tocar la lógica.

## D07 · Reglas por (usuario, lenguaje, tipo), opacas, con defaults
**Estado:** Decidida (spec §4, acordada en el diseño)
**Decisión:**
- `rule_config` se identifica por `(user_id, language, kind ∈ {LINT, FORMAT})` y su `config` es JSON **opaco**: este servicio no lo interpreta, solo lo reenvía a Language Service.
- Si el usuario no tiene reglas guardadas, se usa el default de `resources/rules/{language}-{kind}.json`. El default **no se persiste** hasta el primer `PUT`, y `GET` responde `isDefault: true`.

**Consecuencias:** Agregar un lenguaje = agregar sus dos archivos de defaults.

## D08 · `lint_status` persistido, no calculado al vuelo
**Estado:** Decidida (spec §4)
**Decisión:** El resultado del linteo (`lint_status` + `lint_violations`) se guarda al crear, al actualizar y al cambiar reglas.
**Consecuencias:** El listado (US 5) puede filtrar y ordenar por validez en la base sin llamar a Language Service.

## D09 · Listado: ids desde Permission + filtros en la base
**Estado:** Decidida (spec §7)
**Decisión:** `GET /snippets` pide a Permission los `snippetIds` del usuario según `relation`, y después hace una sola consulta a la base con `id IN (…)` + filtros + orden + paginación.
**Consecuencias:** Simple, pero el `IN` crece con la cantidad de snippets del usuario. Es aceptable en este paso; se revisa si hace falta.

## D10 · Crear con compensación
**Estado:** Decidida (spec §7)
**Decisión:** El orden es validar → lintear → guardar → `POST /permissions` (OWNER). Si el último paso falla, se borra el snippet guardado y se responde 502.
**Consecuencias:** No queda ningún snippet sin dueño. No hay transacción distribuida: la compensación es manual.

## D11 · Clientes HTTP detrás de interfaces
**Estado:** Decidida (spec §6, §8)
**Decisión:** `LanguageClient` y `PermissionClient` son interfaces. La implementación usa `RestClient` con base URL tomada de `LANGUAGE_SERVICE_URL` / `PERMISSION_SERVICE_URL`. Los errores de red y los 5xx se traducen a `DownstreamException` (502). Los 4xx conocidos se traducen a excepciones de dominio (ej. `UNSUPPORTED_VERSION` → `UNSUPPORTED_LANGUAGE`).
**Consecuencias:** La lógica se testea con mocks de las interfaces, y los clientes con WireMock.
**Implementación (paso 2):**
- `DownstreamCalls` centraliza la traducción. Solo un 4xx cuyo código conoce el cliente se vuelve excepción de dominio. Un 5xx, un 4xx desconocido, un timeout, un error de conexión o un body ilegible o vacío terminan en 502.
- Language: `LANGUAGE_NOT_FOUND`/`UNSUPPORTED_VERSION` → `UnsupportedLanguageException`; `INVALID_RULES` → `BadRequestException`; `INVALID_SOURCE` → `InvalidSnippetException` (conserva los `details`).
- Permission: `ALREADY_EXISTS` → `ConflictException(ALREADY_SHARED)`. `OWNER_ALREADY_EXISTS` queda como 502, porque no puede pasar con un id recién generado.
- Timeouts por `SERVICES_CONNECT_TIMEOUT` (2 s) y `SERVICES_READ_TIMEOUT` (10 s). El de lectura tiene que ser mayor a los 5 s de límite de ejecución de Language.
- `execute` no está en `LanguageClient`: nada lo usa en este paso (US 10 llega más adelante).

## D12 · Tests: el assert lo hace Language Service
**Estado:** Decidida (spec §5 Tests)
**Decisión:** Correr un test = `POST /languages/{lang}/test` con contenido, `inputs` y `expectedOutputs`. Este servicio **no compara outputs**: solo traduce `passed` a `PASSED`/`FAILED` y lo guarda en `last_status`.

## D13 · US 16 sincrónica y sin afectar el update
**Estado:** Decidida (spec §5 Tests)
**Decisión:** Después de un `PUT` que cambia el contenido, se corren todos los tests del snippet uno por uno. Si un test falla, o falla la llamada, se registra el resultado y se sigue con el siguiente. La respuesta del `PUT` no depende de los tests.

## D14 · Re-lint / re-format asíncronos con Redis Streams
**Estado:** Decidida (spec §5 Reglas; reemplaza la versión sincrónica, SNIP-3)
**Decisión:** `PUT /rules/{lang}/lint|format` guarda la config y, **en la misma transacción**, marca cada snippet OWNER del usuario en ese lenguaje como pendiente (`lint_status = PENDING`, o `format_status = PENDING`, con la fecha del pedido). **Después del commit** publica una tarea por snippet (`TaskEvent`, campo `data` con JSON, con `contentHash` = SHA-256 del contenido) en el stream `tasks:printscript` y responde **202** `{language, kind, config, queued}`. Los workers de Language procesan y publican un `TaskResultEvent` en `tasks:results`.
**Resultados:** un consumidor (`ResultsConsumer`, grupo `snippet-service`, consumer = hostname) procesa primero sus pendientes y luego las entradas nuevas, y cada `tasks.claim-interval` (30 s) hace XAUTOCLAIM de lo que quedó colgado más de `tasks.claim-idle` (60 s). Descarta (con ACK) los resultados de snippets borrados, con `contentHash` distinto del actual (el snippet cambió en el medio) o con un `taskId` que no es el de la última tarea encolada de ese tipo (`lint_task_id` / `format_task_id`: otro cambio de reglas o un re-encolado la reemplazó). LINT: `OK` → `COMPLIANT`/`NON_COMPLIANT` según `errors`; `INVALID_SOURCE` o `FAILED` → `FAILED`. FORMAT: `OK` → `formatted_content` + `format_status = DONE`; si no, `FAILED`. El ACK va **después** del commit y aplicar dos veces es inocuo. Si aplicar lanza una excepción no hay ACK; los intentos se cuentan localmente (Redis cuenta cada relectura de las pendientes propias, así que solo siembra el primer fallo) con una espera `TASK_RETRY_BACKOFF` entre reintentos, y a la `tasks.max-deliveries`-ésima (5) se loguea y se descarta. Estos números (`claim-*`, `max-deliveries`, `max-requeues` = 3) están en `application.yml`; son configurables solo `TASK_TIMEOUT`, `TASK_REAPER_INTERVAL`, `TASK_RETRY_BACKOFF` y `TASKS_CONSUMER_ENABLED` tienen variable propia.
**Tolerancia a fallos:** el estado vive en la base, Redis es solo transporte. Un reaper (`@Scheduled`) re-encola los snippets `PENDING` (o sin fecha de pedido) hace más de `TASK_TIMEOUT` (5 min por defecto; la consulta usa `SKIP LOCKED` para que varias instancias no re-encolen lo mismo), hasta 3 veces; después quedan `FAILED`. Cubre que Redis pierda una tarea o que falle la publicación (que no rompe el request). Editar el contenido de un snippet cancela un formateo pendiente.
**Consecuencias:** `RuleApplier` es solo el productor (y el re-encolado). Create/update siguen validando y lintando de forma sincrónica (US 1–4 necesitan feedback inmediato).

## D15 · La descarga formateada usa las reglas del owner
**Estado:** Decidida (spec §5)
**Decisión:** `GET /snippets/{id}/download?formatted=true` formatea con las reglas FORMAT del **owner** del snippet, no con las de quien lo descarga.
**Actualización (SNIP-2):** si el snippet tiene `formatted_content` guardado (D21), la descarga formateada devuelve eso; si es `NULL`, formatea al vuelo con las reglas del owner como antes. La descarga original siempre devuelve `content`.

---

## Decisiones de implementación (no vienen del spec)

## D16 · Organización del código por feature
**Estado:** Decidida (paso 0)
**Decisión:** Un paquete por capacidad (`snippet`, `rules`, `sharing`, `tests`, `languages`), más `common` (errores, identidad) y `clients` (Language, Permission). Dentro de cada feature: `controller`, `service`, `repository`, `dto`, `model`.
**Alternativa:** organizar por capa técnica (`controllers/`, `services/`, …).
**Por qué:** El plan avanza de a una capacidad por paso, y cada paso queda en su propio paquete.

## D17 · Nombre de paquete y de aplicación
**Estado:** Decidida (aplicada en paso 0: `com.ingsis.snippets`, `SnippetServiceApplication`, `rootProject.name = 'snippet-service'`)
**Contexto:** El esqueleto usa `com.ingsis.CNC` y `spring.application.name=CNC`.
**Decisión:** Renombrar a `com.ingsis.snippets` y `snippet-service`. Las mayúsculas en paquetes no siguen la convención de Java, y el nombre debería identificar al servicio.

## D18 · Migraciones con Flyway
**Estado:** Decidida (paso 0)
**Decisión:** El esquema se versiona con Flyway (`db/migration/V{n}__*.sql`), con `ddl-auto=validate`.
**Alternativa:** Liquibase, o `ddl-auto=update` (descartado: no es reproducible).
**Nota:** En Spring Boot 4 la autoconfiguración de Flyway vive en `spring-boot-starter-flyway` y no se activa solo con agregar `flyway-core`.

## D19 · Estrategia de tests
**Estado:** Decidida (paso 0)
**Decisión:**
- Unitarios con JUnit 5 + Mockito para servicios (clientes mockeados).
- `@WebMvcTest` para controllers (contrato HTTP y errores).
- `@DataJpaTest` + **Testcontainers (PostgreSQL)** para repositorios, así se prueba contra el mismo motor que en producción (JSON y orden).
- WireMock para los clientes HTTP.
- JaCoCo con verificación de **≥ 80 %** de líneas, que hace fallar el build si no se alcanza.

**Alternativa:** H2 en lugar de Testcontainers: más rápido, pero difiere en tipos JSON.
**Notas de implementación:**
- `TestcontainersConfiguration` (test) levanta `postgres:16-alpine` con `@ServiceConnection`. Los tests que necesitan base de datos la importan.
- JaCoCo excluye solo `SnippetServiceApplication`, el entry point de Spring Boot. `check` depende de `jacocoTestCoverageVerification`.
- Los tests corren igual en local y en CI (GitHub Actions usa el Docker del runner). WireMock se agrega en el paso 2, cuando haya clientes HTTP.

## D20 · Representación de listas JSON en la base
**Estado:** Decidida (se aplica desde el paso 4)
**Decisión:** `inputs`, `expected_outputs`, `lint_violations` y `rule_config.config` como columnas `jsonb`, mapeadas con `@JdbcTypeCode(SqlTypes.JSON)` (Hibernate 6+).

## D21 · `formatted_content` separado del original
**Estado:** Decidida (SNIP-2)
**Decisión:** el formateo automático (`PUT /rules/{lang}/format`, D14) escribe en la columna nullable `snippet.formatted_content` y **no** toca `content`, así la descarga "original" (US 13) devuelve exactamente lo que subió el usuario. Cuando `content` (o lenguaje/versión) cambia en `PUT /snippets/{id}`, la columna se limpia a `NULL`. `POST /snippets/format` formatea contenido arbitrario con las reglas FORMAT del llamador, valida primero (422 con el formato D03 + violaciones si es inválido) y no persiste nada.
**Consecuencias:** `GET /snippets/{id}` sigue devolviendo `content` original. Si las reglas de formato cambian, la columna se recalcula en el siguiente re-format.
