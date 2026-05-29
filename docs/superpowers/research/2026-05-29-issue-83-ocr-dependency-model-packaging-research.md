# Issue #83 OCR Dependency and Model Packaging Research

- Issue: [#83](https://github.com/bluetape4k/bluetape4k-image/issues/83)
- Implementation target: [#1](https://github.com/bluetape4k/bluetape4k-image/issues/1)
- Date: 2026-05-29
- Scope: OCR runtime choice, traineddata packaging, module boundary, CI strategy.

## Summary

Proceed with a separate `bluetape4k-images-ocr` module backed by Tesseract
through Tess4J. Do not add OCR dependencies to `bluetape4k-images`, and do not
bundle traineddata files in the published artifact by default.

Tesseract is the lowest-risk open-source OCR baseline for Java server-side use:
the engine and official `tessdata` repositories are Apache-2.0 licensed, Tess4J
is the established JVM/JNA wrapper, and Korean/Japanese/multilingual support is
handled by explicit traineddata installation rather than a hidden runtime
download.

## Current Repository Fit

- Current core modules are pure image processing plus optional integration
  modules. Heavy native/runtime dependencies already live in dedicated modules
  such as `bluetape4k-images-vips-java21` and `bluetape4k-images-vips-java25`.
- The OCR issue asks for `ImmutableImage.extractText()` and
  `suspendExtractText()`, but adding OCR directly to `bluetape4k-images` would
  force native OCR dependencies onto every consumer. Keep the extensions in the
  OCR module package instead.
- Existing native-lifecycle guidance applies: native tests must run
  sequentially, and Testcontainers-backed verification should not run in
  parallel with other container lanes.

## Candidate Evaluation

| Candidate | Decision | Rationale |
|---|---|---|
| Tess4J + Tesseract | Recommended | Mature JVM wrapper, Apache-2.0-compatible stack, supports local/offline OCR, works with explicit `tessdata` directories. |
| Tesseract CLI process wrapper | Rejected for first implementation | Easier isolation, but worse lifecycle/error handling, slower per-call startup unless a pool is built, and awkward for library APIs. |
| Cloud OCR APIs | Rejected | Network, credentials, billing, privacy, and provider lock-in do not fit a reusable local image library. |
| DJL/OCR model wrappers | Deferred | Better for ML pipelines, but OCR model packaging and text layout quality would require a separate model strategy. |
| Pure Java OCR libraries | Rejected | Weak maintenance and accuracy story compared with Tesseract. |

## Recommended Module Boundary

Add a non-core module:

```text
images-ocr/
  artifact: io.github.bluetape4k.image:bluetape4k-images-ocr
  package: io.bluetape4k.images.ocr
```

Dependencies:

- `api(project(":bluetape4k-images"))` because the public API accepts
  `ImmutableImage`.
- `implementation(net.sourceforge.tess4j:tess4j)` with the version governed in
  the version catalog.
- `implementation(libs.kotlinx.coroutines.core)` for suspend wrappers.
- Test dependencies should include JUnit 5, bluetape4k assertions, and
  Testcontainers only if the native runtime is verified through a container.

Public API shape:

```kotlin
interface OcrEngine {
    fun extractText(image: ImmutableImage, options: OcrOptions = OcrOptions()): OcrResult
    suspend fun extractTextSuspend(image: ImmutableImage, options: OcrOptions = OcrOptions()): OcrResult
}
```

```kotlin
data class OcrOptions(
    val languages: List<String> = listOf("eng"),
    val tessdataPath: Path? = null,
    val pageSegmentationMode: Int? = null,
    val engineMode: Int? = null,
) : Serializable
```

```kotlin
data class OcrResult(
    val text: String,
    val languageHint: List<String>,
    val confidence: Double? = null,
) : Serializable
```

Expose convenience extensions from this module only:

```kotlin
fun ImmutableImage.extractText(
    engine: OcrEngine = TesseractOcrEngine(),
    options: OcrOptions = OcrOptions(),
): OcrResult
```

## Model and Data Packaging

Do not bundle `*.traineddata` files in the library artifact in the first
release. Instead:

- Resolve `tessdataPath` from options first.
- Fall back to `TESSDATA_PREFIX` or the platform default only if Tess4J/Tesseract
  can resolve it reliably.
- Document installation examples for `eng`, `kor`, and `jpn`.
- Add a README section that makes language data an explicit runtime
  prerequisite.
- Keep any test-only traineddata in container images or CI setup, not in
  published jars.

This keeps the artifact small and avoids making language/model updates part of
the library release cadence.

## CI and Verification Strategy

Use two test tiers:

1. JVM unit tests with a fake `OcrEngine` or small image encoding fixtures.
2. Native OCR integration tests gated by `-Docr.enabled=true`, run sequentially.

Recommended validation after implementation:

```bash
./gradlew :bluetape4k-images-ocr:test
./gradlew :bluetape4k-images-ocr:test -Docr.enabled=true
```

The native lane should verify:

- `eng` text extraction from a generated or fixture image.
- `kor` and `jpn` language-pack resolution when the CI image installs the
  traineddata packages.
- clear failure messages when `tessdataPath` or language data is missing.
- cancellation propagation in suspend APIs before broad exception handling.

## Handoff for Issue #1

Acceptance criteria for implementation:

- Add `bluetape4k-images-ocr` without changing core `bluetape4k-images`
  dependencies.
- Provide sync and suspend OCR APIs plus `ImmutableImage` extension functions in
  the OCR module package.
- Support explicit language list and explicit `tessdataPath`.
- Document native Tesseract and traineddata prerequisites in `README.md` and
  `README.ko.md`.
- Add gated native integration tests and keep them sequential.
- Add BOM/module/README/CI registration if the module is published.

## Sources

- Tesseract README and license: https://github.com/tesseract-ocr/tesseract
- Tesseract installation and language packages: https://github.com/tesseract-ocr/tessdoc/blob/main/Installation.md
- Tesseract traineddata repository: https://github.com/tesseract-ocr/tessdata
- Tess4J Maven metadata checked on 2026-05-29: latest `5.16.0`
- Apache Tika Tess4J parser notes: https://tika.apache.org/docs/4.0.0-SNAPSHOT/configuration/parsers/tess4j-parser.html
