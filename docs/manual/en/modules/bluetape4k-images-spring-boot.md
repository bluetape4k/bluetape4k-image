---
manualId: "bluetape4k-images-spring-boot"
id: "bluetape4k-images-spring-boot"
title: "Spring Boot image platform"
locale: "en"
kind: "library"
gradlePath: ":bluetape4k-images-spring-boot"
sourceDir: "images-spring-boot"
releaseRef: "0.3.0"
artifact: io.github.bluetape4k.image:bluetape4k-images-spring-boot
---

# Spring Boot image platform

> Library module

## Problem {#problem}

This Spring Boot 4 module wires image object storage, optional signed URLs, reactive health, and Micrometer metrics. It defines a stable coroutine `ImageStorage` boundary and chooses local or S3-backed implementations from configuration.

Despite its name, release `0.3.0` does not auto-register an image processor or filter pipeline; its processing auto-configuration only binds properties.

## When to use it {#when-to-use}

Use it when a Spring Boot service needs local/S3 image persistence and operational integration. Use `bluetape4k-images` directly for processing and add this module only for storage/CDN/health/metrics wiring.

## Coordinates {#coordinates}

Maven coordinate: `io.github.bluetape4k.image:bluetape4k-images-spring-boot`

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<version>"))
    implementation("io.github.bluetape4k.image:bluetape4k-images-spring-boot")
}
```

## Core concepts {#concepts}

`ImageObjectKey` validates prefix/name and blocks `..`. `ImageStorage` provides suspend upload/download/delete/exists and cold-flow listing. `ImageStorageException` is sealed into not-found, access-denied, conflict, transient, and validation failures. Optional decorators provide signed URLs, health, and metrics.

## Quick start {#quick-start}

```yaml
bluetape4k:
  images:
    storage:
      enabled: true
      backend: local
      max-size-bytes: 52428800
      local:
        root-dir: /var/lib/app/images
```

```kotlin
val key = ImageObjectKey.of("avatars", "user-42.jpg")
storage.upload(key, bytes, UploadOptions(contentType = "image/jpeg"))
val saved = storage.download(key)
```

## API by task {#api-by-task}

- Store locally with `LocalImageStorage` or select S3 with `backend=s3` and an `S3Operations` bean.
- Upload/download bytes or paths; list keys as a cold `Flow`.
- Generate S3 presigned read/write URLs or CloudFront read URLs when CDN is enabled.
- Enable the reactive health indicator and Micrometer decorator when those APIs are present.
- Override any auto-configured bean with an application `ImageStorage` or signer.

## Recommended patterns {#patterns}

Use explicit persistent local storage; the default is under the JVM temp directory. Treat `ValidationException` as a client problem, `NotFoundException` as absence, and `TransientException` as the only generally retryable class. Re-throw coroutine cancellation in custom implementations.

Do not assume the interface's atomic-upload requirement means crash-atomic local replacement in `0.3.0`: the local implementation uses `Files.write`/`Files.copy(REPLACE_EXISTING)`, not a temporary file plus atomic rename.

## Integrations {#integrations}

S3 uses `bluetape4k-aws-spring-boot` `S3Operations`. CloudFront and S3 presigning are optional classpath integrations. Health needs Spring Boot health plus coroutines-reactor; metrics needs Micrometer.

## Configuration {#configuration}

Storage defaults: enabled, `LOCAL`, 50 MiB upload/download limit, temp-root local directory. Selecting `S3` requires a non-blank bucket when `S3Operations` exists. If `backend=s3` but no `S3Operations` bean is available, release `0.3.0` deliberately falls back to local storage.

CDN is disabled by default. Providers are `s3_presign` and `cloudfront`. CloudFront requires exactly one private-key source; prefer a mounted `privateKeyPath`. Health and metrics default to enabled when their supporting classes exist.

## Failure modes {#failures}

Storage failures use the sealed exception hierarchy. S3 401/403 maps to access denied, 404 to not found, 409 to conflict, and other SDK failures to transient. `exists` must not convert access errors to `false`. Invalid keys, sizes, and upload MIME types fail validation; SVG is intentionally excluded from allowed upload types.

## Operations {#operations}

Metrics cover upload/download duration and errors under `images.storage.*`. Health calls `exists` on `_health/<probeKey>` and reports reachability, not object presence. CloudFront key properties are redacted by `toString` and an Actuator sanitizing function.

## Testing {#testing}

Use auto-configuration tests with `ApplicationContextRunner`, local storage contract tests, metric and health tests, and key/sanitization tests. Add an integration test for the application's real S3-compatible endpoint; the release suite does not include an S3 storage integration test.

## Workshops and learning path {#workshops}

Run `examples/spring-boot-image-api`, then inspect local storage, switch to an explicit S3 environment, add signed URLs, and only then enable production health/metrics alerts.

## Limitations {#limitations}

S3 path uploads are read into a byte array because `S3Operations` has no streaming upload in this release. `UploadOptions.cacheControl` and metadata are not forwarded by `S3ImageStorage`. Its size precheck uses listing because no HEAD operation is exposed. Use the lower-level AWS SDK for large streams or required headers.

<!-- release-readme-diagrams:start -->
## Release diagrams {#release-diagrams}

These diagrams are copied byte-for-byte from README assets in the `0.3.0` release tag. They describe this manual's released structure and runtime flows, not later Snapshot changes. Select a preview to open the SVG source.

### Images Spring Boot Architecture diagram

[![Images Spring Boot Architecture diagram](../../assets/readme-diagrams/images-spring-boot-architecture-01.png)](../../assets/readme-diagrams/images-spring-boot-architecture-01.svg)

_Release README: [`images-spring-boot/README.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images-spring-boot/README.md)_

<!-- release-readme-diagrams:end -->

## Sources {#sources}

- [Storage contract](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-spring-boot/src/main/kotlin/io/bluetape4k/images/spring/storage/ImageStorage.kt)
- [Storage auto-configuration](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-spring-boot/src/main/kotlin/io/bluetape4k/images/spring/autoconfigure/ImagesStorageAutoConfiguration.kt)
- [S3 implementation and constraints](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-spring-boot/src/main/kotlin/io/bluetape4k/images/spring/storage/s3/S3ImageStorage.kt)
- [Release build](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-spring-boot/build.gradle.kts)
