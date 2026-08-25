# bluetape4k-images-ocr

English | [한국어](./README.ko.md)

Tess4J/Tesseract OCR extensions for `ImmutableImage`.

## Features

- `ImmutableImage.extractText(...)` for blocking OCR.
- `ImmutableImage.suspendExtractText(...)` for coroutine callers; blocking OCR
  runs on `Dispatchers.IO` by default.
- `ImmutableImage.extractOcr(...)` and `suspendExtractOcr(...)` for structured
  page, block, line, and word metadata when the engine can provide it.
- `OcrOptions` for languages, `tessdataPath`, engine mode, page segmentation
  mode, variables, configs, structured detail, and source regions.
- `TesseractOcrEngine` creates a fresh Tess4J instance per call so mutable OCR
  state is not shared across callers.

## Architecture

![images-ocr Architecture](../docs/images/readme-diagrams/images-ocr-architecture-01.png)

## Class Diagram

![images-ocr Class Diagram](../docs/images/readme-diagrams/images-ocr-class-diagram-01.png)

## Sequence Diagram

![images-ocr Recognition Sequence](../docs/images/readme-diagrams/images-ocr-sequence-diagram-01.png)

## Runtime Requirements

Install Tesseract and the traineddata packages you request.

```bash
# macOS / Homebrew
brew install tesseract tesseract-lang

# Ubuntu / Debian
sudo apt-get install tesseract-ocr tesseract-ocr-eng tesseract-ocr-kor tesseract-ocr-jpn fonts-noto-cjk

tesseract --list-langs
```

Set `TESSDATA_PREFIX` or pass `OcrOptions(tessdataPath = "...")` when the
runtime cannot find traineddata files.

Host-native OCR uses Tess4J/Lept4J through JNA, so the JVM loads the host
Leptonica and Tesseract shared libraries. The current runtime is validated
locally on Homebrew Tesseract 5.5 / Leptonica 1.87. Ubuntu 24.04 packages
provide Leptonica 1.82, which does not satisfy the native symbol set expected by
the current Lept4J/Tess4J line. For that reason GitHub CI and Nightly run the
portable container-backed OCR gate, while `-Docr.enabled=true` remains a
local/manual host-native check.

## Installation

```kotlin
dependencies {
    implementation("io.github.bluetape4k.image:bluetape4k-images-ocr:<version>")
}
```

## Usage

```kotlin
import io.bluetape4k.images.immutableImageOf
import io.bluetape4k.images.ocr.OcrBoundingBox
import io.bluetape4k.images.ocr.OcrOptions
import io.bluetape4k.images.ocr.OcrRegion
import io.bluetape4k.images.ocr.OcrStructuredDetail
import io.bluetape4k.images.ocr.TesseractPageSegmentationMode
import io.bluetape4k.images.ocr.extractOcr
import io.bluetape4k.images.ocr.extractText
import io.bluetape4k.images.ocr.suspendExtractText
import java.io.File

val image = immutableImageOf(File("receipt.png"))

val text = image.extractText(
    OcrOptions(languages = listOf("eng", "kor")),
)

val suspendText = image.suspendExtractText(
    OcrOptions(
        languages = listOf("eng"),
        pageSegmentationMode = TesseractPageSegmentationMode.SINGLE_LINE,
    ),
)

val structured = image.extractOcr(
    OcrOptions(
        languages = listOf("eng"),
        structuredDetail = OcrStructuredDetail.WORD,
        regions = listOf(
            OcrRegion(
                boundingBox = OcrBoundingBox(x = 0, y = 0, width = 640, height = 180),
                id = "header",
            ),
        ),
    ),
)

val words = structured.words.map { word ->
    "${word.text}:${word.confidence ?: "unknown"}"
}
```

Use `variables` for Tesseract tuning:

```kotlin
val digits = image.extractText(
    OcrOptions(
        languages = listOf("eng"),
        variables = mapOf("tessedit_char_whitelist" to "0123456789"),
    ),
)
```

## Structured Results

`OcrStructuredResult` keeps `text` source-compatible with plain extraction and
adds `pages`, `blocks`, `lines`, and `words` lists. Detail is opt-in:

- `OcrStructuredDetail.PLAIN_TEXT`: plain text plus page metadata only.
- `OcrStructuredDetail.LINE`: block and line entries when available.
- `OcrStructuredDetail.WORD`: block, line, and word entries when available.

Bounding boxes and confidence scores are nullable. If Tesseract does not return
valid geometry or confidence for an entry, the field stays `null`; the module
does not invent placeholder values. Region-limited extraction uses
`OcrRegion` metadata and copies the matching region to structured entries whose
bounding boxes intersect it.

