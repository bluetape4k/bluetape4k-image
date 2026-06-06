# Issue 173 Ktor OCR Example Plan

## Scope

Implement issue #173 as a new non-published example module:

- `examples/ktor-ocr-api`
- root README locale updates
- example README locale set
- generated README diagrams
- Gradle/settings/AGENTS registration
- Examples workflow matrix coverage
- review, verification, lessons, PR artifacts

## Task Plan

| Task | Description | DoD |
|---|---|---|
| T1 | Add Gradle module registration and build file. | `settings.gradle.kts` includes `ktor-ocr-api`; `./gradlew projects` lists it. |
| T2 | Implement Ktor OCR app. | `GET /ready`; `POST /api/ocr`; injectable `OcrEngine`; env config for tessdata path; multipart validation; 400/503 mapping. |
| T3 | Add Ktor route tests. | `testApplication` tests pass without native Tesseract; fake engine verifies languages and tessdata path; invalid multipart cases return 400. |
| T4 | Add README locale set. | `README.md` and `README.ko.md` explain purpose, diagrams, native install, run, curl, test, and local-only limits. |
| T5 | Update root docs and repo guidance. | Root README/README.ko examples mention Ktor OCR; repo `AGENTS.md` module list includes new example. |
| T6 | Add diagrams. | Generator creates scenario, top-down layered architecture, and sequence assets with PNG/SVG/DOT/plain/Graphviz evidence. |
| T7 | Update Examples workflow. | Matrix includes `:ktor-ocr-api:test`; path triggers already include `examples/**` and `images-ocr/**`; `actionlint` passes. |
| T8 | Run verification and review. | Target tests, projects, diagram generation/XML/visual checks, workflow checks, `git diff --check`, Step 6-R P0=0/P1=0. |
| T9 | Capture lessons, commit, PR. | Lessons committed before PR; PR body ends with `## DoD Status`; post-PR review and CI gate update PR body. |

## Implementation Details

### Module

- Directory: `examples/ktor-ocr-api`
- Package: `io.bluetape4k.images.examples.ktor.ocr`
- Main class: `KtorOcrApiApplicationKt`
- Dependencies:
  - `project(":bluetape4k-images")`
  - `project(":bluetape4k-images-ocr")`
  - `libs.bluetape4k.ktor.core`
  - `libs.ktor.server.core`
  - `libs.ktor.server.netty`
  - `runtimeOnly(libs.logback)`
  - tests use `bluetape4k-junit5`, `bluetape4k-ktor-testing`, `ktor-server-test-host`

### Route

- `fun Application.configureKtorOcrApi(...)`
- Defaults:
  - engine: `TesseractOcrEngine()`
  - tessdata path: `System.getenv("EXAMPLE_OCR_TESSDATA_PATH")`
  - max input bytes: 10 MiB
- `POST /api/ocr`:
  - reads multipart field `file`
  - supports `PartData.FileItem` and `PartData.BinaryChannelItem`
  - releases each part in `finally`
  - validates non-empty upload and content type allowlist
  - parses `languages` with `[,+\s]+`
  - calls `immutableImageOf(uploadBytes).suspendExtractText(OcrOptions(...), engine)`
  - responds with `OcrTextResponse`
- Errors:
  - `IllegalArgumentException` and `IOException` -> `400 bad_request`
  - `OcrException` -> `503 ocr_unavailable`

### Tests

- `ready endpoint responds with plain text`
- `recognizes uploaded image with parsed languages`
- `rejects request without expected file field`
- `rejects unsupported content type`
- `maps OCR failures to service unavailable`

Use a class-level fake `OcrEngine` injected through `configureKtorOcrApi(...)`.

### Diagrams

Add three assets through `docs/scripts/generate-example-readme-diagrams.py`:

- `examples-ktor-ocr-api-scenario-01`
- `examples-ktor-ocr-api-architecture-01`
- `examples-ktor-ocr-api-sequence-01`

Architecture must be top-down layered:

1. Client Layer
2. Ktor Routing Layer
3. Application Layer
4. OCR Library Layer
5. Native Runtime Layer

## Validation Commands

Run in order:

```bash
./gradlew :ktor-ocr-api:test --no-configuration-cache --no-daemon
./gradlew projects --no-configuration-cache --no-daemon
python3 docs/scripts/generate-example-readme-diagrams.py
find docs/images/readme-diagrams -name 'examples-ktor-ocr-api-*.svg' -print0 | xargs -0 -n1 xmllint --noout
actionlint .github/workflows/Examples.yml
rg -n "\\\\'" .github/workflows
git diff --check
```

Visual inspection:

- Open each new PNG and check text fit, layer containment, connector routing,
  sequence label placement, PNG embed policy, and top-down architecture shape.

## Follow-Up Issue Policy

Create a follow-up issue only if implementation proves a reusable `images-ktor`
OCR route helper, production auth/rate-limit policy, batch OCR, persistence, or
native Ktor OCR smoke gate is necessary. Do not create speculative follow-ups for
rejected options already outside #173.
