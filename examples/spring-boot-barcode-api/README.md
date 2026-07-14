# Spring Boot Barcode API Quickstart

English | [한국어](./README.ko.md)

Compact Spring Boot 4 example for extracting QR and barcode data from bundled
scenarios or multipart image uploads with `bluetape4k-images-barcode-zxing`.

## What It Shows

- Deterministic `GET` endpoints for barcode-found, no-result, and malformed
  input scenarios
- `POST /api/barcodes/extract` for user-supplied PNG, JPEG, and WebP images
- Provider-neutral response DTOs backed by the pure-JVM `ZxingBarcodeReader`
- Separate compressed-byte, decoded-side, and decoded-pixel guards before
  barcode extraction
- Coroutine dispatching for blocking multipart reads and CPU-bound decoding
- Stable, sanitized HTTP errors that do not expose filenames, raw bytes,
  provider metadata, regions, or stack traces
- MockMvc coverage without Docker, native libraries, or external services

This is a local quickstart, not a production upload service. Authentication,
rate limiting, request concurrency limits, a request-log policy, malware
scanning, and observability must be added by the consuming application.

## Diagrams

### Deterministic Scenarios

![Spring Boot Barcode API scenarios](docs/images/readme-diagrams/barcode-api-scenarios.png)

### Architecture

![Spring Boot Barcode API architecture](docs/images/readme-diagrams/barcode-api-architecture.png)

### Upload Sequence

![Spring Boot Barcode API upload sequence](docs/images/readme-diagrams/barcode-api-sequence.png)

## Run

Start the example from the repository root:

```bash
./gradlew :spring-boot-barcode-api:bootRun
```

The application listens on `http://localhost:8080`.

## Deterministic Scenario Endpoints

The bundled resources make the three main outcomes reproducible without
preparing an upload first.

| Endpoint | Status | Outcome |
|---|---:|---|
| `GET /api/barcodes/sample` | `200` | One QR result with text `bluetape4k-barcode-quickstart` |
| `GET /api/barcodes/no-result` | `200` | Valid image, `count: 0`, empty `results` |
| `GET /api/barcodes/malformed` | `400` | Sanitized `MALFORMED_INPUT` response |

```bash
curl http://localhost:8080/api/barcodes/sample
curl http://localhost:8080/api/barcodes/no-result
curl http://localhost:8080/api/barcodes/malformed
```

Sample response:

```json
{
  "count": 1,
  "results": [
    {
      "text": "bluetape4k-barcode-quickstart",
      "format": "QR_CODE",
      "provider": "ZXing"
    }
  ]
}
```

No barcode is a successful extraction with an empty result:

```json
{
  "count": 0,
  "results": []
}
```

## Upload an Image

Send a multipart `file` part to the extraction endpoint. The declared content
type must be `image/png`, `image/jpeg`, or `image/webp`.

```bash
curl -F \
  "file=@examples/spring-boot-barcode-api/src/main/resources/barcodes/qr.png;type=image/png" \
  "http://localhost:8080/api/barcodes/extract"
```

To try your own web image:

```bash
curl -F 'file=@/path/to/image.webp;type=image/webp' \
  http://localhost:8080/api/barcodes/extract
```

The response deliberately contains only `text`, provider-neutral `format`, and
provider name. It excludes raw provider bytes, backend format labels, result
points, regions, arbitrary metadata, source filenames, and stack traces.

## Limits and Error Contract

The defaults are intentionally small and are configured in `application.yml`:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 6MB

example:
  barcode:
    max-input-bytes: 5242880
    max-input-pixels: 16777216
    max-input-side: 8192
```

`spring.servlet.multipart` rejects oversized multipart requests at the web
boundary. The example then checks the actual byte array again and probes image
dimensions before creating an `ImmutableImage` or invoking the provider.

| Status | Error | Meaning |
|---:|---|---|
| `400` | `empty_input` | Missing or empty multipart file |
| `400` | `malformed_input` | Bytes cannot be decoded as an image |
| `400` | `unsupported_format` | Barcode format is not supported |
| `413` | `payload_too_large` | Compressed bytes, side length, or pixel count exceeds a limit |
| `415` | `unsupported_media_type` | Missing or disallowed declared content type |
| `503` | `provider_unavailable` | Barcode provider is unavailable |
| `500` | provider failure reason | Extraction failed without exposing provider details |

Example malformed response:

```json
{
  "error": "malformed_input",
  "reason": "MALFORMED_INPUT",
  "message": "The uploaded file is not a decodable image."
}
```

The content-type allowlist is only an early request guard. The service still
decodes and probes the actual bytes. A production service should additionally
set request timeouts and concurrency limits, authenticate callers, rate-limit
uploads, define a request-log policy that keeps raw inputs out of logs, scan
uploads for malware, and monitor rejection rates and provider latency.

## Dependencies

The example depends on `bluetape4k-images-barcode-zxing`, which supplies the
provider implementation while exposing `bluetape4k-images-barcode-api` result
contracts. Spring Web handles multipart MVC requests, and coroutine support
keeps blocking reads and CPU-bound extraction off the request coroutine.

The bundled HTTP success fixture verifies QR Code extraction. The ZXing provider
module separately verifies QR Code and Code 128, while the provider-neutral API
keeps decoder-specific types out of this example's response contract.

## Test

```bash
./gradlew :spring-boot-barcode-api:test
```

The tests cover the three deterministic endpoints, PNG/JPEG/WebP uploads,
bounded success JSON, empty results, malformed bytes, missing parts,
unsupported media types, encoded size, decoded side length, decoded pixel
count, cancellation propagation, and sanitized exception mapping.
