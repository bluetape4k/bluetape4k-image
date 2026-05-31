# Ktor Image API Quickstart

English | [한국어](./README.ko.md)

Compact Ktor 3 example for running the `bluetape4k-images-ktor` route helpers
locally. It exposes CAPTCHA issue/verification routes and a multipart image
thumbnail route without S3, CDN, external services, Docker, or native libvips.

## What It Shows

- `bluetape4kCaptchaRoutes` mounted at `/api/captcha`
- `bluetape4kImageThumbnailRoutes` mounted at `/api/images`
- JSON serialization and error payload defaults from `bluetape4k-ktor-core`
- Multipart thumbnail generation through `bluetape4k-images`
- Route tests with Ktor test host and `bluetape4k-ktor-testing`

This quickstart is intentionally local-only. Use a larger workshop or service
example when persistence, public URLs, S3/CDN, or operational policy decisions
are required.

## Run

```bash
./gradlew :ktor-image-api:run
```

The server listens on port `8080` by default. Set `PORT` to override it.

```bash
PORT=9090 ./gradlew :ktor-image-api:run
```

## CAPTCHA Routes

Issue a CAPTCHA challenge:

```bash
curl "http://localhost:8080/api/captcha?length=6"
```

Example response:

```json
{
  "id": "4f3sGvA9tPq1LmN2",
  "imageBase64": "iVBORw0KGgo...",
  "contentType": "image/png",
  "expiresAt": "2026-05-29T07:00:00Z"
}
```

Verify the answer:

```bash
curl -X POST "http://localhost:8080/api/captcha/4f3sGvA9tPq1LmN2/verify" \
  -H "Content-Type: application/json" \
  -d '{"answer":"123456"}'
```

## Thumbnail Route

Create a PNG thumbnail from a multipart image upload:

```bash
curl -X POST \
  -F "file=@images/src/test/resources/images/cafe.jpg;type=image/jpeg" \
  "http://localhost:8080/api/images/thumbnail?maxSide=320" \
  -o thumbnail.png
```

The route returns PNG bytes. It does not store the original image and does not
require S3 or CDN configuration.

## Test

```bash
./gradlew :ktor-image-api:test
```

The tests verify the ready endpoint, CAPTCHA issuance, thumbnail generation, and
bad-request handling for missing multipart file fields.
