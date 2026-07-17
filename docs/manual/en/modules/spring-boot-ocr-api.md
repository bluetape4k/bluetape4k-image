---
manualId: "spring-boot-ocr-api"
id: "spring-boot-ocr-api"
title: "Spring Boot OCR API workshop"
locale: "en"
kind: "example"
gradlePath: ":spring-boot-ocr-api"
sourceDir: "examples/spring-boot-ocr-api"
releaseRef: "0.3.0"
artifact: null
---

# Spring Boot OCR API workshop

> Runnable example

## Problem {#problem}

This Spring Boot 4 workshop exposes `bluetape4k-images-ocr` through a multipart endpoint. It parses language codes, passes host tessdata configuration to an injectable `OcrEngine`, and separates invalid requests from native OCR availability failures.

## When to use it {#when-to-use}

Use it when introducing OCR into a Spring MVC application or when you need a testable boundary around Tess4J/Tesseract. It is a focused endpoint example, not a document-processing platform.

## Coordinates {#coordinates}

The example itself is not published. Choose one `bluetape4k-dependencies` version and consume `bluetape4k-images-ocr`; keep the OCR module aligned through that central version rather than pinning it independently.

## Core concepts {#concepts}

- `OcrEngine` is injected; `TesseractOcrEngine` is only the production default.
- `eng+kor` and `eng,kor` become `listOf("eng", "kor")`.
- `example.ocr.tessdata-path` is host configuration and is not accepted from each request.
- `IllegalArgumentException` maps to `400`; `OcrException` maps to `503`.

## Quick start {#quick-start}

Tests require JDK 21+ only. Real OCR requires Tesseract and matching traineddata.

```bash
brew install tesseract tesseract-lang
tesseract --list-langs
./gradlew :spring-boot-ocr-api:bootRun
```

```bash
curl -F "file=@sample-ko.png;type=image/png" \
  "http://localhost:8080/api/ocr?languages=eng+kor"
```

Configure a non-default traineddata directory with `example.ocr.tessdata-path`.

## API by task {#api-by-task}

| Task | Release API |
| --- | --- |
| Submit OCR | `POST /api/ocr` multipart field `file` |
| Select languages | query parameter `languages` |
| Configure native data | `ExampleOcrProperties.tessdataPath` |
| Run recognition | `ImmutableImage.suspendExtractText(options, engine)` |
| Report result | `OcrTextResponse(text, languages, characterCount)` |

## Recommended patterns {#patterns}

Inject the native engine, keep host paths in configuration, validate content type before decoding, and give native-unavailable failures a different status from client errors. Fake the engine in controller tests; reserve host OCR smoke tests for a compatible environment.

## Integrations {#integrations}

The workshop uses [`bluetape4k-images-ocr`](./bluetape4k-images-ocr.md) and core image decoding. Pair it with [`spring-boot-image-api`](./spring-boot-image-api.md) only after deciding whether OCR runs before or after durable storage.

## Configuration {#configuration}

Supported request media types are JPEG, PNG, WebP, and GIF. The language default is `eng`. Set `example.ocr.tessdata-path` when Tesseract cannot find traineddata through its host defaults or `TESSDATA_PREFIX`.

## Failure modes {#failures}

- `400 bad_request`: empty upload, missing content type, unsupported media type, or malformed languages.
- `503 ocr_unavailable`: Tesseract/native bridge or requested traineddata is unavailable.
- Incorrect text: verify installed languages, input quality, orientation, and preprocessing; successful HTTP execution does not guarantee OCR accuracy.

## Operations {#operations}

Add request-size limits, timeouts, concurrency controls, authentication, and rate limiting in the owning service. Avoid logging source documents or full recognized text unless the data policy explicitly permits it.

## Testing {#testing}

```bash
./gradlew :spring-boot-ocr-api:test
```

MockMvc and a fake `OcrEngine` verify multipart success, `eng+kor` parsing, tessdata propagation, unsupported media rejection, and `503` failure mapping without host Tesseract.

## Workshops and learning path {#workshops}

1. Read the OCR module manual and run this workshop's fake-engine tests.
2. Install Tesseract and confirm language packages with `tesseract --list-langs`.
3. Exercise a real text-heavy image and observe quality separately from API correctness.
4. Compare [`ktor-ocr-api`](./ktor-ocr-api.md) to see the same domain boundary with explicit multipart streaming.

## Limitations {#limitations}

Authentication, queues, persistence, batch OCR, preprocessing policy, and rate limiting are outside this quickstart. OCR quality and language coverage are host-dependent.

<!-- release-readme-diagrams:start -->
## Release diagrams {#release-diagrams}

These diagrams are copied byte-for-byte from README assets in the `0.3.0` release tag. They describe this manual's released structure and runtime flows, not later Snapshot changes. Select a preview to open the SVG source.

### Spring Boot OCR API Architecture

[![Spring Boot OCR API Architecture](../../assets/readme-diagrams/examples-spring-boot-ocr-api-architecture-01.png)](../../assets/readme-diagrams/examples-spring-boot-ocr-api-architecture-01.svg)

_Release README: [`examples/spring-boot-ocr-api/README.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/examples/spring-boot-ocr-api/README.md)_

### Spring Boot OCR API Scenario

[![Spring Boot OCR API Scenario](../../assets/readme-diagrams/examples-spring-boot-ocr-api-scenario-01.png)](../../assets/readme-diagrams/examples-spring-boot-ocr-api-scenario-01.svg)

_Release README: [`examples/spring-boot-ocr-api/README.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/examples/spring-boot-ocr-api/README.md)_

### Spring Boot OCR API Sequence

[![Spring Boot OCR API Sequence](../../assets/readme-diagrams/examples-spring-boot-ocr-api-sequence-01.png)](../../assets/readme-diagrams/examples-spring-boot-ocr-api-sequence-01.svg)

_Release README: [`examples/spring-boot-ocr-api/README.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/examples/spring-boot-ocr-api/README.md)_

<!-- release-readme-diagrams:end -->

## Sources {#sources}

- [Release README](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/examples/spring-boot-ocr-api/README.md)
- [Application source](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/examples/spring-boot-ocr-api/src/main/kotlin/io/bluetape4k/images/examples/spring/ocr/SpringBootOcrApiApplication.kt)
- [Integration test](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/examples/spring-boot-ocr-api/src/test/kotlin/io/bluetape4k/images/examples/spring/ocr/SpringBootOcrApiApplicationTest.kt)
- [Gradle build file](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/examples/spring-boot-ocr-api/build.gradle.kts)
