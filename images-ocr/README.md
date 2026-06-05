# bluetape4k-images-ocr

English | [한국어](./README.ko.md)

Tess4J/Tesseract OCR extensions for `ImmutableImage`.

## Features

- `ImmutableImage.extractText(...)` for blocking OCR.
- `ImmutableImage.suspendExtractText(...)` for coroutine callers; blocking OCR
  runs on `Dispatchers.IO` by default.
- `OcrOptions` for languages, `tessdataPath`, engine mode, page segmentation
  mode, variables, and configs.
- `TesseractOcrEngine` creates a fresh Tess4J instance per call so mutable OCR
  state is not shared across callers.

## Architecture

![images-ocr Architecture](docs/assets/readme-diagrams/images-ocr-architecture-01.png)

## Class Diagram

![images-ocr Class Diagram](docs/assets/readme-diagrams/images-ocr-class-diagram-01.png)

## Sequence Diagram

![images-ocr Recognition Sequence](docs/assets/readme-diagrams/images-ocr-sequence-diagram-01.png)

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

## Installation

```kotlin
dependencies {
    implementation("io.github.bluetape4k.image:bluetape4k-images-ocr:<version>")
}
```

## Usage

```kotlin
import io.bluetape4k.images.immutableImageOf
import io.bluetape4k.images.ocr.OcrOptions
import io.bluetape4k.images.ocr.TesseractPageSegmentationMode
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

Always-on tests validate options, enum mappings, serialization, engine
delegation, per-call Tess4J configuration, sanitized exceptions, and coroutine
cancellation:

```bash
./gradlew :bluetape4k-images-ocr:test
```

Native OCR tests are gated because they need host Tesseract and language packs:

```bash
./gradlew :bluetape4k-images-ocr:test -Docr.enabled=true
```

Container smoke tests are gated because they need Docker:

```bash
./gradlew :bluetape4k-images-ocr:test -Docr.container.enabled=true
```
