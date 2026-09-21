# SECOP process finder

Read-only Spring Boot service that finds **open SECOP II processes** matching the committed parametría (`src/main/resources/parametria/parametria-ofertas.json`, v2.0.1) and returns an estimated compliance percentage.

It queries the public [datos.gov.co](https://www.datos.gov.co/resource/p6dx-8zbt.json) SODA API (dataset `p6dx-8zbt`). It does **not** log into community.secop.gov.co, apply to tenders, or mutate SECOP.

## Requirements

- Java 21
- Maven Wrapper (`./mvnw`)
- **Tesseract OCR** (optional): only needed for **scanned / image-only** Estudio Previo PDFs. Native text extraction uses Apache PDFBox and works without Tesseract. Install the engine plus Spanish traineddata (English is used as a fallback language when present):
  - Debian/Ubuntu: `sudo apt-get install -y tesseract-ocr tesseract-ocr-spa tesseract-ocr-eng`
  - macOS: `brew install tesseract tesseract-lang`
  - Confirm: `tesseract --list-langs` includes `spa` (and ideally `eng`). Set `TESSDATA_PREFIX` if traineddata is not in a default path.

There are **no paid OCR APIs**. OCR is local (Tess4J wrapping `libtesseract`, or the `tesseract` CLI).

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

- `secop.datos-gov-base-url` — SODA resource URL (dataset `p6dx-8zbt`)
- `secop.archivos-gov-base-url` — SODA resource for SECOP II attachments (dataset `dmgg-8hin`)
- `secop.parametria-path` — parametría JSON on the classpath
- `secop.default-min-match` — default `minMatch`
- `secop.national-entities` — extra entity stems used in the OR territory filter
- `secop.estudios-previos-dir` — writable directory for downloaded Estudio Previo PDFs **and** extracted `.txt` sidecars. **Default:** `src/main/resources/estudios-previos` (the layout used with local `spring-boot:run`). A packaged JAR cannot write into the classpath; production must set an **absolute writable path**.

No API tokens or secrets are required for the public datasets. OCR never calls an external paid API.

## Download Estudio Previo

`POST /api/v1/processes/estudios-previos/download`

Request body: the JSON **array** returned by search (`ProcessMatchResponse`). A wrapper `{ "processes": [ ... ] }` is also accepted.

For each item the service:

1. Looks up `id_del_portafolio` (`CO1.BDOS.*`) on datos.gov.co `p6dx-8zbt` using `processNumber` (then the exact OpportunityDetail `url`).
2. Lists attachments on datos.gov.co `dmgg-8hin` for that portfolio.
3. Selects **Estudio Previo PDFs** (names containing `ESTUDIO PREVIO` / `Estudio Previo` / `ESTUDIOS PREVIOS`). Invitacion ZIPs are only used when no dedicated PDF is listed; inner Estudio Previo PDFs are extracted.
4. Downloads binaries from the public `RetrieveFile` URL (no SECOP login). OpportunityDetail HTML is behind reCAPTCHA and is **not** scraped.

Per-process statuses: `DOWNLOADED`, `SKIPPED` (same filename already on disk — **not overwritten**), `NOT_FOUND`, `BLOCKED` (reCAPTCHA/login page instead of a PDF), `ERROR`. One blocked item does not fail the rest of the batch. No fake PDFs are written.

Files are stored as `estudios-previos/<sanitized-processNumber>/`, e.g. `src/main/resources/estudios-previos/MC-2026-047/ESTUDIO PREVIO.pdf`. Downloaded binaries are gitignored; `.gitkeep` keeps the folder.

This endpoint is **read-only**. It does not apply to tenders, submit offers, or mutate SECOP.

Example (search → pipe into download):

```bash
curl -s 'http://localhost:8080/api/v1/processes/search?minMatch=80&departamento=Caquetá&limit=5' \
  | curl -s -X POST 'http://localhost:8080/api/v1/processes/estudios-previos/download' \
      -H 'Content-Type: application/json' \
      -d @-
```

Example response:

```json
[
  {
    "processNumber": "MC-055-DISAN-EJC-2026",
    "status": "DOWNLOADED",
    "files": [
      {
        "fileName": "ESTUDIO PREVIO MOLECULARES 2026.pdf",
        "path": "src/main/resources/estudios-previos/MC-055-DISAN-EJC-2026/ESTUDIO PREVIO MOLECULARES 2026.pdf",
        "sizeBytes": 6152753
      }
    ]
  }
]
```

## Extract Estudio Previo text

`POST /api/v1/processes/estudios-previos/extract`

Request body: the **same JSON as download** (search-result array or `{ "processes": [ ... ] }`). A shortcut list of process numbers (or folder paths under `estudios-previos`) is also accepted: `["MC-2026-047"]` or `{ "processNumbers": ["MC-2026-047"] }`.

For each process the service reads PDFs already stored as `estudios-previos/<sanitized-processNumber>/` (paso 2 download). It does **not** download from SECOP.

1. **Native text** with Apache PDFBox.
2. If the PDF is empty / near-empty (scanned), **OCR**: rasterize pages with PDFBox, then Tess4J or `tesseract` (`spa+eng` when English traineddata is present, otherwise `spa`).
3. Write a sidecar `.txt` next to the PDF, e.g. `ESTUDIO PREVIO.pdf` → `ESTUDIO PREVIO.txt`.

Per-process statuses: `EXTRACTED`, `MISSING_PDF` (no PDF on disk — run download first; **no invented content**), `ERROR` (unreadable PDF, or scanned PDF while Tesseract is not installed). One failure does not fail the rest of the batch.

Example (search → download → extract):

```bash
curl -s 'http://localhost:8080/api/v1/processes/search?minMatch=80&departamento=Caquetá&limit=5' \
  | tee /tmp/secop-matches.json \
  | curl -s -X POST 'http://localhost:8080/api/v1/processes/estudios-previos/download' \
      -H 'Content-Type: application/json' \
      -d @-

curl -s -X POST 'http://localhost:8080/api/v1/processes/estudios-previos/extract' \
  -H 'Content-Type: application/json' \
  --data-binary @/tmp/secop-matches.json
```

Extract from process numbers already on disk:

```bash
curl -s -X POST 'http://localhost:8080/api/v1/processes/estudios-previos/extract' \
  -H 'Content-Type: application/json' \
  -d '["MC-055-DISAN-EJC-2026"]'
```

Example response:

```json
[
  {
    "processNumber": "MC-055-DISAN-EJC-2026",
    "status": "EXTRACTED",
    "files": [
      {
        "pdf": "src/main/resources/estudios-previos/MC-055-DISAN-EJC-2026/ESTUDIO PREVIO MOLECULARES 2026.pdf",
        "textFile": "src/main/resources/estudios-previos/MC-055-DISAN-EJC-2026/ESTUDIO PREVIO MOLECULARES 2026.txt",
        "method": "text",
        "chars": 18420
      }
    ]
  }
]
```

`method` is `"text"` (embedded PDF text) or `"ocr"` (Tesseract). Downloaded PDFs and extracted `.txt` files are gitignored; `.gitkeep` keeps the folder.

## Tests

```bash
./mvnw -q test
```

Unit tests cover native-text PDFs, the OCR path with a **mocked** engine (CI does **not** need Tesseract), and `MISSING_PDF`. An optional integration test runs OCR against a real Tesseract install when `spa` traineddata is present (`@EnabledIf`).
