---
manualId: "ktor-integration"
title: "Ktor Integration"
locale: "en"
releaseRef: "0.3.0"
---

# Ktor Integration

<code>bluetape4k-images-ktor</code> supplies focused route helpers for thumbnails and CAPTCHA. It depends on the core image and CAPTCHA modules and the shared Bluetape Ktor foundation.

## Thumbnail routes

<code>ImageThumbnailKtorRoutes</code> connects an input image source to resizing and encoded output. The application must define where media comes from, which dimensions and formats are allowed, cache behavior, authentication, and request limits. Never let a route resolve arbitrary filesystem paths from a request.

## CAPTCHA routes

<code>CaptchaKtorRoutes</code> issues challenge images and verifies answers through the CAPTCHA service. Route configuration controls API shape, while the challenge store owns one-shot state and expiry. Install JSON serialization and error handling in the application; the helper does not silently alter the global Ktor pipeline.

## Compose the application

The [Ktor image API workshop](../modules/ktor-image-api.md) demonstrates both route families with tests. Use it as the smallest complete topology, then replace in-memory or example components with application-owned storage, authentication, rate limits, and observability.

OCR is not hidden inside the route module. The [Ktor OCR workshop](../modules/ktor-ocr-api.md) shows how to combine multipart input, the framework-neutral OCR engine, response models, and failures explicitly.

## Sources

- [CAPTCHA routes](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images-ktor/src/main/kotlin/io/bluetape4k/images/ktor/CaptchaKtorRoutes.kt)
- [Thumbnail routes](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images-ktor/src/main/kotlin/io/bluetape4k/images/ktor/ImageThumbnailKtorRoutes.kt)
