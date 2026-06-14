# Issue #1 OCR Design Spec

- Issue: [#1](https://github.com/bluetape4k/bluetape4k-image/issues/1)
  `feat: OCR (Optical Character Recognition) 지원 추가`
- Milestone: `0.3.0`
- Branch/worktree: `feat/issue-1-ocr-support` at
  `.worktrees/feat-issue-1-ocr-support`
- Research input:
  `docs/superpowers/research/2026-06-05-issue-1-ocr-research-refresh.md`
- Follow-up scope guard:
  [#169](https://github.com/bluetape4k/bluetape4k-image/issues/169)

## Problem

`bluetape4k-image` has image loading, transformation, analysis, CAPTCHA, Ktor,
Spring Boot, and libvips modules, but no OCR surface. Issue #1 asks for text
extraction from `ImmutableImage`, suspend-friendly APIs, Korean/multilingual
support, and Docker/Testcontainers-based CI evidence.

The feature must not make the core `bluetape4k-images` artifact depend on native
OCR libraries or bundled model data.

## Current Evidence

- Existing image-analysis extensions live in `bluetape4k-images`, for example
  `blurScore`, `dominantColors`, and suspend variants.
- Prior OCR research for issue #83 recommends a separate
  `bluetape4k-images-ocr` module backed by Tesseract through Tess4J.
- Tess4J 5.19.0 exposes `ITesseract.doOCR(BufferedImage)`, `setDatapath`,
  `setLanguage`, `setOcrEngineMode`, and `setPageSegMode`.
- Tesseract installation docs separate the OCR engine from language
  `traineddata` packages.
- PaddleOCR 3.6.0 is a larger Python/model/document-AI stack and is tracked as
  follow-up issue #169.
- Local Docker is unavailable in the current agent environment, so local
  Testcontainers verification must be skippable. CI can still provide the
  Testcontainers lane.

## Goals

- Add a published `bluetape4k-images-ocr` module.
- Provide sync and suspend OCR extraction APIs for `ImmutableImage`.
- Keep OCR opt-in by package/import and artifact dependency.
- Support explicit language lists and explicit tessdata paths.
- Document Tesseract engine and language data prerequisites for English,
  Korean, and Japanese.
- Add always-on unit tests plus gated native OCR and Testcontainers tests.
- Register the module in Gradle, BOM, README locale sets, CI, Nightly, coverage
  artifacts, and repo-local guidance.
- Update root README diagrams/charts so the OCR module is visible and the
  rendered visual assets remain source-backed.

## Non-Goals

- Do not add Tess4J or Tesseract dependencies to `bluetape4k-images`.
- Do not bundle `*.traineddata` files in published jars.
- Do not implement PaddleOCR in this PR.
- Do not add a cloud OCR provider or credential-driven OCR service.
- Do not claim container tests prove host JVM native library loading.

## Design Options

### Option A: Put OCR extensions directly in `bluetape4k-images`

Rejected. It would make every core image consumer pull native OCR dependencies,
JNA, and model/runtime concerns even when they only need resize/filter/encode.

### Option B: Add optional `bluetape4k-images-ocr` module with Tess4J

Selected. It matches the existing optional-native module pattern, keeps
dependencies isolated, and still lets users write extension-style code after
adding the OCR artifact.

### Option C: Implement PaddleOCR or document-AI backend now

Rejected for #1 and moved to #169. PaddleOCR requires a separate runtime/model
strategy, likely service/container boundaries, and different CI evidence.

## Module Boundary

Add:

```text
images-ocr/
  artifact: io.github.bluetape4k.image:bluetape4k-images-ocr
  package: io.bluetape4k.images.ocr
```

Gradle dependencies:

- `api(project(":bluetape4k-images"))` because public APIs accept
  `ImmutableImage`.
- `implementation(libs.tess4j)`.
- `implementation(libs.kotlinx.coroutines.core)`.
- `testImplementation(libs.bluetape4k.junit5)`.
- `testImplementation(libs.kotlinx.coroutines.test)`.
- `testImplementation(libs.testcontainers)` for the gated container lane.

Version catalog additions:

```toml
[versions]
tess4j = "5.19.0"

[libraries]
tess4j = { module = "net.sourceforge.tess4j:tess4j", version.ref = "tess4j" }
```

## Public API

Package: `io.bluetape4k.images.ocr`

```kotlin
interface OcrEngine {
    fun extractText(image: ImmutableImage, options: OcrOptions = OcrOptions()): OcrResult

    suspend fun suspendExtractText(
        image: ImmutableImage,
        options: OcrOptions = OcrOptions(),
    ): OcrResult
}
```

```kotlin
data class OcrOptions(
    val languages: List<String> = listOf("eng"),
    val tessdataPath: Path? = null,
    val pageSegmentationMode: TesseractPageSegmentationMode? = null,
    val engineMode: TesseractEngineMode? = null,
    val variables: Map<String, String> = emptyMap(),
    val trimResult: Boolean = true,
) : Serializable
```

```kotlin
data class OcrResult(
    val text: String,
    val languages: List<String>,
    val confidence: Int? = null,
) : Serializable
```

```kotlin
enum class TesseractEngineMode(val value: Int)
enum class TesseractPageSegmentationMode(val value: Int)
```

```kotlin
class TesseractOcrEngine : OcrEngine

fun ImmutableImage.extractText(
    engine: OcrEngine = TesseractOcrEngine(),
    options: OcrOptions = OcrOptions(),
): OcrResult

suspend fun ImmutableImage.suspendExtractText(
    engine: OcrEngine = TesseractOcrEngine(),
    options: OcrOptions = OcrOptions(),
): OcrResult
```

API rules:

- Public KDoc must be English.
- `OcrOptions` and `OcrResult` must implement `Serializable` and define
  `serialVersionUID`.
- The enum values wrap Tess4J integer constants so callers do not import
  `ITessAPI` types for ordinary use.
- Validate language codes as non-blank strings and join them with `+` for
  Tesseract.
- Validate option variable keys as non-blank strings.
- Use `Dispatchers.IO` for suspend OCR because Tess4J/Tesseract is blocking.
- Rethrow `CancellationException` before broad exception handling in suspend
  paths.
- Wrap Tess4J failures in OCR-specific runtime exceptions with sanitized
  messages.

## Runtime Behavior

`TesseractOcrEngine` creates a fresh Tess4J `Tesseract` instance per extraction
call. This avoids sharing mutable Tess4J state across callers.

Configuration order:

1. Apply `tessdataPath` when present.
2. Apply joined `languages`, defaulting to `eng`.
3. Apply optional page segmentation mode and engine mode.
4. Apply Tesseract variables.
5. Run `doOCR(image.awt())`.
6. Trim text when `trimResult = true`.

The default path relies on Tess4J/Tesseract resolution such as `TESSDATA_PREFIX`
or platform defaults. Missing language data should fail clearly.

## Testing Strategy

Always-on tests:

- `OcrOptionsTest`: language validation, variable validation, language join,
  serializable model behavior.
- `OcrExtensionsTest`: fake `OcrEngine` verifies sync/suspend extension
  delegation and result propagation.
- `TesseractOcrEngineConfigurationTest`: option-to-Tess4J configuration is
  covered with a seam or focused internal adapter where practical.

Gated host-native tests:

- Enabled by `-Docr.enabled=true`.
- Generate high-contrast English text image with Java2D.
- Verify Tess4J extracts expected English text from `ImmutableImage.awt()`.
- Verify missing `tessdataPath` or missing language data produces a clear
  exception.
- Verify language list accepts `eng`, `kor`, and `jpn` when corresponding packs
  are installed; exact Korean/Japanese OCR text matching can be non-blocking if
  font/rendering reliability is lower than language-pack resolution.

Gated Testcontainers tests:

- Enabled by `-Docr.container.enabled=true`.
- Prefer a test-owned Dockerfile based on the GitHub Actions Ubuntu LTS image
  family and install `tesseract-ocr`, `tesseract-ocr-eng`, `tesseract-ocr-kor`,
  and `tesseract-ocr-jpn`. Do not depend on an unverified public OCR image.
- Copy or generate a fixture image and run the Tesseract CLI in the container.
- Verify CLI extraction for English and language-pack availability for
  multilingual support.
- Record explicitly that this proves the containerized runtime, not host JVM
  Tess4J native loading.

## CI and Nightly

`ci.yml`:

- Add `images-ocr` to `changes.outputs` and `paths-filter`.
- Add `test-images-ocr` job.
- Install `tesseract-ocr`, `tesseract-ocr-eng`, `tesseract-ocr-kor`,
  `tesseract-ocr-jpn`, and `fonts-noto-cjk` in the OCR job.
- Run `tesseract --list-langs` before Gradle and fail early if `eng`, `kor`, or
  `jpn` is missing.
- Run `./gradlew :bluetape4k-images-ocr:test -Docr.enabled=true -Docr.container.enabled=true --no-daemon`.
- Upload `test-results-images-ocr`.
- Add the OCR job to `ci-status.needs`.

`nightly-tests.yml`:

- Add `test-images-ocr` to the full scope.
- Generate/upload `coverage-images-ocr`.
- Add the OCR job and coverage artifact to nightly status/coverage aggregation.

Workflow edits require `actionlint` and `rg "\\\\'" .github/workflows` before
push.

## Documentation and Diagrams

Update:

- Root `README.md` and `README.ko.md` module tables, requirements, installation,
  usage, troubleshooting, and module README link list.
- New `images-ocr/README.md` and `images-ocr/README.ko.md`.
- Repo-local `AGENTS.md` module list and command section.
- Root README visual assets:
  - `docs/images/readme-diagrams/root-readme-overview-01.svg`
  - `docs/images/readme-diagrams/root-readme-overview-01.png`
  - `docs/images/readme-diagrams/root-readme-overview-01.dot`
  - `docs/images/readme-diagrams/root-readme-overview-01.plain`
  - `docs/images/readme-diagrams/root-readme-overview-01-graphviz.svg`
  - `docs/images/readme-diagrams/root-readme-overview-01-graphviz.png`
  - `docs/images/readme-charts/root-readme-module-chart-01.svg`
  - `docs/images/readme-charts/root-readme-module-chart-01.png`
  - `docs/images/readme-diagrams/bluetape4k-image-architecture-01.svg`
  - `docs/images/readme-diagrams/bluetape4k-image-architecture-01.png`

Diagram labels stay English and README files embed PNG only. The diagram update
must follow `$bluetape4k-diagram` validation: SVG parses, PNG exists, Graphviz
evidence exists where connector-heavy, README links resolve, and rendered PNGs
are visually inspected.

## Risks and Mitigations

| Risk | Severity | Mitigation |
|---|---:|---|
| Tess4J native dependencies fail on developer machines | P1 | Keep OCR in optional module, document install/traineddata, provide clear exceptions |
| Testcontainers does not prove host JVM native loading | P1 | Keep separate host-native `-Docr.enabled=true` lane and state evidence boundary |
| Korean/Japanese OCR text matching is flaky due font/rasterization | P2 | Verify language-pack availability in CI; make exact non-Latin text matching a follow-up only if unreliable |
| PaddleOCR expands scope materially | P2 | Track as #169 and keep #1 baseline focused |
| README diagrams drift after module addition | P1 | Update SVG/PNG/dot/plain assets and validate via diagram skill |
| New module misses CI/Nightly/BOM registration | P1 | Include module-registration checklist in plan and Step 6/6-R review |

## Acceptance Criteria

- `bluetape4k-images-ocr` is registered and published through normal Gradle/BOM
  paths.
- `ImmutableImage.extractText()` and `suspendExtractText()` extension functions
  are available from the OCR module package.
- `OcrOptions` supports `eng`, `kor`, `jpn`, custom language lists, explicit
  `tessdataPath`, PSM/OEM, and variables.
- Tesseract/Tess4J failures surface as clear OCR exceptions.
- Unit tests pass without native OCR dependencies.
- Native OCR tests pass when `-Docr.enabled=true` and Tesseract/traineddata are
  installed.
- Testcontainers OCR smoke passes when `-Docr.container.enabled=true` and Docker
  is available.
- README/README.ko and `images-ocr` README/README.ko document install, usage,
  multilingual setup, and troubleshooting.
- Root README diagrams/charts include OCR and pass diagram validation.
- CI/Nightly include `bluetape4k-images-ocr` test and coverage visibility.
- Step 6-R review closes with `P0 = 0` and `P1 = 0`.

## Spec DoD

| Item | Status |
|---|---|
| Requirements from issue #1 restated | Done |
| Prior research and current primary sources incorporated | Done |
| At least three design approaches considered | Done |
| Selected approach keeps OCR dependencies out of core module | Done |
| PaddleOCR scope expansion filed as follow-up | Done (#169) |
| Public API shape specified | Done |
| Test, CI, Nightly, and Testcontainers strategy specified | Done |
| Diagram impact explicitly decided | Done |
| Risks and mitigations documented | Done |
