# Paso 3 · Lenguajes disponibles

**Qué se buscaba:** que la UI pueda armar el selector de lenguaje y versión sin conocer a Language Service. Snippet Service expone `GET /languages` como proxy (D01); no agrega lógica propia.

## Lo central

### Endpoint (`LanguageController`)
- `GET /languages` → `LanguageClient.languages()` → `[{id, versions}]` tal cual lo devuelve Language Service.
- Es un proxy de un solo método, sin capa de servicio: no hay lógica que aislar. El contrato (`LanguageInfo { id, versions }`) ya lo define el cliente del paso 2.
- Requiere `X-User-Id` como cualquier ruta (interceptor del paso 1): sin header → 401.
- Si Language Service falla, `DownstreamCalls` dentro de `RestLanguageClient` ya convierte la falla en `DownstreamException` → 502 `DOWNSTREAM_ERROR` (D11). El controller no maneja errores.

## Tests

| Test | Qué cubre |
|---|---|
| `returnsTheLanguagesFromTheClient` | caso feliz: devuelve la lista del cliente con `id` y `versions` |
| `whenLanguageServiceFailsRespondsBadGateway` | el cliente lanza `DownstreamException` → 502 `DOWNSTREAM_ERROR` |
| `missingHeaderIsUnauthenticated` | sin `X-User-Id` → 401 `UNAUTHENTICATED` |

`@WebMvcTest(LanguageController.class)` con `LanguageClient` mockeado (`@MockitoBean`). El paquete queda con 100 % de líneas cubiertas.

## Cómo verificarlo
```bash
./gradlew build
```
