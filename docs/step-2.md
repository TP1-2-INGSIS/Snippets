# Paso 2 · Clientes de Language y Permission

**Qué se buscaba:** que Snippet Service pueda hablar con Language Service y Permission Service mediante contratos tipados, sin que el resto del código sepa de URLs, HTTP o códigos de error ajenos. Cada falla de otro servicio tiene que convertirse en una excepción de dominio o en un 502. También se resuelve acá la autorización de una operación (404 vs 403), que van a usar casi todos los pasos siguientes. Este paso no agrega endpoints.

## Lo central

### Clientes (D11)
Las features dependen de las interfaces; las implementaciones usan `RestClient`.

| Interfaz | Métodos | Implementación |
|---|---|---|
| `LanguageClient` | `languages`, `validate`, `format`, `lint`, `test` | `RestLanguageClient` |
| `PermissionClient` | `grant`, `idsFor`, `check`, `usersOf`, `deleteAll`, `searchUsers` | `RestPermissionClient` |

- `rules` es un `JsonNode` opaco: este servicio no lo interpreta (D07). Si es `null`, no se envía.
- `execute` no está en el cliente porque nada lo usa todavía.
- Los parámetros van siempre por variables de template, así que un `userId` como `auth0|abc` o un lenguaje con `/` se codifican bien.

### Traducción de errores (`DownstreamCalls`)

| Qué pasa en el otro servicio | Resultado |
|---|---|
| `LANGUAGE_NOT_FOUND`, `UNSUPPORTED_VERSION` | `UnsupportedLanguageException` (400) |
| `INVALID_RULES` | `BadRequestException` (400) |
| `INVALID_SOURCE` | `InvalidSnippetException` (422, conserva los `details`) |
| `ALREADY_EXISTS` | `ConflictException(ALREADY_SHARED)` (409) |
| 5xx, otro 4xx, timeout, sin conexión, body ilegible o vacío | `DownstreamException` (502) |

`OWNER_ALREADY_EXISTS` queda como 502: no puede pasar con un id recién generado, así que si aparece es una falla.

### Configuración (`ClientsConfiguration`, `ServicesProperties`)
- `services.language.url`, `services.permission.url`.
- `SERVICES_CONNECT_TIMEOUT` (2 s) y `SERVICES_READ_TIMEOUT` (10 s). El de lectura tiene que superar los 5 s de límite de ejecución de Language.

### Autorización (`AuthorizationService`, D04 y D05)
```java
PermissionRole role = authorization.require(user, snippetId, PermissionAction.WRITE);
```
| `check` responde | `require` |
|---|---|
| `allowed: true` | devuelve el rol |
| `allowed: false`, sin rol | `NotFoundException` (404): un snippet ajeno parece inexistente |
| `allowed: false`, con rol | `ForbiddenException` (403) |

## Tests

| Test | Qué cubre |
|---|---|
| `RestLanguageClientTest` | cada operación contra WireMock (body enviado y respuesta), `rules` omitidas, encoding del path, cada error traducido, 5xx, body inválido o vacío, timeout, servicio inaccesible |
| `RestPermissionClientTest` | cada operación, query con y sin `role`, encoding de `userId`, conflictos, 5xx |
| `AuthorizationServiceTest` | los tres casos de la tabla |

Los clientes se construyen con su configuración real (`WireMockClients`), así que los tests también ejercitan los timeouts.

> **Ojo con los timeouts en tests:** un timeout corto compartido por todos los tests es inestable, porque la primera request de la JVM puede tardar más de un segundo. Solo el test de timeout usa uno corto.

## Cómo verificarlo
```bash
./gradlew build
```
