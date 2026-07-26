# Spring Boot Image Intelligence API

English | [한국어](./README.ko.md)

This Spring Boot 4 example qualifies and decodes one uploaded image once, then
runs OCR, object detection, and barcode/QR analysis in parallel.

Its primary lesson is not a particular recognition model. It demonstrates four
reusable boundaries:

- shared qualification before any analysis starts;
- isolated parallel provider execution;
- partial-result contracts that preserve successful siblings;
- replaceable business policy over provider-neutral facts.

## Practical scenario

Assume an event visitor pass contains a visitor name and organization, a face
photo, and an entry QR code.

| Analysis lane | Fact extracted from the image | Visitor-pass policy question |
| --- | --- | --- |
| OCR | Pass text and page metadata | Is readable text present? |
| Detection | Face and sensitive regions | Is there exactly one face and no forbidden region? |
| Barcode/QR | A value beginning with `visitor:` | Is there exactly one valid visitor QR? |

The `demo` profile uses deterministic OCR and detection fixtures while keeping
the real `ZxingBarcodeReader` path. It therefore reproduces the integrated
workflow without native OCR or an external ML model, while still proving real
barcode decoding.

This is not a universal visitor-pass service. It is a reusable foundation for
qualification, parallel processing, partial failure, and policy separation in
workloads such as shipping labels or product labels.

## Architecture

[Open the SVG](./docs/images/readme-diagrams/image-intelligence-architecture.svg)

![Image Intelligence API architecture](./docs/images/readme-diagrams/image-intelligence-architecture.png)

The request follows these boundaries:

1. Check multipart media type, compressed byte size, and the actual file signature.
2. Probe width, height, and decoded pixel count from the image header.
3. Decode guarded bytes into one `ImmutableImage`.
4. Fan that immutable value out to OCR, detection, and ZXing lanes.
5. Preserve each lane as `Completed`, `Empty`, `Unavailable`, or `Failed`.
6. Calculate the aggregate status and apply the visitor-pass policy.

## Interactions and failure meaning

[Open the SVG](./docs/images/readme-diagrams/image-intelligence-interactions.svg)

![Normal completion, partial failure, and external cancellation](./docs/images/readme-diagrams/image-intelligence-interactions.png)

`bluetape4k-workflow` runs the three lanes through `suspendParallelFlow` and
writes each result to a unique `WorkContext` key. Two kinds of success must not
be confused:

| Expression | Meaning |
| --- | --- |
| `WorkReport.Success` | Every workflow task honored its contract and all result keys were collected |
| `AnalysisResult.Completed` | This provider produced an analysis value |
| `AnalysisResult.Empty` | The provider ran successfully but found nothing |
| `AnalysisResult.Unavailable` | The provider is not configured or cannot run |
| `AnalysisResult.Failed` | The provider ran but failed, for example by timeout |

Provider failure is expected domain data. The lane records `Failed` and returns
`WorkReport.Success`, allowing OCR failure to coexist with useful detection and
QR results. A missing result key or an unexpected programming error is a
workflow failure instead.

Aggregate status uses these rules:

| Result combination | Response status |
| --- | --- |
| Every lane is `Completed` or `Empty` | `COMPLETED` |
| Available results coexist with `Unavailable` or `Failed` | `PARTIAL` |
| No lane has an available result | `FAILED` |

External request cancellation is propagated to every child lane rather than
being normalized into a business failure. Semaphore permits are released after
cancellation, timeout, or failure so a later request can proceed.

> Coroutine cancellation cannot forcibly stop an already-running native
> function. `withContext` prevents new native work from starting after
> cancellation, but a non-cooperative Tesseract call can occupy its thread until
> it returns. Production designs may also need process isolation and a
> process-level timeout.

## Run

### Default profile

The default profile starts without external dependencies. OCR and detection
return `UNAVAILABLE(provider_not_configured)`, while ZXing runs normally, so a
valid image usually returns `PARTIAL`.

```bash
./gradlew :spring-boot-image-intelligence-api:bootRun
```

### Demo profile

Use `demo` for the integrated happy path:

```bash
./gradlew :spring-boot-image-intelligence-api:bootRun \
  --args='--spring.profiles.active=demo'
```

### Native OCR profile

After installing Tesseract and the required traineddata on the host, run the
optional native path. Detection remains disabled, OCR uses Tesseract, and
barcode analysis uses ZXing.

```bash
./gradlew :spring-boot-image-intelligence-api:bootRun \
  --args='--spring.profiles.active=native-ocr \
  --example.image-intelligence.tessdata-path=/usr/local/share/tessdata'
```

Activating `demo` and `native-ocr` together is rejected with a stable
configuration error because provider ownership would be ambiguous.

## Request

```bash
curl -X POST \
  -F "file=@visitor-pass.png;type=image/png" \
  http://localhost:8080/api/images/intelligence
```

Only PNG, JPEG, and WebP are accepted. Defaults limit compressed input to
5 MiB, either side to 8,192 pixels, and decoded area to 16,777,216 pixels.
A declared/actual media-type mismatch or undecodable image returns a sanitized
`400`; size-limit violations return `413` with a stable `reasonCode`.

## Response examples

The following abbreviated examples teach response shape and status meaning.
`requestId` and `elapsedMillis` vary by execution.

### `COMPLETED`

With the `demo` profile, an image containing a visitor QR completes all three
lanes and the visitor policy chooses `ALLOW`.

