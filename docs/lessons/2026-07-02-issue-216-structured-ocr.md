# Issue 216 Structured OCR

## Context

Issue #216 added structured OCR extraction to `bluetape4k-images-ocr` while
keeping the existing plain-text `extractText` helpers source-compatible.

## Decision

- Keep Tess4J types behind internal adapter boundaries.
- Model page, block, line, and word metadata with serializable bluetape4k API
  values.
- Treat missing or invalid confidence and bounding boxes as `null`; do not
  coerce them into placeholder values.
- Keep PaddleOCR/GPU/model-download adoption outside this module and continue
  tracking that lane through #169.

## Verification

- Always-on tests use deterministic fake `TesseractClient` fixtures, not host
  Tesseract, so normal CI can verify structured output and region filtering.
- Host/native and container Tesseract smoke tests remain opt-in gates.

## Future Guard

When extending OCR structure, update `README.md` and `README.ko.md` together and
avoid exposing Tess4J classes from public bluetape4k APIs.
