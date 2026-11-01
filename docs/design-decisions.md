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

## D02 · Identidad por header `X-User-Id` (temporal)
**Estado:** Decidida (spec §5)
**Contexto:** En este paso no hay autenticación real. Auth0 llega más adelante.
**Decisión:** Toda request trae `X-User-Id`; si falta, se responde 401 `UNAUTHENTICATED`. La lectura del header vive en **un único componente**, que expone un `CurrentUser` (o un argumento resuelto en el controller). El resto del código nunca lee el header.
**Consecuencias:** Para pasar a JWT alcanza con cambiar ese componente.
**Implementación (paso 1):** `common.identity`:
- `CurrentUserInterceptor` lee el header y es la única clase que lo conoce. Corre para **todas** las rutas salvo `/error`, así que no hace falta acordarse de pedir el usuario en cada endpoint.
- `CurrentUserArgumentResolver` inyecta `CurrentUser` como parámetro del controller.
- El header se toma sin espacios alrededor. Si viene vacío o solo con espacios, cuenta como ausente (401).
- Efecto lateral aceptado: una ruta inexistente sin header responde 401, no 404, porque la identidad se chequea antes de resolver la ruta.

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

## D14 · Re-lint / re-format sincrónicos, aislados en `RuleApplier`
**Estado:** Decidida (spec §5 Reglas)
**Decisión:** `PUT /rules/{lang}/lint|format` aplica las reglas a todos los snippets OWNER del usuario en ese lenguaje, **dentro del request**. Si un snippet falla, queda `FAILED` (lint) o sin cambios (format) y se sigue. Toda esa lógica vive en `RuleApplier`.
**Consecuencias:** El paso de Redis Streams podrá mover `RuleApplier` a un consumer sin tocar controllers.

## D15 · La descarga formateada usa las reglas del owner
**Estado:** Decidida (spec §5)
**Decisión:** `GET /snippets/{id}/download?formatted=true` formatea con las reglas FORMAT del **owner** del snippet, no con las de quien lo descarga.

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
