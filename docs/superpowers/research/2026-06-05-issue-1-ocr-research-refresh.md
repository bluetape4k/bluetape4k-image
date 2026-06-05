# Issue #1 OCR Research Refresh

- Issue: [#1](https://github.com/bluetape4k/bluetape4k-image/issues/1)
- Date: 2026-06-05
- Scope: refresh the prior OCR handoff before writing the implementation spec.

## Decision

Keep issue #1 focused on a Tesseract/Tess4J baseline in a new optional
`bluetape4k-images-ocr` module. Do not add OCR dependencies to
`bluetape4k-images`.

PaddleOCR is out of scope for issue #1. It is a broader Python/model/document-AI
stack with separate runtime, model packaging, serving, hardware, and CI concerns.
The follow-up is tracked in
[#169](https://github.com/bluetape4k/bluetape4k-image/issues/169).

## Updated Source Evidence

- Prior repo-local research:
  `docs/superpowers/research/2026-05-29-issue-83-ocr-dependency-model-packaging-research.md`.
- Prior lesson:
  `docs/lessons/2026-05-29-image-ai-research-gates.md`.
- GitHub issue #83 is closed with a comment linking the research handoff.
- Tess4J Maven metadata checked on 2026-06-05: latest/release `5.19.0`,
  last updated `20260527033916`.
- Tess4J GitHub release checked on 2026-06-05:
  `tess4j-5.19.0`, Apache-2.0, published 2026-05-27.
- Tesseract GitHub release checked on 2026-06-05:
  `5.5.2`, Apache-2.0, published 2025-12-26.
- Tesseract installation docs state that engine packages and language
  `traineddata` packages are installed separately.
- `tesseract-ocr/tessdata` is Apache-2.0 and provides trained models.
- PaddleOCR GitHub release checked on 2026-06-05:
  `v3.6.0`, Apache-2.0, published 2026-05-28.

## Repository Fit

- Existing optional native/runtime dependencies already live outside the core
  module, for example `bluetape4k-images-vips-java21` and
  `bluetape4k-images-vips-java25`.
- The root README and repo-local `AGENTS.md` module list must be updated when a
  new module is added.
- Root README visual assets currently include module overview, module chart, and
  architecture diagrams. Adding OCR makes those assets stale, so diagram work is
  in scope for issue #1.
- `settings.gradle.kts`, the BOM constraints, CI path filters, CI jobs, Nightly
  jobs, coverage artifacts, and README module tables must all include the new
  module.

## API Evidence

Tess4J 5.19.0 exposes the required first implementation surface:

- `ITesseract.doOCR(BufferedImage)`
- `ITesseract.setDatapath(String)`
- `ITesseract.setLanguage(String)`
- `ITesseract.setOcrEngineMode(int)`
- `ITesseract.setPageSegMode(int)`
- `Tesseract` reads `TESSDATA_PREFIX` by default and validates missing datapath
  or language data during initialization.

This supports an `ImmutableImage.awt()` based implementation without temporary
files for the common single-image path.

## Test and CI Strategy

Use three test levels:

1. Unit tests with a fake `OcrEngine` for API validation, option validation, and
   suspend wrapper behavior. These run in the normal local and CI test path.
2. Host-native Tess4J integration tests gated by `-Docr.enabled=true`. CI can
   install `tesseract-ocr`, `tesseract-ocr-eng`, `tesseract-ocr-kor`, and
   `tesseract-ocr-jpn` before running this lane.
3. Testcontainers OCR CLI smoke tests gated by `-Docr.container.enabled=true`.
   These validate a containerized Tesseract runtime and language data, but they
   do not replace the host-native Tess4J integration test because a separate
   container cannot load native libraries into the host JVM.

Local Docker is not available in the current agent environment, so the
Testcontainers lane must be designed as CI-capable and locally skippable with an
explicit skip reason.

## Follow-Up Scope Guard

Created [#169](https://github.com/bluetape4k/bluetape4k-image/issues/169) for
PaddleOCR backend evaluation. Do not expand issue #1 into PaddleOCR integration.

If Testcontainers image build/runtime reliability becomes larger than expected,
keep the host-native Tess4J baseline and file a separate CI hardening issue
instead of delaying the API/module baseline.

## Step 1-R DoD Inputs for Spec

- Add `bluetape4k-images-ocr` as a published module.
- Add `tess4j = "5.19.0"` and `tess4j = { module = "net.sourceforge.tess4j:tess4j" }`
  to the repo-local version catalog.
- Public API should expose `OcrEngine`, `TesseractOcrEngine`, `OcrOptions`,
  `OcrResult`, `extractText`, and `suspendExtractText`.
- Keep `traineddata` external by default and document `TESSDATA_PREFIX` plus
  explicit `tessdataPath`.
- Keep extension functions in the OCR module package so consumers opt in by
  adding the OCR artifact.
- Update README/README.ko, module README/README.ko, root diagrams/charts, CI,
  Nightly, BOM, and repo-local `AGENTS.md`.
