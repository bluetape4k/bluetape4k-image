# Issue 216 Structured OCR 검토

## 범위

- `images-ocr` public OCR models and extension functions.
- Tess4J adapter boundary and deterministic engine tests.
- README English/Korean parity와 #169 scope separation.

## 7계층 결과

| 계층 | 판정 | 근거 |
|---|---|---|
| 1 보안 | PASS | 새 file/network execution path는 없다. region metadata는 caller-supplied geometry일 뿐이다. |
| 2 아키텍처 | PASS | Tess4J classes stay internal; public API uses bluetape4k model values. |
| 3 API | PASS | Existing `extractText` and `suspendExtractText` remain unchanged; structured extraction is additive. |
| 4 Correctness | PASS | Missing confidence and invalid/missing boxes remain `null`; no placeholder geometry is fabricated. |
| 5 Tests | PASS | deterministic fake `TesseractClient` test가 detail level, region filtering, serialization, coroutine delegation을 다룬다. |
| 6 Docs | PASS | `README.md` and `README.ko.md` document structured results and #169 boundary. |
| 7 운영 | PASS | Host/native and container Tesseract tests remain opt-in; normal CI does not require Tesseract or large model artifacts. |

## P0/P1 게이트

- P0: 0
- P1: 0

## 검증 근거

- `./gradlew :bluetape4k-images-ocr:test --tests 'io.bluetape4k.images.ocr.OcrOptionsTest' --tests 'io.bluetape4k.images.ocr.ImmutableImageOcrExtensionsTest' --tests 'io.bluetape4k.images.ocr.TesseractOcrEngineTest'`: PASS, 15 tests.
- `./gradlew :bluetape4k-images-ocr:test`: PASS, 15 PASSing, 4 pending gated native/container OCR tests.
- `./gradlew detekt`: PASS, root detekt task has no source.
- `git diff --check`: PASS.
