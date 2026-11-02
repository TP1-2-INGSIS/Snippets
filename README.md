# Snippet Service

Microservicio central de Snippet Searcher. Es la **única puerta de la UI**: guarda
snippets, tests y reglas, y orquesta a Language Service (validar, formatear, lintear,
testear) y Permission Service (quién puede qué). La UI no conoce a los otros dos
servicios (D01).

Spring Boot 4 · Java 21 · PostgreSQL · Flyway · Gradle.

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
| `SERVICES_CONNECT_TIMEOUT` | `2s` | Timeout de conexión a los otros servicios |
| `SERVICES_READ_TIMEOUT` | `10s` | Timeout de lectura (debe superar los 5 s de ejecución de Language) |

Las extensiones de archivo para la descarga se configuran en `application.yml`
(`download.extensions`, con `download.fallback`).

## Autenticación (temporal)

No hay auth real todavía: cada request lleva el header `X-User-Id: <userId>`. Si falta,
el servicio responde `401 UNAUTHENTICATED`. Más adelante lo reemplaza Auth0 (D02).

## Endpoints

Todos requieren `X-User-Id`. Errores con el formato común
`{ "error": "CODE", "message": "...", "details": [] }` (D03).

### Snippets
| Método | Path | Descripción |
|---|---|---|
| `POST` | `/snippets` | Crear desde editor (JSON) |
| `POST` | `/snippets/upload` | Crear desde archivo (multipart) |
| `GET` | `/snippets/{id}` | Ver un snippet (incluye `sharedWith` si sos OWNER) |
| `GET` | `/snippets` | Listar con `relation`, `name`, `language`, `lintStatus`, `sort`, `order`, `page`, `size` |
| `PUT` | `/snippets/{id}` | Actualizar (editor); revalida y re-lintea si cambia el contenido |
| `PUT` | `/snippets/{id}/upload` | Actualizar (archivo) |
| `DELETE` | `/snippets/{id}` | Borrar (snippet + tests + permisos) |
| `GET` | `/snippets/{id}/download?formatted` | Descargar original o formateado (reglas del owner, D15) |

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
| `PUT` | `/rules/{language}/lint` | Guardar reglas de lint y re-lintear todos los snippets del lenguaje |
| `GET` | `/rules/{language}/format` | Ver reglas de formato |
| `PUT` | `/rules/{language}/format` | Guardar y re-formatear todos los snippets del lenguaje |
| `GET` | `/languages` | Lenguajes y versiones disponibles (proxy) |

## Códigos de error

| HTTP | `error` | Cuándo |
|---|---|---|
| 400 | `BAD_REQUEST` | body / params inválidos |
| 400 | `UNSUPPORTED_LANGUAGE` | lenguaje o versión no soportados |
| 401 | `UNAUTHENTICATED` | falta `X-User-Id` |
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
