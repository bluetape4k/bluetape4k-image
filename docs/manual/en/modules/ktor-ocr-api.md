---
manualId: "ktor-ocr-api"
id: "ktor-ocr-api"
title: "Ktor OCR API workshop"
locale: "en"
kind: "example"
gradlePath: ":ktor-ocr-api"
sourceDir: "examples/ktor-ocr-api"
releaseRef: "0.4.0"
artifact: null
---

# Ktor OCR API workshop

> Runnable example

## Problem {#problem}

This Ktor 3 workshop accepts a streamed multipart image, parses Tesseract language codes, calls an injectable `OcrEngine`, and maps validation and native-runtime failures to stable JSON responses. Its tests run without a host Tesseract installation.

## When to use it {#when-to-use}

Use it when adding OCR to a Ktor service, learning how to isolate native dependencies behind an interface, or comparing Ktor wiring with the Spring Boot OCR workshop.

## Coordinates {#coordinates}

The example is not published. Select one `bluetape4k-dependencies` version for the application and consume `bluetape4k-images-ocr`; do not manage an independent OCR-module version.

## Core concepts {#concepts}

- Multipart input is streamed and limited by `maxInputBytes` before decoding.
- `eng+kor`, `eng,kor`, and whitespace-separated values become a language list.
- `KtorOcrService` depends on `OcrEngine`; production uses `TesseractOcrEngine`, tests inject a fake.
- Invalid requests become `400 bad_request`; `OcrException` becomes `503 ocr_unavailable`.

## Quick start {#quick-start}

JDK 25+ is required for tests. Real OCR also requires Tesseract and the requested traineddata packages.

```bash
brew install tesseract tesseract-lang
export EXAMPLE_OCR_TESSDATA_PATH=/opt/homebrew/share/tessdata
./gradlew :ktor-ocr-api:run
```

```bash
curl -F "file=@sample-ko.png;type=image/png" \
  "http://localhost:8080/api/ocr?languages=eng+kor"
```

The response contains `text`, normalized `languages`, and `characterCount`.

## API by task {#api-by-task}

| Task | Release API |
| --- | --- |
| Configure route/runtime | `KtorOcrApiConfig` |
| Install endpoint | `configureKtorOcrApi(config, ocrEngine)` |
| Decode and recognize | `immutableImageOf(bytes).suspendExtractText(...)` |
| Health check | `GET /ready` |
| OCR request | `POST /api/ocr?languages=eng+kor` |

## Recommended patterns {#patterns}

Keep host-specific tessdata configuration outside the request, inject the OCR engine, cap bytes before image decoding, and map native availability separately from client validation. This keeps normal CI independent of Tesseract while preserving the production contract.

## Integrations {#integrations}

The workshop uses [`bluetape4k-images-ocr`](./bluetape4k-images-ocr.md) and core image decoding. Compare [`spring-boot-ocr-api`](./spring-boot-ocr-api.md) to choose framework wiring without changing the OCR domain boundary.

## Configuration {#configuration}

Defaults: route `/api/ocr`, multipart field `file`, 10 MiB maximum, language `eng`, port `8080`. `PORT` changes the port; `EXAMPLE_OCR_TESSDATA_PATH` supplies the host traineddata directory.

## Failure modes {#failures}

- `400 bad_request`: missing/wrong multipart field, empty upload, unsupported media type, invalid language list, or input over 10 MiB.
- `503 ocr_unavailable`: Tesseract, a traineddata package, or the native bridge is unavailable.
- Empty/poor recognition: verify `tesseract --list-langs`, input resolution, contrast, orientation, and the requested language set.

## Operations {#operations}

Treat OCR as a bounded native service call. Add authentication, rate limiting, request queues, timeouts, and concurrency limits in the owning application. Log language and duration, not uploaded document contents.

## Testing {#testing}

```bash
./gradlew :ktor-ocr-api:test
```

Ktor `testApplication` verifies language parsing and tessdata propagation, wrong fields, unsupported content types, and the `503` native-failure mapping using a fake engine.

## Workshops and learning path {#workshops}

1. Learn multipart route conventions in [`ktor-image-api`](./ktor-image-api.md).
2. Run this workshop's fake-engine tests before installing native OCR.
3. Install Tesseract, verify languages, then send a real text-heavy image.
4. Read the OCR module manual and compare the Spring Boot workshop.

## Limitations {#limitations}

The workshop does not include authentication, persistence, batch OCR, preprocessing policy, queues, or distributed admission control. OCR quality and supported languages depend on the host installation.

<!-- release-readme-diagrams:start -->
## Release diagrams {#release-diagrams}

These diagrams are loaded directly from README assets published with the `0.4.0` release and pinned to its immutable commit. They describe this manual's released structure and runtime flows, not later Snapshot changes. Select a preview to open the SVG at the same release commit.

### Ktor OCR API Architecture

[![Ktor OCR API Architecture](https://raw.githubusercontent.com/bluetape4k/bluetape4k-image/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/examples-ktor-ocr-api-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/examples-ktor-ocr-api-architecture-01.svg)

_Release README: [`examples/ktor-ocr-api/README.md`](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/examples/ktor-ocr-api/README.md)_

### Ktor OCR API Scenario

[![Ktor OCR API Scenario](https://raw.githubusercontent.com/bluetape4k/bluetape4k-image/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/examples-ktor-ocr-api-scenario-01.png)](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/examples-ktor-ocr-api-scenario-01.svg)

_Release README: [`examples/ktor-ocr-api/README.md`](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/examples/ktor-ocr-api/README.md)_

### Ktor OCR API Sequence

[![Ktor OCR API Sequence](https://raw.githubusercontent.com/bluetape4k/bluetape4k-image/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/examples-ktor-ocr-api-sequence-01.png)](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/examples-ktor-ocr-api-sequence-01.svg)

_Release README: [`examples/ktor-ocr-api/README.md`](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/examples/ktor-ocr-api/README.md)_

<!-- release-readme-diagrams:end -->

## Sources {#sources}

- [Release README](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/examples/ktor-ocr-api/README.md)
- [Application source](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/examples/ktor-ocr-api/src/main/kotlin/io/bluetape4k/images/examples/ktor/ocr/KtorOcrApiApplication.kt)
- [Route tests](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/examples/ktor-ocr-api/src/test/kotlin/io/bluetape4k/images/examples/ktor/ocr/KtorOcrApiApplicationTest.kt)
- [Gradle build file](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/examples/ktor-ocr-api/build.gradle.kts)
