# Step 5 Verification — Issue 173 Ktor OCR Example

Scope:

- Issue: #173 `feat: add Ktor OCR example`
- Module: `examples/ktor-ocr-api`
- Branch: `feat/issue-173-ktor-ocr-example`

## Implementation DoD

| DoD | Status | Evidence |
|---|---|---|
| New Ktor OCR example module exists. | PASS | `examples/ktor-ocr-api` added and registered in `settings.gradle.kts`. |
| Minimal OCR route exists. | PASS | `GET /ready` and `POST /api/ocr` implemented in `KtorOcrApiApplication.kt`. |
| Existing `images-ocr` API is reused. | PASS | Route calls `ImmutableImage.suspendExtractText` with injected `OcrEngine`; no new backend was added. |
| Native runtime path is host config, not request config. | PASS | `EXAMPLE_OCR_TESSDATA_PATH` maps to `OcrOptions.tessdataPath`; request-level tessdata path is not accepted. |
| Ktor route tests avoid host Tesseract. | PASS | Tests inject a fake `OcrEngine`. |
| Multipart validation covers field, type, and OCR failure mapping. | PASS | Tests cover expected field, unsupported content type, and `OcrException` -> 503. |
| README locale set documents setup and run commands. | PASS | `examples/ktor-ocr-api/README.md` and `README.ko.md` added. |
| Diagrams are generated through repo script. | PASS | Scenario, top-down layered architecture, and sequence assets were generated. |

## Targeted Test Evidence

Command:

```bash
./gradlew :ktor-ocr-api:test --no-configuration-cache --no-daemon
```

Result:

- PASS
- 5 tests passing:
  - `ready endpoint responds with plain text`
  - `recognizes uploaded image with parsed languages`
  - `rejects request without expected file field`
  - `rejects unsupported content type`
  - `maps OCR failures to service unavailable`

## Repairs During Verification

| Finding | Fix | Evidence |
|---|---|---|
| Ktor response serialization returned 500 for `OcrTextResponse` because the `@Serializable` DTO had a private companion object. | Made the companion object serializer-accessible while keeping `serialVersionUID` private. | Happy path route test now returns 200 and deserializes `OcrTextResponse`. |
| `languages=eng+kor` is decoded by HTTP query parsing as `eng kor`. | Language parser now accepts comma, plus, and whitespace separators. | Test request `languages=eng+kor` resolves to `["eng", "kor"]`. |

## Step 6 Checks

| Check | Status | Evidence |
|---|---|---|
| `./gradlew projects --no-configuration-cache --no-daemon` | PASS | `:ktor-ocr-api` is listed under examples. |
| `python3 docs/scripts/generate-example-readme-diagrams.py` | PASS | New scenario, architecture, and sequence families report `manual_exceptions=0`. |
| `xmllint --noout` for generated `examples-ktor-ocr-api-*.svg` | PASS | No XML errors. |
| README SVG-reference guard | PASS | No README references generated SVG assets. |
| `actionlint .github/workflows/Examples.yml` | PASS | No workflow lint errors. |
| Workflow escaped single-quote guard | PASS | No fixed-string `\'` matches. |
| `git diff --check` | PASS | No whitespace errors. |
| Visual inspection of generated PNG assets | PASS | Scenario, architecture, and sequence text fit; architecture is top-down layered. |
| Step 6-R code review | PASS | `docs/review/2026-06-06-issue-173-ktor-ocr-example-code-review.md` records P0=0/P1=0. |
