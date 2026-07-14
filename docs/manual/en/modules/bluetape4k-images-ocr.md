---
manualId: "bluetape4k-images-ocr"
id: "bluetape4k-images-ocr"
title: "Tesseract OCR integration"
locale: "en"
kind: "library"
gradlePath: ":bluetape4k-images-ocr"
sourceDir: "images-ocr"
releaseRef: "0.3.0"
artifact: io.github.bluetape4k.image:bluetape4k-images-ocr
---

# Tesseract OCR integration

> Library module

## Problem {#problem}

This module extracts text from an existing Scrimage `ImmutableImage` through Tess4J and the host Tesseract runtime. It gives Kotlin applications stable options and exception types without pretending that OCR is a pure-JVM operation.

## When to use it {#when-to-use}

Use it when an image is already decoded by `bluetape4k-images` and the service needs English or multilingual text extraction. Do not add it for barcode recognition or when a managed OCR service is the intended operational model.

## Coordinates {#coordinates}

Maven coordinate: `io.github.bluetape4k.image:bluetape4k-images-ocr`

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<version>"))
    implementation("io.github.bluetape4k.image:bluetape4k-images-ocr")
}
```

## Core concepts {#concepts}

`OcrEngine` is the provider boundary. `TesseractOcrEngine` creates a fresh Tess4J client for every recognition call, so mutable native OCR state is not shared. `OcrOptions.languages` becomes a `+`-joined Tesseract expression, and `OcrResult` retains the effective options.

## Quick start {#quick-start}

```kotlin
val image = immutableImageOf(Path.of("receipt.png"))
val text = image.suspendExtractText(
    OcrOptions(
        languages = listOf("eng", "kor"),
        pageSegmentationMode = TesseractPageSegmentationMode.AUTO,
    ),
)
```

## API by task {#api-by-task}

- Blocking extraction: `ImmutableImage.extractText`.
- Coroutine boundary: `suspendExtractText`, which runs the blocking call on `Dispatchers.IO` by default.
- Language packs: configure `languages` and optionally `tessdataPath`.
- Engine tuning: select engine/page segmentation modes, configs, and variables.
- Alternative provider: implement `OcrEngine` and pass it to either extension.

## Recommended patterns {#patterns}

Install the exact language packs used in production and verify them during image build or startup. Use explicit page segmentation for constrained documents. Keep OCR behind a bounded worker pool; `suspendExtractText` moves blocking work but does not make Tesseract cancellable mid-call.

## Integrations {#integrations}

Inputs are `ImmutableImage` values from `bluetape4k-images`. Ktor and Spring examples can expose OCR at the service boundary, but this library does not register routes or Spring beans itself.

## Configuration {#configuration}

Install Tesseract and requested traineddata packages on the host. If the default lookup is unsuitable, set `TESSDATA_PREFIX` or `OcrOptions.tessdataPath`. Defaults are language `eng`, default engine mode, automatic page segmentation, and trimmed output.

## Failure modes {#failures}

Missing native libraries, Tess4J classes, or traineddata setup surface as `OcrConfigurationException`. Recognition failures surface as `OcrException`. The message contains language and tessdata guidance; keep the original cause in server logs rather than returning it to clients.

## Operations {#operations}

Track recognition latency, empty-result rate, configured languages, and queue saturation. Do not record recognized text by default. Package Tesseract and traineddata into the same immutable deployment image used in production.

## Testing {#testing}

Unit tests use a fake Tess4J client. Host-native checks require `-Docr.enabled=true`; container checks require Docker and `-Docr.container.enabled=true`. The release native baseline verifies `eng`, `kor`, and `jpn`. Run host and container native checks sequentially.

## Workshops and learning path {#workshops}

First exercise `OcrQuickstartExampleTest`, then tune page segmentation on representative documents, and finally add the gated native/container checks to the deployment pipeline.

## Limitations {#limitations}

The module does not bundle Tesseract or language data. Coroutine cancellation before dispatch prevents startup, but an active native OCR call is blocking. Recognition quality is input- and language-pack-dependent.

## Sources {#sources}

- [OCR contract and exceptions](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/OcrEngine.kt)
- [OCR options](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/OcrOptions.kt)
- [Tesseract engine](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/TesseractOcrEngine.kt)
- [Release build and test gates](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-ocr/build.gradle.kts)
