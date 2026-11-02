# Paso 1 · Identidad y errores transversales

**Qué se buscaba:** resolver una sola vez dos cosas que necesitan todos los endpoints futuros. La primera es saber **quién hace la request**: hoy con el header `X-User-Id`, mañana con el JWT de Auth0. La segunda es que **todo error salga con el mismo formato** y el status HTTP correcto. Así, cada feature se concentra en su lógica: pide el usuario como parámetro y lanza excepciones de dominio, sin preocuparse por headers ni por armar respuestas de error.

## Lo central

### Identidad (`common.identity`, D02)

```
request ──▶ CurrentUserInterceptor ──▶ CurrentUserArgumentResolver ──▶ controller(CurrentUser user)
             lee X-User-Id              lo inyecta como parámetro
             falta o vacío → 401
```

- **`CurrentUser(id)`**: el usuario de la request. Un controller lo recibe declarándolo como parámetro:
  ```java
  @GetMapping("/snippets/{id}")
  SnippetDetail get(CurrentUser user, @PathVariable UUID id) { ... }
  ```
- **`CurrentUserInterceptor`**: la **única** clase que conoce el header. Corre para todas las rutas, salvo `/error`. Si el header falta o viene en blanco, lanza `UnauthenticatedException` (401).
- **`IdentityWebConfig`**: registra el interceptor y el resolver.
- Migrar a Auth0 = cambiar el interceptor para que lea el `sub` del JWT. El resto del código no cambia.
- Efecto lateral aceptado: una ruta inexistente **sin** header responde 401, no 404, porque la identidad se chequea antes de resolver la ruta.

### Errores (`common.error`, D03)

Todas las respuestas de error tienen este formato:
```json
{ "error": "CODE", "message": "texto legible", "details": [] }
```

**Excepciones de dominio.** Las features lanzan estas excepciones y nunca arman respuestas de error a mano:

| Excepción | HTTP | `error` |
|---|---|---|
| `NotFoundException` | 404 | `NOT_FOUND` |
| `ForbiddenException` | 403 | `FORBIDDEN` |
| `UnauthenticatedException` | 401 | `UNAUTHENTICATED` |
| `UnsupportedLanguageException` | 400 | `UNSUPPORTED_LANGUAGE` |
| `InvalidSnippetException(errors)` | 422 | `INVALID_SNIPPET` (`details` = errores de Language) |
| `ConflictException(code, msg)` | 409 | el código recibido, p. ej. `ALREADY_SHARED` (solo acepta códigos 409) |
| `DownstreamException(msg, cause)` | 502 | `DOWNSTREAM_ERROR` |

Todas extienden `ApiException`, que lleva un `ErrorCode`. El enum `ErrorCode` define el status HTTP de cada código, así que el status y el código no pueden quedar desalineados.

**`GlobalExceptionHandler`**, el único lugar donde una excepción se convierte en respuesta HTTP:
1. `ApiException` → status y código de su `ErrorCode`, con sus `details`.
2. Excepciones de Spring MVC (body mal formado, param faltante, ruta inexistente, método no soportado, …) → **se mantiene el status de Spring, pero con nuestro body**. Todo 400 sale como `BAD_REQUEST`; el resto con el nombre del status (`NOT_FOUND`, `METHOD_NOT_ALLOWED`, …). Por eso extiende `ResponseEntityExceptionHandler`.
3. Validación del body (`@Valid`) → 400 `BAD_REQUEST` con `details: [{field, message}]`.
4. Cualquier otra excepción → 500 `INTERNAL_ERROR`, con un mensaje genérico (nunca el interno) y logueada.

Los 5xx se loguean; los 4xx no, porque son errores del cliente.

## Tests

| Test | Qué cubre |
|---|---|
| `CurrentUserTest` | header válido (con espacios), sin header, header en blanco, ruta desconocida sin header |
| `GlobalExceptionHandlerTest` | cada excepción de dominio → status, código, mensaje; `details` de 422 y de validación; 500 sin filtrar el mensaje; body mal formado, param faltante, 404 y 405 de Spring |
| `ApiErrorTest` | `details` nulos → lista vacía; `ConflictException` rechaza códigos que no son 409 |

Los tests usan controllers **de prueba** (`ErrorProbeController`, `IdentityProbeController`), que existen solo en `src/test` y lanzan cada caso. Así, este paso se verifica sin depender de features que todavía no existen.

## Cómo verificarlo
```bash
./gradlew build
```
Resultado del paso: 18 tests nuevos y 100 % de líneas cubiertas.
