---
manualId: "storage-and-cdn"
title: "Storage and CDN"
locale: "en"
releaseRef: "0.4.0"
---

# Storage and CDN

The Spring Boot module defines an <code>ImageStorage</code> boundary with local and S3 implementations and optional CloudFront URL signing. Choose from durability, topology, and access policy rather than switching by environment name alone.

## Local storage

Local storage fits single-node development, controlled batch jobs, and deployments with a durable mounted volume. Resolve every key under a configured root, reject path traversal, create directories deliberately, and define atomic-write and cleanup behavior. A container's writable layer is not durable storage.

## S3 storage

S3 fits shared and durable object storage. The application owns bucket creation, encryption, credentials, region, key naming, lifecycle, retention, and retry/idempotency policy. Treat an upload as incomplete until the object operation succeeds; do not publish metadata or CDN URLs first.

## CDN URLs

CloudFront signing can produce controlled delivery URLs, but it does not replace S3 policy. Protect private key material, keep URL lifetime short enough for the use case, and consider clock skew. Cache keys should include every transformation parameter that changes bytes.

## Health and metrics

Health checks should be cheap and must not create or delete user objects. Metrics should distinguish operation, backend, outcome, and latency without placing object keys or user data in high-cardinality tags.

The [Spring Boot image API workshop](../modules/spring-boot-image-api.md) starts with local storage. Extend it to S3 only after the same storage contract and failure behavior are tested.

## Sources

- [ImageStorage contract](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images-spring-boot/src/main/kotlin/io/bluetape4k/images/spring/storage/ImageStorage.kt)
- [S3 implementation](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images-spring-boot/src/main/kotlin/io/bluetape4k/images/spring/storage/s3/S3ImageStorage.kt)
