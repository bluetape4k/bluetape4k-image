# bluetape4k-images-barcode-zxing

English | [한국어](./README.ko.md)

Pure-JVM ZXing provider for the provider-neutral bluetape4k barcode API.

## Features

- Implements `BarcodeReader` from `bluetape4k-images-barcode-api`.
- Decodes QR Code and common 1D formats such as Code 128 through ZXing.
- Maps ZXing text, backend format, result points, bounding box, raw bytes, and
  metadata into `BarcodeResult`.
- Returns an empty list for no-code images.
- Keeps ZXing classes out of public method signatures.

## Installation

```kotlin
dependencies {
    implementation("io.github.bluetape4k.image:bluetape4k-images-barcode-api:<version>")
    implementation("io.github.bluetape4k.image:bluetape4k-images-barcode-zxing:<version>")
}
```

## Usage

```kotlin
import io.bluetape4k.images.barcode.BarcodeFormat
import io.bluetape4k.images.barcode.BarcodeOptions
import io.bluetape4k.images.barcode.extractBarcodes
import io.bluetape4k.images.barcode.zxing.ZxingBarcodeReader

val reader = ZxingBarcodeReader()
val results = image.extractBarcodes(
    reader = reader,
    options = BarcodeOptions(
        formats = setOf(BarcodeFormat.QR_CODE, BarcodeFormat.CODE_128),
        tryHarder = true,
    ),
)
```

For encoded bytes, use the provider helper when malformed input should be
reported as a barcode failure:

```kotlin
val results = ZxingBarcodeReader().readBarcodes(bytes)
```

## Provider Boundary

ZXing is Apache-2.0 and pure JVM, so it is the default OSS provider path for
services that cannot install native barcode libraries. Treat it as a provider,
not as the public barcode API: callers should depend on `BarcodeReader` and
`BarcodeResult`, while ZXing-specific classes stay inside this module.

The simple ZXing reader path commonly returns one decoded barcode per image. If
your workload needs broader multi-barcode detection, compare additional
providers before standardizing on ZXing alone.

## Tests

```bash
./gradlew :bluetape4k-images-barcode-zxing:test
```

Tests generate QR and Code 128 images in memory with ZXing writers. No external
image fixtures are required.
