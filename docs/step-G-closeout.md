# Grupo G · Cierre (paso 16)

**Qué se buscaba:** cerrar el servicio — README completo, revisar los criterios de aceptación §9 uno por uno y confirmar la cobertura final. Esta branch integra C+D+E+F, así que es también la verificación de que todas las capacidades conviven.

## Build final
`./gradlew build` en verde con las 4 migraciones y los 9 paquetes. **141 tests, 0 fallos, 92.9 % de líneas** (602/648), por encima del 80 % exigido.

## Criterios de aceptación §9 → dónde se cubren

| Criterio | Cubierto por |
|---|---|
| Crear inválido → 422 con regla/línea/columna, nada guardado | `SnippetServiceTest.createInvalidSnippetDoesNotSaveNorGrant`, `SnippetControllerTest` (422 details) |
| Crear válido → `lint_status` + creador OWNER | `SnippetServiceTest.createValidSnippetLintsSavesAndGrantsOwner` |
| `GET /snippets` filtra/ordena/pagina | `SnippetRepositoryTest` (filtros/orden/paginación), `SnippetServiceTest.list*`, `SnippetControllerTest.list*` |
| READ puede ver/correr/descargar, no editar/borrar/compartir | `AuthorizationService` (403 en WRITE/SHARE) + `SnippetServiceTest`/`SharingServiceTest` (forbidden), `DownloadServiceTest` (READ descarga) |
| Sin rol → 404 en snippet ajeno (D04) | `AuthorizationServiceTest`, `SnippetControllerTest.getUnknownIsNotFound` |
| `PUT /rules/{lang}/lint` actualiza lint_status de todos | `RuleApplierTest.relint*`, `RulesControllerTest.putLint*` |
| Correr test → `POST /languages/{lang}/test`, guarda PASSED/FAILED sin comparar | `SnippetTestServiceTest.runPassed/runNotPassed` (D12) |
| Editar contenido corre los tests, sin afectar la respuesta | `SnippetServiceTest.updateContentRevalidatesRelintsAndPublishesEvent` + `SnippetTestServiceTest.runAll*` (listener, D13) |
| Descarga formateada usa reglas del owner | `DownloadServiceTest.formattedDownloadUsesTheOwnersFormatRulesNotTheDownloaders` (D15) |

Todos los criterios tienen al menos un test que los ejercita.

## Entregable
`README.md`: cómo correr local (Postgres + URLs), tabla de endpoints por capacidad,
variables de entorno, códigos de error y un resumen de arquitectura.

## Cómo verificarlo
```bash
./gradlew build
```
