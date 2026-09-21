# SECOP process finder

Read-only Spring Boot service that finds **open SECOP II processes** matching the committed parametría (`src/main/resources/parametria/parametria-ofertas.json`, v2.0.1) and returns an estimated compliance percentage.

It queries the public [datos.gov.co](https://www.datos.gov.co/resource/p6dx-8zbt.json) SODA API (dataset `p6dx-8zbt`). It does **not** log into community.secop.gov.co, apply to tenders, or mutate SECOP.

## Requirements

- Java 21
- Maven Wrapper (`./mvnw`)

## Run

```bash
./mvnw spring-boot:run
```

The app listens on `http://localhost:8080`.

Health check (unchanged):

```bash
curl -s http://localhost:8080/ping
```

## Search endpoint

`GET /api/v1/processes/search`

Optional query parameters:

| Param | Default | Description |
| --- | --- | --- |
| `minMatch` | `80` | Minimum score (0–100). Only processes at or above this value are returned. |
| `departamento` | _(omitted)_ | When set (e.g. `Caquetá`), **only that department** is searched. When omitted, Caquetá **or** national entities of interest. |
| `limit` | `20` | Max number of scored matches to return (1–200) |

Example:

```bash
curl -s 'http://localhost:8080/api/v1/processes/search?minMatch=80&departamento=Caquetá&limit=10'
```

Response: a JSON array of

```json
[
  {
    "processNumber": "129-CMC-2026",
    "matchPercent": 92,
    "entity": "Municipio de Curillo — Oficina de Contratación Curillo",
    "object": "Suministro de uniformes deportivos...",
    "modality": "Mínima cuantía",
    "budget": 31276000,
    "closingDate": "2026-08-06",
    "url": "https://community.secop.gov.co/Public/Tendering/OpportunityDetail/Index?noticeUID=..."
  }
]
```

## How matching works

The API first pulls **open** processes from datos.gov.co:

- `estado_de_apertura_del_proceso` = `Abierto`
- `estado_del_procedimiento` in parametría `estados_proceso` (`Publicado`, `Abierto`)
- `modalidad_de_contratacion` in parametría `modalidades` (Mínima cuantía or Contratación régimen especial (con ofertas))
- Territory:
  - **`departamento` present** (e.g. `Caquetá`): only `departamento_entidad` matching that name, including accent variants (`Caquetá`, `Caqueta`, `CAQUETA`). National-entity keywords are **not** OR-ed, so SENA Antioquia/Santander cannot appear. In-department national bodies (Policía Caquetá, ICBF Regional Caquetá) still appear because their department is Caquetá. Parametría municipalities (Curillo, Florencia) are used for scoring, not as a nationwide city OR — Florencia also exists in Cauca. The dataset has no DIVIPOLA column.
  - **`departamento` omitted**: profile department (Caquetá, all municipalities) **or** `entidades_interes` **or** national entities (`Policía`, `Ejército`, `ICBF`, `Armada`, `Fuerza Aeroespacial`/`Aérea`, `Ministerio de Defensa`, `INPEC`, `SENA`) anywhere. LIKE stems are ASCII-folded (`POLIC` matches `POLICIA` and `POLICÍA`).

Each row is then scored against the parametría profile (not a pliego parse). Suggested weights, aligned with `reglas_decision`:

| Factor | Weight | Source |
| --- | --- | --- |
| Object / keywords / UNSPSC | 35% | `palabras_clave_incluir`, accepted contractual objects, business profile, `codigos_unspsc` |
| Entity / territory | 25% | Caquetá / Curillo / Florencia / `entidades_interes` **or** national target |
| Modality CMC / ESAL | 15% | known modalities |
| Budget band | 15% | soft 30M–200M COP; far outside scores 0 (`presupuesto_en_rango` = discard) |
| Time-to-close | 10% | prefer at least `dias_minimos_antes_cierre` (2) days; closer dates score lower |

Missing budget or closing date is scored conservatively. Processes below `minMatch` are omitted.

## Configuration

`src/main/resources/application.properties`:

- `secop.datos-gov-base-url` — SODA resource URL
- `secop.parametria-path` — parametría JSON on the classpath
- `secop.default-min-match` — default `minMatch`
- `secop.national-entities` — extra entity stems used in the OR territory filter

No API tokens or secrets are required for the public dataset.

## Tests

```bash
./mvnw -q test
```
