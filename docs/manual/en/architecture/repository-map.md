---
manualId: "repository-map"
title: "Repository Map"
locale: "en"
releaseRef: "0.3.0"
---

# Repository Map

The 0.3.0 release contains 15 Gradle projects. Nine are published coordinates, five are runnable examples, and one is a benchmark project. The project directory, Gradle path, and artifact name are intentionally not always the same, so the release registry is the authoritative inventory.

## Platform and foundation

- [Image BOM](../modules/bluetape4k-image-bom.md) aligns the eight image library artifacts.
- [Immutable image processing](../modules/bluetape4k-images.md) is the portable Scrimage/Java2D foundation used by CAPTCHA, OCR, Ktor, Spring Boot, and the libvips API test fixtures.

## Capabilities and frameworks

- [CAPTCHA](../modules/bluetape4k-images-captcha.md) generates challenges and owns verification semantics.
- [OCR](../modules/bluetape4k-images-ocr.md) adapts Tess4J/Tesseract to <code>ImmutableImage</code>.
- [Ktor routes](../modules/bluetape4k-images-ktor.md) expose thumbnail and CAPTCHA routes.
- [Spring Boot](../modules/bluetape4k-images-spring-boot.md) configures storage, CDN, health, and metrics.

## Native processing

- [Vips API](../modules/bluetape4k-images-vips-api.md) defines <code>VipsImage</code>, <code>VipsRuntime</code>, formats, writers, and lifecycle rules.
- [Java 21 JVips](../modules/bluetape4k-images-vips-java21.md) implements that API with JNI.
- [Java 25 FFM](../modules/bluetape4k-images-vips-java25.md) implements it with the Foreign Function and Memory API.

The common API does not select or initialize a backend for the application. Deploy exactly one runtime implementation unless a measured migration requires both.

## Learn and measure

The five workshops cover basic JVM processing, Ktor image/CAPTCHA, Ktor OCR, Spring Boot storage, and Spring Boot OCR. The [benchmark project](../modules/bluetape4k-images-benchmark.md) compares processing and I/O paths but is not a published library.

Start from [the learning path](../guides/learning-path.md) instead of reading the project list alphabetically.

## Source

- [Exact 0.3.0 project registration](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/settings.gradle.kts#L84-L123)
- [Publishing inclusion rules](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/build.gradle.kts#L46-L58)
