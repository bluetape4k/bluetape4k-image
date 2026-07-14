---
manualId: "bluetape4k-images-ktor"
id: "bluetape4k-images-ktor"
title: "Ktor image routes"
locale: "en"
kind: "library"
gradlePath: ":bluetape4k-images-ktor"
sourceDir: "images-ktor"
releaseRef: "0.3.0"
artifact: io.github.bluetape4k.image:bluetape4k-images-ktor
---

# Ktor image routes

> Library module

## Problem {#problem}

This module provides small Ktor route adapters for CAPTCHA issue/verification and multipart thumbnail generation. It standardizes validation and HTTP responses while leaving authentication, JSON installation, persistence, and rate limiting with the application.

## When to use it {#when-to-use}

Use it for a compact Ktor service that needs the release CAPTCHA contract or a single-image thumbnail endpoint. Compose a custom route when uploads must stream to object storage, use libvips, or participate in a larger domain workflow.

## Coordinates {#coordinates}

Maven coordinate: `io.github.bluetape4k.image:bluetape4k-images-ktor`

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<version>"))
    implementation("io.github.bluetape4k.image:bluetape4k-images-ktor")
}
```

## Core concepts {#concepts}

`bluetape4kCaptchaRoutes` registers GET issue and POST verify endpoints backed by `CaptchaVerificationService`. `bluetape4kImageThumbnailRoutes` reads one streamed multipart part, decodes through Scrimage, resizes by maximum side, and returns encoded bytes.

## Quick start {#quick-start}

```kotlin
install(ContentNegotiation) { json() }

routing {
    bluetape4kCaptchaRoutes()
    bluetape4kImageThumbnailRoutes(
        ImageThumbnailKtorRoutesConfig(maxInputBytes = 5L * 1024 * 1024),
    )
}
```

## API by task {#api-by-task}

- `GET /captcha?length=6`: returns id, base64 PNG, content type, and expiry.
- `POST /captcha/{id}/verify`: consumes the challenge and maps success/wrong/expired/missing to 200/400/410/404.
- `POST /images/thumbnail?maxSide=320`: reads multipart field `file` and returns PNG by default.
- Replace generators, verification stores, id factories, writers, route paths, and response content types through config objects.

## Recommended patterns {#patterns}

Install JSON serialization before CAPTCHA routes. Provide a distributed one-shot store and an unpredictable id factory in clustered services. Apply auth, CSRF policy, upload content validation, quotas, and rate limits at the application boundary.

## Integrations {#integrations}

The routes reuse `bluetape4k-ktor-core` request/error helpers, `bluetape4k-images`, and `bluetape4k-images-captcha`. They do not integrate Spring storage, S3, CDN, OCR, or libvips.

## Configuration {#configuration}

CAPTCHA defaults to `/captcha`. Thumbnail defaults to `/images/thumbnail`, field `file`, 10 MiB maximum input, 320 default maximum side, 2,048 hard maximum side, and PNG output. Every multipart part is released after inspection.

## Failure modes {#failures}

Invalid parameters, missing/wrong multipart fields, empty uploads, and oversize uploads return the shared 400 error payload. Malformed image I/O is also mapped to 400. CAPTCHA verification uses distinct status codes; unexpected runtime failures remain server errors.

## Operations {#operations}

Scrimage decode/encode runs on `Dispatchers.IO`, but the upload is buffered into a bounded byte array. Observe request size, decode latency, status counts, and CAPTCHA issue/verify ratios without logging answers or image contents.

## Testing {#testing}

Use Ktor test application coverage in `CaptchaKtorRoutesTest` and `ImageThumbnailKtorRoutesTest`. Add application tests for installed JSON, authentication, distributed-store behavior, body limits, and malformed payloads.

## Workshops and learning path {#workshops}

Start with the route tests, run the `examples/ktor-image-api` and `examples/ktor-ocr-api` applications, then replace default in-memory/security boundaries before production.

## Limitations {#limitations}

The thumbnail endpoint is not a streaming transform: it buffers at most `maxInputBytes + 1` and uses Scrimage. It creates one thumbnail, stores nothing, and performs no MIME sniffing beyond image decode. CAPTCHA routes do not install JSON or abuse controls.

## Sources {#sources}

- [CAPTCHA routes](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-ktor/src/main/kotlin/io/bluetape4k/images/ktor/CaptchaKtorRoutes.kt)
- [Thumbnail route and limits](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-ktor/src/main/kotlin/io/bluetape4k/images/ktor/ImageThumbnailKtorRoutes.kt)
- [Release build](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-ktor/build.gradle.kts)
