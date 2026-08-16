---
manualId: "spring-boot-image-intelligence-api"
id: "spring-boot-image-intelligence-api"
title: "Spring Boot image intelligence API workshop"
locale: "en"
kind: "example"
gradlePath: ":spring-boot-image-intelligence-api"
sourceDir: "examples/spring-boot-image-intelligence-api"
releaseRef: "0.4.0"
artifact: null
---

# Spring Boot image intelligence API workshop

> Runnable example

## Problem {#problem}

This Spring Boot 4 example validates and decodes one uploaded image once, then
fans the immutable value out to OCR, object detection, and barcode/QR analysis.
It demonstrates shared qualification, isolated provider execution, partial
results, and a replaceable business policy without choosing a particular ML
model. A visitor pass is the reference scenario: text, a face region, and a
`visitor:` QR value are facts that a policy can evaluate together.

## When to use it {#when-to-use}

Use this workshop when several image analyses must share one guarded decode and
still remain independently observable. It is useful for shipping labels,
product labels, and intake documents as well as visitor passes. It is a
teaching boundary, not a complete service: authentication, retention, malware
scanning, and production model governance remain application responsibilities.

## Coordinates {#coordinates}

The workshop is not published and has no Maven coordinate. Select one
`bluetape4k-dependencies` version for published image modules; run the example
from this repository with `:spring-boot-image-intelligence-api`.

## Core concepts {#concepts}

- Qualification checks the declared media type, compressed size, signature,
  dimensions, and decoded pixel budget before the single `ImmutableImage`
  decode.
- `ImageIntelligenceWorkflow` runs OCR, detection, and ZXing lanes through
  `suspendParallelFlow`; each lane writes a distinct `WorkContext` key.
- `AnalysisResult` distinguishes `Completed`, `Empty`, `Unavailable`, and
  `Failed`. A provider failure is domain data and does not discard successful
  siblings.
- The aggregate is `COMPLETED` when every lane is available or empty, `PARTIAL`
  when some results coexist with unavailable/failed lanes, and `FAILED` when no
  lane produced an available result.
- `VisitorPassPolicy` consumes provider-neutral facts. Replace that policy for a
  different domain without rewriting qualification or orchestration.

## Quick start {#quick-start}

The default profile needs no external OCR or ML service. Start it with:

```bash
./gradlew :spring-boot-image-intelligence-api:bootRun
```

For deterministic OCR and detection fixtures while keeping real ZXing decoding,
use the demo profile:

```bash
./gradlew :spring-boot-image-intelligence-api:bootRun \
  --args='--spring.profiles.active=demo'
```

Then submit a PNG, JPEG, or WebP image:

```bash
curl -X POST \
  -F "file=@visitor-pass.png;type=image/png" \
  http://localhost:8080/api/images/intelligence
```

The optional `native-ocr` profile requires host Tesseract and traineddata. Do
not activate it together with `demo`; provider ownership is deliberately
exclusive.

## API by task {#api-by-task}

| Task | Example boundary |
| --- | --- |
| Submit an image | `POST /api/images/intelligence`, multipart field `file` |
| Guard input | `ImageUploadQualifier` checks type, bytes, signature, dimensions, and pixels |
| Run analyses | `ImageIntelligenceWorkflow` coordinates OCR, detection, and ZXing |
| Preserve partial results | `AnalysisResult` plus `ImageIntelligenceAggregator` |
| Apply business rules | `VisitorPassPolicy` maps facts to `ALLOW` or `MANUAL_REVIEW` |

## Recommended patterns {#patterns}

Decode once and share an immutable image; never let each provider reopen the
upload. Keep provider timeouts and semaphores separate, and treat
`Empty` (“ran and found nothing”) differently from `Failed` (“could not
verify”). Propagate external `CancellationException` instead of turning it
into a business failure. A native call that ignores interruption may still hold
its thread until it returns, so a production deployment can add process
isolation and a process-level timeout.

## Integrations {#integrations}

