# Paso 4 · Reglas de lint/format (grupo B)

**Qué se buscaba:** que cada usuario pueda ver y cambiar sus reglas de lint y format por lenguaje (US 11, 14). En este paso **no** se aplican todavía a los snippets: eso lo agrega el grupo C (el `PUT` solo guarda).

## Lo central

- Migración `V3__rule_config.sql`: PK compuesta `(user_id, language, kind)`, `config` jsonb opaco (D07).
- `RuleConfig` (entidad con `@IdClass RuleConfigId`), `RuleKind` (`LINT`/`FORMAT`), `RuleConfigRepository`.
- `RulesService.get` devuelve la config guardada (`isDefault: false`) o el default del servicio (`isDefault: true`), que vive en `resources/rules/{language}-{kind}.json`. `save` hace upsert. Un lenguaje sin archivo de defaults → 400 `UNSUPPORTED_LANGUAGE`, tanto en `get` como en `save` (y `save` no persiste).
- `GET|PUT /rules/{language}/lint` y `.../format`. `config` es opaco: se reenvía tal cual, no se interpreta.

## Nota de implementación
`config` se persiste como `JsonNode` (Jackson 3, `tools.jackson`) en columna jsonb. Hibernate 7 trae `Jackson3JsonFormatMapper`, así que el round-trip funciona sin mapper manual.

## Tests
- `RuleConfigRepositoryTest` (`@DataJpaTest`): round-trip del jsonb opaco, PK compuesta separa kinds.
- `RulesServiceTest` (Mockito): default vs guardada, unsupported en `get` y `save` (no persiste).
- `RulesControllerTest` (`@WebMvcTest`): `GET` default (`isDefault: true`), `PUT` guarda (`isDefault: false`), format usa kind FORMAT, unsupported → 400, sin header → 401.

## Cómo verificarlo
```bash
./gradlew build
```