```json
{
  "requestId": "2d7eebfa-2d3b-4d95-b07a-94bb82d6df38",
  "status": "COMPLETED",
  "decision": "ALLOW",
  "reasons": [],
  "image": { "mediaType": "image/png", "width": 240, "height": 240 },
  "ocr": {
    "status": "COMPLETED",
    "provider": "fixture-ocr",
    "elapsedMillis": 4,
    "result": { "text": "VISITOR PASS-001", "pageCount": 1 }
  },
  "detection": {
    "status": "COMPLETED",
    "provider": "fixture-detector",
    "elapsedMillis": 2,
    "regions": [
      { "label": "face", "category": "FACE", "confidence": 0.99, "detector": "fixture-detector" }
    ]
  },
  "barcodes": {
    "status": "COMPLETED",
    "provider": "zxing",
    "elapsedMillis": 18,
    "items": [
      { "text": "visitor:PASS-001", "format": "QR_CODE", "provider": "ZXING" }
    ]
  }
}
```

### `PARTIAL`

If only OCR times out, successful siblings remain available and policy chooses
manual review instead of automatic approval.

```json
{
  "status": "PARTIAL",
  "decision": "MANUAL_REVIEW",
  "reasons": ["OCR_FAILED"],
  "ocr": {
    "status": "FAILED",
    "provider": "tesseract",
    "elapsedMillis": 3001,
    "reasonCode": "timeout"
  },
  "detection": {
    "status": "COMPLETED",
    "provider": "local-detector",
    "elapsedMillis": 32,
    "regions": [
      { "label": "face", "category": "FACE", "confidence": 0.97, "detector": "local-detector" }
    ]
  },
  "barcodes": {
    "status": "COMPLETED",
    "provider": "zxing",
    "elapsedMillis": 19,
    "items": [
      { "text": "visitor:PASS-001", "format": "QR_CODE", "provider": "ZXING" }
    ]
  }
}
```

### `FAILED`

When no analysis result is available, the service returns a domain `FAILED`
envelope instead of converting the request into an HTTP `500`.

```json
{
  "status": "FAILED",
  "decision": "MANUAL_REVIEW",
  "reasons": ["OCR_UNAVAILABLE", "DETECTION_UNAVAILABLE", "BARCODE_FAILED"],
  "ocr": {
    "status": "UNAVAILABLE",
    "provider": "disabled-ocr",
    "elapsedMillis": 0,
    "reasonCode": "provider_not_configured"
  },
  "detection": {
    "status": "UNAVAILABLE",
    "provider": "disabled-detector",
    "elapsedMillis": 0,
    "reasonCode": "provider_not_configured"
  },
  "barcodes": {
    "status": "FAILED",
    "provider": "zxing",
    "elapsedMillis": 2001,
    "reasonCode": "timeout"
  }
}
```

## Configuration

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

Timeouts keep one slow provider from holding the whole response indefinitely.
Per-provider semaphores protect native resources and CPU capacity. Tune them
independently for provider cost and workload behavior.

## Reusing the structure

OCR, detection, and barcode results are business-neutral facts. Replace
`VisitorPassPolicy` while keeping the orchestration:

- shipping labels: compare OCR shipment numbers with label regions and barcodes;
- product labels: compare product text, warning marks, and SKU barcodes;
- intake documents: compare document numbers, stamp regions, and asset QR codes.

A replacement policy should not treat `AnalysisResult.Empty` and `Failed` as
equivalent. “Ran and found nothing” and “could not verify” lead to different
automation decisions.

## Production gaps

This example intentionally omits:

- authentication, authorization, tenant quotas, and rate limiting;
- antivirus/malware scanning and content disarm;
- original/derived storage, retention, deletion, and audit history;
- encryption, masking, and access control for faces, OCR text, and QR values;
- provider retry, circuit breaker, bulkhead, and process isolation;
- production detection-model selection, quality measurement, and drift monitoring.

## Test

```bash
./gradlew :spring-boot-image-intelligence-api:test
```

Tests cover real ZXing extraction from a generated QR, qualification boundaries,
profile ownership, parallel overlap, partial failures, workflow keys, the policy
decision table, external cancellation, permit recovery, payload-free logs, and
the HTTP error contract.

## Resources

- [`ImageUploadQualifier.kt`](./src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/ImageUploadQualifier.kt) — qualification and one decode
- [`ImageIntelligenceWorkflow.kt`](./src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/ImageIntelligenceWorkflow.kt) — `suspendParallelFlow` orchestration
- [`ImageAnalysisProviders.kt`](./src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/ImageAnalysisProviders.kt) — OCR, detection, and ZXing adapters
- [`VisitorPassPolicy.kt`](./src/main/kotlin/io/bluetape4k/images/examples/spring/intelligence/service/VisitorPassPolicy.kt) — fact/policy separation
- [`ImageIntelligenceControllerTest.kt`](./src/test/kotlin/io/bluetape4k/images/examples/spring/intelligence/web/ImageIntelligenceControllerTest.kt) — real HTTP and QR integration
- [`ImageIntelligenceCancellationTest.kt`](./src/test/kotlin/io/bluetape4k/images/examples/spring/intelligence/ImageIntelligenceCancellationTest.kt) — external cancellation and next-request recovery
- [Operating an OCR Service in Practice](https://bluetape4k.github.io/blog/ocr-api-fallback-contract-bluetape4k-image/) — upload limits, native OCR, and failure contracts
- [From Pure JVM to libvips](https://bluetape4k.github.io/blog/from-pure-jvm-to-libvips-benchmarking-image-processing/) — choosing an image-processing backend and cost model