The example combines `bluetape4k-images` decoding, the OCR contract, the
provider-neutral barcode API, and the ZXing provider with Spring Boot 4 and
`bluetape4k-workflow`. It intentionally keeps the detection adapter local so a
real model can be introduced without changing the HTTP or aggregate contract.

## Configuration {#configuration}

The release defaults are five MiB compressed input, 8,192 pixels per side,
16,777,216 decoded pixels, and independent provider timeouts/concurrency:

```yaml
example:
  image-intelligence:
    max-input-bytes: 5242880
    max-input-pixels: 16777216
    max-input-side: 8192
    ocr-timeout: 3s
    detection-timeout: 2s
    barcode-timeout: 2s
    ocr-concurrency: 1
    detection-concurrency: 2
    barcode-concurrency: 4
    tessdata-path: null
```

Keep host paths such as `tessdata-path` in application configuration. Never
accept them from an upload request.

## Failure modes {#failures}

- `400` indicates an empty upload, unsupported media type, signature mismatch,
  malformed image, or a dimension/pixel qualification failure.
- `413` indicates that the compressed input limit was exceeded.
- A provider-level timeout or unavailable native dependency is represented in
  the response as `Failed` or `Unavailable`; successful sibling results remain.
- Missing workflow keys or unexpected programming errors are workflow failures,
  not normal provider results.

Responses expose a stable status/reason code and do not log or return the
uploaded payload. Inspect provider configuration and sanitized application logs
when a lane is unavailable; do not expose tessdata paths or native exception
details to callers.

## Operations {#operations}

Add authentication, authorization, tenant quotas, rate limiting, request
timeouts, antivirus/content-disarm scanning, retention/deletion, encryption,
and audit policy before exposing this endpoint to untrusted traffic. Size and
pixel limits protect decode memory, while per-provider semaphores protect CPU
and native resources. Measure provider latency and partial-result rates rather
than treating `PARTIAL` as an infrastructure crash.

## Testing {#testing}

Run the workshop tests with:

```bash
./gradlew :spring-boot-image-intelligence-api:test
```

The release suite covers real ZXing extraction from a generated QR,
qualification boundaries, profile ownership, parallel overlap, partial
failures, workflow keys, the policy decision table, external cancellation,
permit recovery, payload-free logs, and the HTTP error contract. Native OCR
smoke testing remains environment-dependent.

## Workshops and learning path {#workshops}

1. Read the immutable-image and OCR module guides, then run this workshop's
   fake-provider tests.
2. Run the `demo` profile to observe a complete aggregate without native OCR.
3. Compare the Ktor and Spring OCR workshops to separate transport concerns
   from provider orchestration.
4. Replace `VisitorPassPolicy` with a shipping-label or product-label policy
   and retain the same qualification and partial-result boundaries.

## Limitations {#limitations}

The example does not provide authentication, persistence, queues, batch
processing, preprocessing policy, malware scanning, tenant isolation, retry or
circuit-breaker policy, model selection, quality measurement, or drift
monitoring. Coroutine cancellation cannot forcibly terminate a non-cooperative
native function; use process isolation when a hard native execution bound is a
requirement.

## Sources {#sources}

- [Release README](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/examples/spring-boot-image-intelligence-api/README.md)
- [Application configuration](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/config/ImageIntelligenceConfiguration.kt)
- [Upload qualification](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/ImageUploadQualifier.kt)
- [Workflow orchestration](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/ImageIntelligenceWorkflow.kt)
- [Provider adapters](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/ImageAnalysisProviders.kt)
- [Policy](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/examples/spring-boot-image-intelligence-api/src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/VisitorPassPolicy.kt)
- [HTTP integration test](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/examples/spring-boot-image-intelligence-api/src/test/kotlin/io/bluetape4k/images/examples/spring/intelligence/web/ImageIntelligenceControllerTest.kt)
- [Gradle build file](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/examples/spring-boot-image-intelligence-api/build.gradle.kts)
