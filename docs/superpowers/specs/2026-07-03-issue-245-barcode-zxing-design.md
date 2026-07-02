# Issue #245 ZXing Barcode Provider Design Spec

- Issue: [#245](https://github.com/bluetape4k/bluetape4k-image/issues/245)
- Parent epic: [#215](https://github.com/bluetape4k/bluetape4k-image/issues/215)
- Milestone: `0.4.0`
- Branch/worktree: `feat/issue-245-barcode-zxing` at `.worktrees/feat-issue-245-barcode-zxing`

## Problem

The provider-neutral barcode API from #244 needs a first OSS implementation so
callers can decode QR and common 1D barcodes without depending on a future
native or commercial provider. ZXing is the lowest-friction first provider
because it is Apache-2.0, pure JVM, and covers QR plus major retail/industrial
1D symbologies.

## Current Evidence

- `bluetape4k-images-barcode-api` already defines `BarcodeReader`,
  `BarcodeOptions`, `BarcodeResult`, `BarcodeRegion`, provider identity, and
  sanitized `BarcodeException` failure reasons.
- #215 requires provider dependencies to stay out of `bluetape4k-images` and
  the API module.
- #245 requires QR plus at least one 1D family, provider metadata, result
  points/bounding polygon mapping, no-code handling, and documentation of ZXing
  maintenance/capability boundaries.
- Maven Central metadata observed for `com.google.zxing:core` and
  `com.google.zxing:javase` reports latest/release `3.5.4`.

## Goals

- Add published module `bluetape4k-images-barcode-zxing`.
- Keep all ZXing dependencies and imports inside the ZXing provider module.
- Implement `ZxingBarcodeReader` as a `BarcodeReader`.
- Decode QR Code and Code 128 in deterministic tests, with broader API format
  mappings for supported ZXing symbologies.
- Map ZXing text, backend format, result points, bounding box, raw bytes when
  requested, provider identity, and metadata into #244 models.
- Return an empty list for images with no barcode.
- Throw `BarcodeException` with provider-neutral failure reasons for unsupported
  requested formats, malformed encoded inputs, and decode failures.
- Document explicit provider construction and `ImmutableImage.extractBarcodes`
  usage in English and Korean README files.

## Non-Goals

- Do not make ZXing the default provider for `images-barcode-api`.
- Do not add ZXing to `bluetape4k-images` or `images-barcode-api`.
- Do not implement BoofCV, commercial SDKs, OpenCV, or ZBar in this issue.
- Do not build the full cross-provider fixture/capability matrix; #247 owns
  that after at least one concrete provider lands.
- Do not introduce a service loader registry before there are multiple shipped
  providers.

## Module Boundary

Add:

```text
images-barcode-zxing/
  artifact: io.github.bluetape4k.image:bluetape4k-images-barcode-zxing
  package: io.bluetape4k.images.barcode.zxing
```

Gradle dependencies:

- `api(project(":bluetape4k-images-barcode-api"))`
- `implementation(libs.zxing.core)`
- `implementation(libs.zxing.javase)`
- `testImplementation(libs.bluetape4k.junit5)`
- `testImplementation(libs.kotlinx.coroutines.test)` if suspend entry points
  need direct provider tests.

## Public API

Primary provider:

```kotlin
class ZxingBarcodeReader(
    private val provider: BarcodeProviderIdentity = zxingProviderIdentity(),
) : BarcodeReader {
    override fun readBarcodes(image: ImmutableImage, options: BarcodeOptions): List<BarcodeResult>
}
```

Convenience entry points may be added only when they add provider-specific
failure normalization that the generic API extension cannot see, such as
malformed encoded input handling:

```kotlin
fun ZxingBarcodeReader.readBarcodes(
    bytes: ByteArray,
    options: BarcodeOptions = BarcodeOptions(),
): List<BarcodeResult>
```

## Mapping Rules

- Empty `BarcodeOptions.formats` means all ZXing-supported formats.
- `BarcodeFormat.UNKNOWN` is not passed as a ZXing hint.
- If callers request only formats ZXing cannot support, fail with
  `BarcodeFailureReason.UNSUPPORTED_FORMAT`.
- `NotFoundException` returns `emptyList()` because no-code images are an
  expected negative result.
- `FormatException`, `ChecksumException`, and unexpected runtime decode errors
  become `BarcodeException(DECODE_FAILED, ...)`.
- Malformed encoded bytes become `BarcodeException(MALFORMED_INPUT, ...)` in
  provider-specific byte helpers.
- `Result.resultPoints` become pixel-space `BarcodePoint` values. A bounding
  box is included when the points produce positive width and height.
- `Result.rawBytes` is exposed only when `BarcodeOptions.includeRawBytes` is
  true and ZXing provides non-empty bytes.
- ZXing result metadata is converted to string-only metadata values.

## Testing Strategy

Always-on tests:

- QR image decodes to `BarcodeFormat.QR_CODE`.
- Code 128 image decodes to `BarcodeFormat.CODE_128`.
- Requested format filtering does not return mismatched barcodes.
- No-code image returns an empty list.
- Rotated QR image decodes when `tryHarder = true`.
- Malformed encoded byte helper throws `BarcodeException` with
  `MALFORMED_INPUT`.
- Unsupported requested format set throws `UNSUPPORTED_FORMAT` when no ZXing
  mapping exists.
- Result metadata includes provider identity, backend format, and pixel region
  when ZXing exposes result points.

Tests generate barcode images in-memory with ZXing writers. Do not add
network-fetched fixtures in this issue; #247 owns shared fixture breadth.

## Documentation and Registration

Update:

- `settings.gradle.kts`
- `gradle/libs.versions.toml`
- `AGENTS.md`
- `README.md`
- `README.ko.md`
- `images-barcode-zxing/README.md`
- `images-barcode-zxing/README.ko.md`
- `.github/workflows/ci.yml`
- `.github/workflows/nightly-tests.yml`
- `.github/workflows/release.yml`
- `.github/workflows/publish-snapshot.yml`
- `.github/workflows/Examples.yml`

BOM constraints are automatic for published subprojects because
`bom/build.gradle.kts` iterates published subprojects.

## Risks and Mitigations

- **ZXing maintenance-mode risk**: document it as the first OSS provider, not a
  long-term exclusive dependency.
- **API leakage**: keep ZXing classes internal to the provider package and
  expose only #244 API models.
- **Multi-barcode limitation**: ZXing's simple reader path may decode one
  barcode per image for many formats. Document this provider boundary and leave
  multi-provider/multi-barcode breadth to future issue work if needed.
- **Workflow drift**: add CI/Nightly/release validation in the same PR.
- **Test fragility**: generate deterministic in-memory fixtures with ZXing
  writers instead of depending on external image files.

## Acceptance Criteria

- `:bluetape4k-images-barcode-zxing` exists and builds.
- ZXing dependencies are visible only in the ZXing provider module.
- Unit tests cover QR, Code 128, no-code, rotated QR, malformed input, and
  unsupported format behavior.
- Root/module README locale set documents construction and extension-style use.
- CI/Nightly/release/publish workflow registration includes the new module.
- `./gradlew :bluetape4k-images-barcode-zxing:test`,
  `./gradlew :bluetape4k-images-barcode-zxing:compileTestKotlin --warning-mode all`,
  `./gradlew projects`, `actionlint`, and `git diff --check` pass.
