# Issue #1 OCR Implementation Plan

- Issue: [#1](https://github.com/bluetape4k/bluetape4k-image/issues/1)
- Spec:
  `docs/superpowers/specs/2026-06-05-issue-1-ocr-design.md`
- Research:
  `docs/superpowers/research/2026-06-05-issue-1-ocr-research-refresh.md`
- Spec review:
  `docs/review/2026-06-05-issue-1-ocr-spec-review.md`
- Follow-up:
  [#169](https://github.com/bluetape4k/bluetape4k-image/issues/169)

## Execution Rules

- Keep all source changes inside the feature worktree.
- Do not start Step 4 implementation until Step 3-R passes and spec/plan
  artifacts are committed.
- Keep `bluetape4k-images` free of Tess4J/Tesseract dependencies.
- Keep public KDoc, README English text, GitHub artifacts, and commit messages in
  English.
- Keep user-facing Korean README and final chat reporting in Korean.
- Load and apply `$bluetape4k-code-patterns` before Step 4 implementation and
  Step 6-R code review; record the relevant checks in the Step DoD report.
- Use `$bluetape4k-diagram` gates for changed root README visual assets.
- If PaddleOCR or a more complex OCR runtime becomes necessary, stop and use
  follow-up issue #169 instead of expanding #1.

## Task Plan

| Task | Scope | Files |
|---|---|---|
| T1 Module registration | Add published OCR module to Gradle settings and version catalog | `settings.gradle.kts`, `gradle/libs.versions.toml`, `images-ocr/build.gradle.kts` |
| T2 API models | Add OCR options/result/enums/exceptions with English KDoc and serializable data classes | `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/*.kt` |
| T3 Engine and extensions | Implement Tess4J-backed engine and `ImmutableImage` sync/suspend extensions | `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/*.kt` |
| T4 Unit tests | Add fake-engine and model tests that pass without native OCR | `images-ocr/src/test/kotlin/...` |
| T5 Native tests | Add host-native Tess4J tests gated by `ocr.enabled` | `images-ocr/src/test/kotlin/...` |
| T6 Testcontainers tests | Add containerized Tesseract CLI smoke gated by `ocr.container.enabled` | `images-ocr/src/test/kotlin/...` |
| T7 Module docs | Add OCR README locale set and test resources | `images-ocr/README.md`, `images-ocr/README.ko.md`, `src/test/resources/*` |
| T8 Root docs/guidance | Register OCR in root README locale set and repo-local AGENTS | `README.md`, `README.ko.md`, `AGENTS.md` |
| T9 Root diagrams/charts | Update root README visual assets to include OCR | `docs/assets/readme-diagrams/*`, `docs/assets/readme-charts/*` |
| T10 CI/Nightly | Add OCR path filters, jobs, coverage artifact, and status needs | `.github/workflows/ci.yml`, `.github/workflows/nightly-tests.yml` |
| T11 Verification | Run targeted Gradle, docs, diagram, and workflow checks | commands below |
| T12 Review prep | Save code review artifact, lessons, PR body DoD | `docs/review/*`, `docs/lessons/*`, PR body |

## Implementation Details

### T1 Module registration

- Include `bluetape4k-images-ocr` and map it to `images-ocr`.
- Add `tess4j = "5.19.0"` and `libs.tess4j`.
- Let BOM constraints pick up the new published module through existing
  `rootProject.subprojects` logic.
- Verify with `./gradlew -q projects`.

### T2/T3 API and engine

- Create package `io.bluetape4k.images.ocr`.
- Implement:
  - `OcrEngine`
  - `OcrOptions`
  - `OcrResult`
  - `TesseractEngineMode`
  - `TesseractPageSegmentationMode`
  - `OcrException` and `OcrConfigurationException`
  - `TesseractOcrEngine`
  - `ImmutableImage.extractText`
  - `ImmutableImage.suspendExtractText`
- `TesseractOcrEngine` creates a fresh `Tesseract` per call.
- `TesseractOcrEngine` does not share mutable Tess4J client state across calls;
  each call owns its configured `Tesseract` instance for the duration of OCR.
- `suspendExtractText` wraps blocking OCR in `Dispatchers.IO`.
- Exceptions must not expose secrets or full local paths beyond the explicit
  configured tessdata path error context.

### T4/T5/T6 tests

- Add `junit-platform.properties` and `logback-test.xml`.
- Unit tests:
  - options validation
  - enum value mapping
  - fake engine delegation
  - suspend delegation with `runTest`
  - suspend cancellation propagation before/around the blocking boundary
  - serializable models
  - per-call engine lifecycle/configuration isolation
- Native tests:
  - `@EnabledIfSystemProperty(named = "ocr.enabled", matches = "true")`
  - generated English fixture OCR
  - missing language/datapath failure message
  - language-pack availability for `eng`, `kor`, `jpn`
- Container tests:
  - `@EnabledIfSystemProperty(named = "ocr.container.enabled", matches = "true")`
  - test-owned Dockerfile, no unverified OCR image
  - CLI OCR English smoke
  - `tesseract --list-langs` contains `eng`, `kor`, `jpn`

If local Docker remains unavailable, record local container verification as
skipped and rely on GitHub CI for `ocr.container.enabled=true`.

### T7/T8 docs

- Root README/README.ko:
  - add OCR adoption lane
  - add module row
  - add requirements row for Tesseract/traineddata
  - add install dependency
  - add usage example
  - add troubleshooting for `TESSDATA_PREFIX`, missing languages, and native
    library loading
  - add module README links
- Module README/README.ko:
  - describe installation and language data setup
  - show sync and suspend examples
  - explain `OcrOptions`
  - explain native and container test gates
- Repo-local `AGENTS.md`:
  - add module row and command examples
  - note OCR native tests are gated and sequential.

### T9 diagram work

- Update existing root README diagram/chart assets in place.
- Keep every README image as PNG and every PNG paired with SVG.
- Keep or regenerate Graphviz `.dot`, `.plain`, and `-graphviz.svg/png` for the
  connector-heavy root overview.
- Use English labels.
- Validate:
  - SVG XML parses.
  - PNG exists for each changed SVG.
  - README does not embed local SVG assets.
  - README image links resolve.
  - Rendered PNGs are inspected directly.
  - Geometry/source drift checks from `$bluetape4k-diagram` are recorded.

### T10 CI/Nightly

- CI:
  - add `images-ocr` output/filter/job
  - install Tesseract packages and Noto CJK fonts
  - run `tesseract --list-langs` preflight
  - run `:bluetape4k-images-ocr:test -Docr.enabled=true -Docr.container.enabled=true`
  - add test results artifact and status need
- Nightly full:
  - add OCR job and `coverage-images-ocr`
  - include OCR job in status and coverage aggregation needs
- Run `actionlint` after workflow edits.

## Validation Commands

Run in this order unless Step 3-R changes the plan:

1. `./gradlew -q projects`
2. `./gradlew :bluetape4k-images-ocr:compileKotlin :bluetape4k-images-ocr:compileTestKotlin --console=plain`
3. `./gradlew :bluetape4k-images-ocr:test --console=plain`
4. `./gradlew :bluetape4k-images-ocr:detekt --console=plain`
5. If local Tesseract is available:
   `./gradlew :bluetape4k-images-ocr:test -Docr.enabled=true --console=plain`
6. If local Docker is available:
   `./gradlew :bluetape4k-images-ocr:test -Docr.container.enabled=true --console=plain`
7. `./gradlew :bluetape4k-images-ocr:build --console=plain`
8. `./gradlew :bluetape4k-images-ocr:koverXmlReport --console=plain`
9. `xmllint --noout docs/assets/readme-diagrams/*.svg docs/assets/readme-charts/*.svg`
10. `find docs/assets/readme-diagrams docs/assets/readme-charts -name '*.svg' -exec sh -c 'test -f "${1%.svg}.png"' sh {} \\;`
11. `rg 'docs/assets/(readme-diagrams|readme-charts)/.*\\.svg' README*.md` must return no hits.
12. README image-link resolution check.
13. `actionlint`
14. `rg "\\\\'" .github/workflows` must return no hits.
15. `git diff --check`
16. Step 6-R 7-tier review with `$bluetape4k-code-patterns` reapplied and
    `P0 = 0`, `P1 = 0`.

## Rollback Plan

- Remove `images-ocr/`.
- Remove `bluetape4k-images-ocr` from `settings.gradle.kts`, README locale set,
  AGENTS, CI, Nightly, and diagrams.
- Remove `tess4j` from `gradle/libs.versions.toml`.
- Re-run `./gradlew -q projects`, `actionlint`, diagram validation, and
  `git diff --check`.

## Step 3 DoD

| Item | Status |
|---|---|
| Spec inputs mapped to implementation tasks | Done |
| Files and modules identified | Done |
| Test strategy sequenced | Done |
| CI/Nightly changes planned | Done |
| Diagram validation planned | Done |
| Local Docker/Tesseract skip conditions explicit | Done |
| Rollback plan documented | Done |
