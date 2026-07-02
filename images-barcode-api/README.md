# bluetape4k-images-barcode-api

English | [한국어](./README.ko.md)

Provider-neutral barcode and QR extraction contracts for `ImmutableImage`.

## Features

- `BarcodeReader` provider contract for blocking barcode extraction.
- `ImmutableImage.extractBarcodes(...)` and `suspendExtractBarcodes(...)`
  extension functions for sync and coroutine callers.
- Input helpers for `ByteArray`, `Path`, `InputStream`, and Okio `Source`.
- Serializable result models: `BarcodeResult`, `BarcodeRegion`,
  `BarcodeBoundingBox`, `BarcodePoint`, and `BarcodeProviderIdentity`.
- No concrete decoder dependency. ZXing, BoofCV, native, or commercial SDK
  adapters live in separate provider modules.

## Architecture

![Barcode API + Provider Architecture](../docs/images/readme-diagrams/images-barcode-api-architecture-01.png)

Solid blue arrows show caller input flow, green arrows show provider modules
implementing the `BarcodeReader` contract, purple arrows show API-side
filtering/normalization, and dashed gray arrows show provider-private decoder
calls.

`images-barcode-api` depends only on `bluetape4k-images` and Kotlin coroutines.
It accepts `ImmutableImage` values, normalizes provider output into bluetape4k
models, and leaves decoder lifecycle, native setup, and provider-specific
configuration to provider modules.

Encoded input helpers first load an `ImmutableImage`, then delegate to the same
`BarcodeReader` contract used by direct image callers.

## Installation

```kotlin
dependencies {
    implementation("io.github.bluetape4k.image:bluetape4k-images-barcode-api:<version>")
}
```

Add one provider module, such as `bluetape4k-images-barcode-zxing`, to actually
decode barcode pixels.

## Usage

```kotlin
import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.images.barcode.BarcodeFormat
import io.bluetape4k.images.barcode.BarcodeOptions
import io.bluetape4k.images.barcode.BarcodeReader
import io.bluetape4k.images.barcode.extractBarcodes
import io.bluetape4k.images.barcode.suspendExtractBarcodes

fun extractCodes(image: ImmutableImage, reader: BarcodeReader) = image.extractBarcodes(
    reader = reader,
    options = BarcodeOptions(
        formats = setOf(BarcodeFormat.QR_CODE, BarcodeFormat.CODE_128),
        tryHarder = true,
        minimumConfidence = 0.80,
    ),
)

suspend fun extractCodesAsync(image: ImmutableImage, reader: BarcodeReader) =
    image.suspendExtractBarcodes(reader)
```

Use `suspendExtractBarcodes` when the caller needs a coroutine dispatcher
boundary.

Provider modules should map backend-specific format labels to `BarcodeFormat`
and keep the raw provider label in `BarcodeResult.rawBackendFormat` when it is
useful for diagnostics.

## Input Helpers

```kotlin
reader.readBarcodes(bytes)
reader.readBarcodes(path)
inputStream.use { reader.readBarcodes(it) }
source.use { reader.readBarcodes(it) }
```

The helpers reuse `immutableImageOf(...)` from `bluetape4k-images`.

## Test Fixtures

The API module publishes provider-neutral test fixtures through
`testFixtures(project(":bluetape4k-images-barcode-api"))`.

```kotlin
import io.bluetape4k.images.barcode.testfixtures.BarcodeTestFixtures

val blank = BarcodeTestFixtures.blankImage()
val rotated = BarcodeTestFixtures.rotateClockwise(blank)
val malformed = BarcodeTestFixtures.malformedImageBytes
```

These fixtures are generated from deterministic code at test runtime. No
external barcode image assets are bundled, and
`BarcodeTestFixtures.GENERATED_SOURCE_NOTE` records the source/licensing note
for provider capability documentation.

## Tests

```bash
./gradlew :bluetape4k-images-barcode-api:test
```

The tests are pure JVM and always-on. They cover model validation,
serialization, sync/suspend delegation, cancellation, and input helper
decoding. They also verify the shared fixture helper behavior.
