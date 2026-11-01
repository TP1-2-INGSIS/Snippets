# Snippet Service · Branches y PRs

Resumen de `implementation-plan.md` reducido a **cómo se integra cada PR**. A partir
del paso 3 los pasos se agrupan por PR cuando comparten superficie de código; cada
grupo es una branch y un PR.

## Convención

- Branch por PR: `feature/<slug>`, salida desde `main` (o desde la branch de la que
  depende, si todavía no se mergeó — PRs stacked).
- Un PR por branch. Se mergea cuando `./gradlew build` está en verde (JaCoCo ≥ 80 %).
- El merge respeta el orden de `Depende de`: no se mergea un PR antes que sus dependencias.

## Mapa de branches

| PR | Capacidad | Pasos | Branch | Depende de | Estado |
|---|---|---|---|---|---|
| — | Base del proyecto | 0 | `feature/project-setup` | — | 🔎 PR |
| — | Identidad y errores | 1 | `feature/identity-and-errors` | 0 | 🔎 PR |
| — | Clientes Language/Permission | 2 | `feature/service-clients` | 1 | 🔎 PR |
| — | Lenguajes disponibles | 3 | `feature/languages-endpoint` | 2 | 🔎 PR |
| A | Persistencia + `SnippetStorage` | 5 | `feature/snippet-persistence` | 0 | 🔎 PR |
| B | Reglas lint/format | 4 | `feature/rules-config` | 1 | 🔎 PR |
| C | Snippet CRUD + re-lint/format | 6–10, 15 | `feature/snippet-crud` | 2, B, A | 🔎 PR |
| D | Compartir | 11 | `feature/share-snippet` | C | 🔎 PR |
| E | Descargar | 12 | `feature/download-snippet` | B, C | 🔎 PR |
| F | Tests de snippet | 13, 14 | `feature/snippet-tests` | C | 🔎 PR |
| G | Cierre (README, coverage) | 16 | `feature/release-closeout` | todos | 🔎 PR |

## Orden de merge sugerido

Encadenado por dependencias (los que comparten nivel pueden ir en paralelo):

```
0 → 1 → 2 → 3
0 → A
1 → B
2, B, A → C → D
         C → F
      B, C → E
   todos → G
```

## Nota sobre PRs stacked

Mientras nada esté en `main`, cada branch sale de la anterior y su PR apunta a esa
branch base (no a `main`). Al mergear un PR, revisar que GitHub re-apunte la base de
los PRs que dependían de él (o rebasear si se usó squash).
