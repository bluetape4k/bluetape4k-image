---
manualId: "spring-vs-ktor"
title: "Spring Boot or Ktor"
locale: "en"
releaseRef: "0.4.0"
---

# Spring Boot or Ktor

Choose the integration that already owns application configuration and lifecycle. Do not add a second framework just to obtain image helpers.

## Choose Ktor

[Ktor integration](../integrations/ktor.md) is focused: it supplies thumbnail and CAPTCHA route helpers on top of the image and CAPTCHA modules. The application installs its own JSON serialization, authentication, error handling, request-size policy, and persistence. Use the [Ktor image API workshop](../modules/ktor-image-api.md) to see challenge issuance, verification, and thumbnails together.

Ktor OCR is assembled directly in the [Ktor OCR workshop](../modules/ktor-ocr-api.md); the OCR library itself is framework-neutral.

## Choose Spring Boot

[Spring Boot integration](../integrations/spring-boot.md) is broader. It auto-configures local or S3 storage, optional CloudFront URL signing, health, and metrics. Choose it when Boot already owns configuration properties, bean lifecycle, Actuator, and Micrometer. Follow the [Spring Boot image API workshop](../modules/spring-boot-image-api.md).

The [Spring Boot OCR workshop](../modules/spring-boot-ocr-api.md) wires the framework-neutral OCR engine into a web controller and keeps host Tesseract as an explicit runtime prerequisite.

## Use the libraries directly

For batch jobs, command-line tools, or another framework, use <code>bluetape4k-images</code>, CAPTCHA, OCR, or Vips directly. Framework modules are adapters, not prerequisites for core processing.

Whichever path you choose, the application owns upload limits, authentication, storage credentials, response caching, and shutdown behavior.

## Sources

- [Ktor release module](https://github.com/bluetape4k/bluetape4k-image/tree/ea5175b083babf8880f53cf80c9a264a0c61777e/images-ktor)
- [Spring Boot release module](https://github.com/bluetape4k/bluetape4k-image/tree/ea5175b083babf8880f53cf80c9a264a0c61777e/images-spring-boot)