Advanced document OCR backends such as PaddleOCR/GPU/model-download pipelines
remain out of this module and are tracked separately by issue #169.

## Bounded Multi-page TIFF OCR

`TiffMultiPageOcr` accepts an encoded TIFF `ByteArray`, validates every page
before the first decode or engine call, and then processes pages sequentially
with one ImageIO reader. The default limits are deliberately bounded:

```kotlin
val result = TiffMultiPageOcr()
    .recognize(
        tiffBytes,
        options = OcrOptions(languages = listOf("eng")),
        limits = TiffMultiPageOcrLimits(
            maxPages = 16,
            maxTotalPixels = 64_000_000L,
            maxResultTextChars = 1_000_000,
            maxResultEntries = 100_000,
        ),
    )
```

The aggregate text follows input page order and uses `\n\n` between pages. Every
page, block, line, and word entry is remapped to its TIFF `pageIndex`; no
partial aggregate is returned after a failure. `maxEncodedBytes`,
`maxMetadataBytes`, and page/side/pixel limits are checked fail-closed before
decode; cumulative text/entry budgets are checked before each page is appended
to the public aggregate.

Input and metadata rejections are reported as
`TiffMultiPageOcrValidationException`; decode, provider, engine, and unexpected
operational failures use `TiffMultiPageOcrException`. Handle the stable
`TiffMultiPageOcrFailureReason` values rather than matching exception text. The
mapped message includes the failure phase (`input`, `reader`, `metadata`,
`decode`, `engine`, `result`, or `unknown`) and page index when available.
Mapped messages do not expose payload bytes, file paths, or tessdata paths; the
original failure remains available through `Throwable.cause` for trusted
diagnostics and must not be sent directly to untrusted clients. Unexpected
failures use `UNKNOWN`. `suspendRecognize` runs blocking work on the supplied
dispatcher and rethrows `CancellationException`; native cancellation is
best-effort, so callers should apply a timeout for untrusted input.

This API intentionally covers multi-page TIFF only. GIF animation frames,
parallel page OCR, and `Path`/`InputStream` overloads are not included. Existing
single-image `extractText` and `extractOcr` callers are unchanged. A caller with
a file or stream should perform a bounded read using the same encoded-byte
budget before calling this `ByteArray` entry point, then map stable reasons to its
retry or HTTP policy.

## Runnable Quickstart

The module includes a file-based quickstart test that creates a small image,
loads it through `immutableImageOf(File)`, and runs OCR with explicit language
and page-segmentation options. Set `TESSDATA_PREFIX` when Tess4J cannot locate
traineddata files automatically.

```bash
export TESSDATA_PREFIX="$(brew --prefix)/share/tessdata"  # macOS / Homebrew
./gradlew :bluetape4k-images-ocr:test \
  --tests 'io.bluetape4k.images.ocr.OcrQuickstartExampleTest' \
  -Docr.enabled=true
```

Expected output shape:

```text
BLUETAPE OCR 123
```

The quickstart uses `eng`. On macOS, `brew install tesseract tesseract-lang`
installs the Tesseract engine plus the bundled language pack set. On Ubuntu,
install each requested traineddata package explicitly, such as
`tesseract-ocr-eng`, `tesseract-ocr-kor`, and `tesseract-ocr-jpn`. The test also
checks common Homebrew and Ubuntu tessdata paths when `TESSDATA_PREFIX` is not
set.

## Tests

Always-on tests validate options, enum mappings, serialization, structured
result modeling, engine delegation, per-call Tess4J configuration, region
filtering, sanitized exceptions, and coroutine cancellation:

```bash
./gradlew :bluetape4k-images-ocr:test
```

Native OCR tests are gated because they need host Tesseract, language packs, and
a Leptonica runtime compatible with the Tess4J/Lept4J version on the classpath:

```bash
./gradlew :bluetape4k-images-ocr:test -Docr.enabled=true
```

Container smoke tests are gated because they need Docker. This is the OCR gate
used by GitHub CI and Nightly:

```bash
./gradlew :bluetape4k-images-ocr:test -Docr.container.enabled=true
```

The gated `TiffMultiPageTesseractContainerOcrTest` writes each decoded page to a
temporary PNG, sends it to the container `tesseract` CLI, and verifies page order
and the aggregate separator. Host-native release checks remain explicit:

```bash
./gradlew :bluetape4k-images-ocr:test -Docr.enabled=true --no-daemon
```

The test launcher starts one non-reusable Tesseract container per module test
JVM. Developers may explicitly opt into local reuse by enabling reusable
containers in `~/.testcontainers.properties` and passing
`-Docr.container.reuse=true`. The opt-in is ignored when `CI=true`; tests and
examples never enable reuse implicitly.
