---
manualId: "spring-boot-image-api"
id: "spring-boot-image-api"
title: "Spring Boot image API workshop"
locale: "en"
kind: "example"
gradlePath: ":spring-boot-image-api"
sourceDir: "examples/spring-boot-image-api"
releaseRef: "0.3.0"
artifact: null
---

# Spring Boot image API workshop

> Runnable example

## Problem {#problem}

This Spring Boot 4 workshop accepts a multipart upload, validates it, stores the original through the auto-configured local `ImageStorage`, creates a PNG thumbnail, and returns local read URLs. It provides an end-to-end storage boundary without requiring S3, CDN, Docker, or native image libraries.

## When to use it {#when-to-use}

Use it to learn `bluetape4k-images-spring-boot`, verify local storage auto-configuration, or prototype an upload/thumbnail/download contract before choosing production storage.

## Coordinates {#coordinates}

The application is not published. Consumers choose one `bluetape4k-dependencies` version, then add `bluetape4k-images` and `bluetape4k-images-spring-boot` without separate module version pins.

## Core concepts {#concepts}

- Spring auto-configuration supplies `ImageStorage` from `bluetape4k.images.storage` properties.
- `UploadOptions.ALLOWED_CONTENT_TYPES` validates the upload before decoding.
- Originals and PNG thumbnails use separate `ImageObjectKey` prefixes.
- Blocking multipart reads use `Dispatchers.IO`; image transformation uses `Dispatchers.Default`.

## Quick start {#quick-start}

Prerequisite: JDK 21+. No external service is required.

```bash
./gradlew :spring-boot-image-api:bootRun
curl -F "file=@images/src/test/resources/images/cafe.jpg;type=image/jpeg" \
  "http://localhost:8080/api/images?maxSide=320"
```

The API responds with `201 Created`, original/thumbnail keys, local read URLs, and both byte counts. Download either URL with a normal `GET`.

## API by task {#api-by-task}

| Task | Release API |
| --- | --- |
| Upload | `POST /api/images?maxSide=320` |
| Download | `GET /api/images/{prefix}/{name}` |
| Store bytes | `ImageStorage.upload(key, bytes, UploadOptions)` |
| Read bytes | `ImageStorage.download(key)` |
| Generate thumbnail | `immutableImageOf(bytes).fit(...).forWriter(PngWriter.MaxCompression)` |

## Recommended patterns {#patterns}

Validate media type, emptiness, and dimensions before expensive work. Keep storage behind `ImageStorage`, give original and derivative objects distinct prefixes, and return storage metadata rather than leaking filesystem paths.

## Integrations {#integrations}

The example combines [`bluetape4k-images-spring-boot`](./bluetape4k-images-spring-boot.md) and [`bluetape4k-images`](./bluetape4k-images.md). Replace local storage with an application-approved backend only after defining bucket ownership, credentials, URLs, retention, and CDN policy.

## Configuration {#configuration}

```yaml
bluetape4k:
  images:
    storage:
      backend: local
      max-size-bytes: 10485760
      local:
        root-dir: build/tmp/spring-boot-image-api/storage
```

Spring multipart request and file limits are both 10 MiB. `maxSide` accepts `64..2048` and defaults to `320`.

## Failure modes {#failures}

- `400 bad_request`: empty file, unsupported content type, or out-of-range `maxSide`.
- Download returns an error: use the exact key returned by upload and verify the local root still exists.
- Upload exceeds limits: align Spring multipart limits and image-storage `max-size-bytes`.
- Production URL is local: this workshop deliberately returns controller read URLs, not CDN URLs.

## Operations {#operations}

The default store lives under `build/tmp` and is disposable. Production work must define durable storage, cleanup/retention, duplicate handling, authorization, malware/content checks, metrics, and public URL policy.

## Testing {#testing}

```bash
./gradlew :spring-boot-image-api:test
```

The MockMvc integration test uploads an in-memory JPEG, verifies both key prefixes and read URLs, downloads both objects, checks the PNG signature, and rejects an unsupported content type.

## Workshops and learning path {#workshops}

1. Run [`basic-processing`](./basic-processing.md) to understand the transform.
2. Run this workshop and inspect the storage directory and both downloads.
3. Read the Spring Boot storage module manual.
4. Continue to [`spring-boot-ocr-api`](./spring-boot-ocr-api.md) or a larger workshop before adding native OCR or S3/CDN concerns.

## Limitations {#limitations}

The workshop is a local quickstart. It omits S3/CDN configuration, authentication, lifecycle policy, asynchronous processing, and multi-instance consistency.

<!-- release-readme-diagrams:start -->
## Release diagrams {#release-diagrams}

These diagrams are copied byte-for-byte from README assets in the `0.3.0` release tag. They describe this manual's released structure and runtime flows, not later Snapshot changes. Select a preview to open the SVG source.

### Spring Boot Image API Architecture

[![Spring Boot Image API Architecture](../../assets/readme-diagrams/examples-spring-boot-image-api-architecture-01.png)](../../assets/readme-diagrams/examples-spring-boot-image-api-architecture-01.svg)

_Release README: [`examples/spring-boot-image-api/README.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/examples/spring-boot-image-api/README.md)_

### Spring Boot Image API Scenario

[![Spring Boot Image API Scenario](../../assets/readme-diagrams/examples-spring-boot-image-api-scenario-01.png)](../../assets/readme-diagrams/examples-spring-boot-image-api-scenario-01.svg)

_Release README: [`examples/spring-boot-image-api/README.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/examples/spring-boot-image-api/README.md)_

### Spring Boot Image API Sequence

[![Spring Boot Image API Sequence](../../assets/readme-diagrams/examples-spring-boot-image-api-sequence-01.png)](../../assets/readme-diagrams/examples-spring-boot-image-api-sequence-01.svg)

_Release README: [`examples/spring-boot-image-api/README.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/examples/spring-boot-image-api/README.md)_

<!-- release-readme-diagrams:end -->

## Sources {#sources}

- [Release README](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/examples/spring-boot-image-api/README.md)
- [Controller and service](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/examples/spring-boot-image-api/src/main/kotlin/io/bluetape4k/images/examples/spring/SpringBootImageApiApplication.kt)
- [Application configuration](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/examples/spring-boot-image-api/src/main/resources/application.yml)
- [Integration test](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/examples/spring-boot-image-api/src/test/kotlin/io/bluetape4k/images/examples/spring/SpringBootImageApiApplicationTest.kt)
- [Gradle build file](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/examples/spring-boot-image-api/build.gradle.kts)
