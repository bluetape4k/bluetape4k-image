# Issue #244 Barcode API Design Spec

- Issue: [#244](https://github.com/bluetape4k/bluetape4k-image/issues/244)
- Parent epic: [#215](https://github.com/bluetape4k/bluetape4k-image/issues/215)
- Milestone: `0.4.0`
- Branch/worktree: `feat/issue-244-barcode-api` at `.worktrees/feat-issue-244-barcode-api`

## Problem

Barcode and QR extraction need a stable bluetape4k API before the repository
adds ZXing, BoofCV, commercial SDKs, or native adapters. The API must let
provider modules decode images without leaking concrete decoder classes into
callers or into the core `bluetape4k-images` artifact.

## Current Evidence

- `images-ocr` keeps Tess4J/Tesseract out of `bluetape4k-images` and exposes
  `ImmutableImage` sync/suspend extensions from an opt-in module.
- `images` already has backend-neutral detection models with serializable value
  objects, provider identity metadata, validation helpers, and suspend wrappers.
- #215 requires API/provider separation from the beginning:
  `images-barcode-api`, `images-barcode-zxing`, and optional future providers.
- #245 owns ZXing implementation. #246 owns BoofCV research. #247 owns shared
  fixtures and provider capability documentation.

## Goals

- Add a published `bluetape4k-images-barcode-api` module.
- Define provider-neutral result models, provider metadata, options, failure
  reasons, and sync/suspend entry points.
- Support `ImmutableImage`, `ByteArray`, `Path`, `InputStream`, and `Source`
  input entry points without adding a concrete barcode decoder dependency.
- Register the module across Gradle, BOM, root/module README locale set,
  repo-local `AGENTS.md`, CI, Nightly, release/publish validation lists, and
  example path filters where needed.
- Keep API tests always-on, deterministic, and pure JVM.

## Non-Goals

- Do not implement ZXing, BoofCV, OpenCV, ZBar, Dynamsoft, or Aspose in this
  issue.
- Do not add barcode dependencies to `bluetape4k-images` or the API module.
- Do not add real barcode fixtures in this issue unless needed for API tests.
  Shared fixture breadth belongs to #247.
- Do not change OCR APIs.

## Design Options

### Option A: API and ZXing in one `images-barcode` module

Rejected. It would make ZXing the shape of the public API and make future
providers adapt to ZXing concepts instead of a bluetape4k contract.

### Option B: API module first, providers later

Selected. It keeps dependency boundaries clean and lets #245, #246, and future
provider modules share one contract.

### Option C: Reuse generic `images.detection` models directly

Rejected as the primary API. Barcode extraction has decoded payload, symbology,
checksum, raw bytes, and failure semantics that are not general object detection
facts. The geometry style can align with detection models, but barcode results
should remain domain-specific.

## Module Boundary

Add:

```text
images-barcode-api/
  artifact: io.github.bluetape4k.image:bluetape4k-images-barcode-api
  package: io.bluetape4k.images.barcode
```

Gradle dependencies:

- `api(project(":bluetape4k-images"))` because public APIs accept
  `ImmutableImage`.
- `implementation(libs.kotlinx.coroutines.core)` for suspend wrappers.
- `testImplementation(libs.bluetape4k.junit5)` and
  `testImplementation(libs.kotlinx.coroutines.test)` for API tests.

## Public API

Package: `io.bluetape4k.images.barcode`

Core models:

- `BarcodeFormat`: stable symbology enum with common values such as `QR_CODE`,
  `CODE_128`, `EAN_13`, `EAN_8`, `UPC_A`, `UPC_E`, `DATA_MATRIX`, `AZTEC`,
  `PDF_417`, `CODABAR`, `ITF`, `UNKNOWN`.
- `BarcodeProviderIdentity`: provider name, optional version/backend, metadata.
- `BarcodePoint`, `BarcodeBoundingBox`, `BarcodeRegion`: pixel or normalized
  localization data.
- `BarcodeResult`: decoded text, format, provider, optional region, confidence,
  quality, raw bytes, failure-free metadata, and optional raw backend format.
- `BarcodeFailureReason`: `NO_BARCODE`, `UNSUPPORTED_FORMAT`,
  `MALFORMED_INPUT`, `DECODE_FAILED`, `PROVIDER_UNAVAILABLE`, `CANCELLED`,
  `UNKNOWN`.
- `BarcodeException`: sanitized failure exception carrying
  `BarcodeFailureReason`.
- `BarcodeOptions`: requested formats, `tryHarder`, `includeRawBytes`,
  `metadata`, and optional minimum confidence filter.

Engine and entry points:

```kotlin
fun interface BarcodeReader {
    fun readBarcodes(image: ImmutableImage, options: BarcodeOptions = BarcodeOptions()): List<BarcodeResult>
}

fun ImmutableImage.extractBarcodes(
    reader: BarcodeReader,
    options: BarcodeOptions = BarcodeOptions(),
): List<BarcodeResult>

suspend fun ImmutableImage.suspendExtractBarcodes(
    reader: BarcodeReader,
    options: BarcodeOptions = BarcodeOptions(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): List<BarcodeResult>
```

Input helpers:

```kotlin
fun BarcodeReader.readBarcodes(bytes: ByteArray, options: BarcodeOptions = BarcodeOptions()): List<BarcodeResult>
fun BarcodeReader.readBarcodes(path: Path, options: BarcodeOptions = BarcodeOptions()): List<BarcodeResult>
fun BarcodeReader.readBarcodes(input: InputStream, options: BarcodeOptions = BarcodeOptions()): List<BarcodeResult>
fun BarcodeReader.readBarcodes(source: Source, options: BarcodeOptions = BarcodeOptions()): List<BarcodeResult>
```

## Validation Rules

- All value objects are `Serializable` and define `serialVersionUID`.
- Public value models use private constructors plus companion `invoke` when
  validation is needed.
- Caller input validation uses bluetape4k `require*` helpers where available.
- Metadata maps use non-blank string keys and values.
- Confidence and quality values are nullable. When present, they must be finite
  and within `0.0..1.0`.
- Raw bytes are optional and provider controlled; the API does not require
  every provider to expose them.
- Suspend wrappers use `withContext(dispatcher)` and do not swallow
  `CancellationException`.

## Testing Strategy

Always-on tests:

- Options validation and format filtering.
- Provider identity validation.
- Region and bounding-box validation.
- Extension delegation for sync and suspend extraction using a fake reader.
- Cancellation propagation for suspend extraction when a provider reports
  `CancellationException`.
- Byte/path/input-stream/source helpers load through `immutableImageOf(...)`
  and delegate to the reader.
- Failure exception keeps sanitized message and reason.
- Serializable model smoke tests.

Concurrency stress helpers are not required for #244 because the API module does
not introduce shared mutable state, background jobs, caches, or provider
lifecycle.

## Documentation and Registration

Update:

- `settings.gradle.kts`
- `AGENTS.md`
- `README.md`
- `README.ko.md`
- `images-barcode-api/README.md`
- `images-barcode-api/README.ko.md`
- `.github/workflows/ci.yml`
- `.github/workflows/nightly-tests.yml`
- `.github/workflows/release.yml`
- `.github/workflows/publish-snapshot.yml`
- `.github/workflows/Examples.yml` path filters if examples watch all modules

BOM constraints are automatic for published subprojects because `bom/build.gradle.kts`
iterates published subprojects, but the module must not be treated as an example
or benchmark.

## Risks and Mitigations

- **API overfits ZXing**: keep ZXing-specific names out of API and document
  provider metadata as string-only.
- **Geometry ambiguity**: model coordinate space explicitly and validate pixel
  bounds only when image dimensions are available.
- **Suspend dispatcher mismatch**: default to `Dispatchers.Default` for local
  CPU-bound decoders and let service/native providers pass `Dispatchers.IO`.
- **Workflow drift**: add CI and Nightly jobs plus path filters in the same PR.
- **Docs drift**: README examples must use actual source names after
  implementation.

## Acceptance Criteria

- `:bluetape4k-images-barcode-api` exists and builds without ZXing/BoofCV.
- Public models and entry points have English KDoc.
- Unit tests cover validation, delegation, input helpers, and exceptions.
- Root/module README locale set explains API/provider split.
- CI/Nightly/release validation include the new module.
- `./gradlew projects`, targeted tests, compile, `actionlint`, and
  `git diff --check` pass.
- Local-equivalent Step 2-R/3-R reviews report P0/P1 = 0 before implementation.
