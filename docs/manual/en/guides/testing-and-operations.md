---
manualId: "testing-and-operations"
title: "Testing and Operations"
locale: "en"
releaseRef: "0.3.0"
---

# Testing and Operations

Test the boundary the deployment actually uses. A pure JVM unit test cannot prove host Tesseract, system libvips, S3 credentials, or filesystem permissions.

## Test pyramid

1. Unit-test transformations, validation, CAPTCHA state, and storage policy with small deterministic fixtures.
2. Use golden images or numeric similarity assertions for visual operations. Avoid byte-for-byte comparisons when encoder metadata is nondeterministic.
3. Run module tests for the selected framework path.
4. Run host-native OCR and libvips checks sequentially on a runner with the required packages.
5. Smoke-test storage and CDN configuration in the deployment environment.

Representative commands:

    ./gradlew :bluetape4k-images:test
    ./gradlew :bluetape4k-images-ocr:test -Docr.enabled=true
    ./gradlew :bluetape4k-images-vips-java21:test

## Operational signals

Measure input bytes, decoded dimensions, processing duration, output bytes, failure category, queue depth, and storage latency. Spring Boot integration can contribute health and metrics, but application-level dimensions and alert thresholds remain local decisions.

## Capacity and isolation

Bound request size before decode. Limit concurrent OCR and native work rather than allowing an unbounded coroutine fanout. Separate benchmark jobs and native test jobs from normal fast CI when their host requirements differ, but keep a scheduled path that proves them.

## Release discipline

This manual targets 0.3.0. Tests and source links must stay on that release commit. A green develop build cannot prove a frozen manual example.

## Sources

- [Release test configuration](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/build.gradle.kts)
- [Spring Boot health and metrics module](https://github.com/bluetape4k/bluetape4k-image/tree/a571c30004f571fe8cfcddc29670c1404d212ec6/images-spring-boot/src/main/kotlin/io/bluetape4k/images/spring)
