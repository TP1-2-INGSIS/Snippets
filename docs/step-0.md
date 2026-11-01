# Paso 0 · Base del proyecto

**Qué se buscaba:** dejar el esqueleto de Spring Boot listo para empezar a escribir funcionalidades. El proyecto tiene que compilar, conectarse a una base PostgreSQL con el esquema versionado, correr los tests contra un Postgres real y fallar si la cobertura baja del 80 %. Además, todo esto tiene que correr igual en CI. Este paso no agrega ningún endpoint; prepara la base sobre la que se construyen los demás.

## Lo central

### Identidad del proyecto (D17)
- Paquete `com.ingsis.snippets`, clase principal `SnippetServiceApplication`, `rootProject.name = 'snippet-service'`.

### Dependencias (`build.gradle`)
Spring Boot 4 separa la autoconfiguración en starters por tecnología:

| Para | Starter |
|---|---|
| API REST | `spring-boot-starter-webmvc` |
| Persistencia | `spring-boot-starter-data-jpa` + driver `postgresql` |
| Validación de requests | `spring-boot-starter-validation` |
| Migraciones | `spring-boot-starter-flyway` + `flyway-database-postgresql` |
| Tests | `-webmvc-test`, `-data-jpa-test`, `spring-boot-testcontainers`, `testcontainers-postgresql` |

> En Boot 4, agregar solo `flyway-core` **no** activa Flyway: hace falta el starter.

### Configuración (`src/main/resources/application.yml`)
Todo lo que cambia entre ambientes viene de variables de entorno, con defaults pensados para desarrollo local:

| Variable | Default |
|---|---|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/snippets` / `snippets` / `snippets` |
| `PORT` | `8080` |
| `LANGUAGE_SERVICE_URL` | `http://localhost:8082` |
| `PERMISSION_SERVICE_URL` | `http://localhost:8081` |

Hibernate está en `ddl-auto: validate`: el esquema lo crea **solo Flyway**, y Hibernate solo verifica que las entidades coincidan con él.

### Esquema versionado (D18)
- `db/migration/V1__init.sql`: migración inicial vacía. Las tablas se agregan en los pasos 4, 5 y 13, cada una en su propia migración `V{n}__*.sql`.

### Tests contra Postgres real (D19)
- `TestcontainersConfiguration` (en `src/test`) levanta `postgres:16-alpine` en Docker y, gracias a `@ServiceConnection`, Spring conecta el datasource sin configuración extra.
- Un test que necesite base de datos lo importa con `@Import(TestcontainersConfiguration.class)`.
- `SnippetServiceApplicationTests.contextLoads` comprueba que la app arranca y que Flyway aplica las migraciones.

### Cobertura mínima
- Plugin `jacoco`. `check` (y por lo tanto `build`) depende de `jacocoTestCoverageVerification` con un mínimo de **80 % de líneas**.
- Solo se excluye `SnippetServiceApplication`, porque su `main` no se puede cubrir con tests.
- Reporte: `build/reports/jacoco/test/html/index.html`.

### CI (`.github/workflows/ci.yml`)
- En cada PR y en cada push a `main`/`dev`: Java 21 + `./gradlew build`. Testcontainers usa el Docker del runner. Al final sube el reporte de JaCoCo como artefacto.

## Cómo verificarlo
```bash
./gradlew build      # requiere Docker corriendo
```
En los logs del test aparecen `Container postgres:16-alpine started` y `Successfully applied 1 migration`.
