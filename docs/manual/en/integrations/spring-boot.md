---
manualId: "spring-boot-integration"
title: "Spring Boot Integration"
locale: "en"
releaseRef: "0.4.0"
---

# Spring Boot Integration

<code>bluetape4k-images-spring-boot</code> provides Spring Boot 4 auto-configuration for image storage, optional CDN URL signing, health, and metrics. Core processing remains in the image library.

## Auto-configuration groups

- Storage configuration selects local or S3-backed <code>ImageStorage</code>.
- CDN configuration creates optional CloudFront URL signing support.
- Health configuration exposes storage availability through Actuator contracts.
- Metrics configuration instruments storage operations when Micrometer is present.
- Processing configuration binds processing properties only; the application still creates and owns its processor or pipeline components.

Optional AWS and observability dependencies are compile-only in the library. The application brings the features it uses.

## Configuration ownership

Bind storage roots, bucket and region settings, CDN keys, upload policy, and feature switches from the application's secure configuration. Validate local roots and create directories with appropriate permissions. Use the standard AWS provider chain or application credential policy; never place secrets in manual examples or repository files.

## Start with the workshop

The [Spring Boot image API workshop](../modules/spring-boot-image-api.md) demonstrates multipart upload, local storage, download, thumbnail processing, configuration, and tests. Replace its local assumptions before production. For OCR, use the separate [Spring Boot OCR workshop](../modules/spring-boot-ocr-api.md).

Read [storage and CDN](storage-and-cdn.md) before selecting an operational backend.

## Sources

- [Auto-configuration source](https://github.com/bluetape4k/bluetape4k-image/tree/ea5175b083babf8880f53cf80c9a264a0c61777e/images-spring-boot/src/main/kotlin/io/bluetape4k/images/spring/autoconfigure)
- [Spring Boot module reference](../modules/bluetape4k-images-spring-boot.md)
