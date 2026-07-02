# Issue 216 Structured OCR Review

## Scope

- `images-ocr` public OCR models and extension functions.
- Tess4J adapter boundary and deterministic engine tests.
- README English/Korean parity and #169 scope separation.

## 7-Tier Result

| Tier | Verdict | Evidence |
|---|---|---|
| 1 Security | PASS | No new file/network execution path; region metadata is caller-supplied geometry only. |
| 2 Architecture | PASS | Tess4J classes stay internal; public API uses bluetape4k model values. |
| 3 API | PASS | Existing `extractText` and `suspendExtractText` remain unchanged; structured extraction is additive. |
| 4 Correctness | PASS | Missing confidence and invalid/missing boxes remain `null`; no placeholder geometry is fabricated. |
| 5 Tests | PASS | Deterministic fake `TesseractClient` tests cover detail levels, region filtering, serialization, and coroutine delegation. |
| 6 Docs | PASS | `README.md` and `README.ko.md` document structured results and #169 boundary. |
| 7 Operations | PASS | Host/native and container Tesseract tests remain opt-in; normal CI does not require Tesseract or large model artifacts. |

## P0/P1 Gate

- P0: 0
- P1: 0

## Validation Evidence

- `./gradlew :bluetape4k-images-ocr:test --tests 'io.bluetape4k.images.ocr.OcrOptionsTest' --tests 'io.bluetape4k.images.ocr.ImmutableImageOcrExtensionsTest' --tests 'io.bluetape4k.images.ocr.TesseractOcrEngineTest'`: PASS, 15 tests.
- `./gradlew :bluetape4k-images-ocr:test`: PASS, 15 passing, 4 pending gated native/container OCR tests.
- `./gradlew detekt`: PASS, root detekt task has no source.
- `git diff --check`: PASS.
