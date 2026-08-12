---
manualId: "learning-path"
title: "Image Learning Path"
locale: "en"
releaseRef: "0.4.0"
---

# Image Learning Path

The manual includes selection rules, API explanations, runnable examples, failure diagnosis, and operational constraints. Enter at the first unresolved decision instead of reading all projects alphabetically.

![Learning path from portable JVM processing through framework examples and native backends](../../assets/overview/repository-learning-map.svg)

## 1. Complete portable JVM processing

Read [Getting started](../getting-started.md), [the immutable image model](../core/immutable-image-model.md), and [loading and writing](../core/loading-and-writing.md). Run the [basic processing workshop](../modules/basic-processing.md) and prove one load-transform-write path.

## 2. Add only the capability you need

Continue to [transforms and filters](../core/transforms-and-filters.md) or [analysis and similarity](../core/analysis-and-similarity.md). If the service needs challenges or text extraction, add [CAPTCHA](../integrations/captcha.md) or [OCR](../integrations/ocr.md); these are separate dependencies and runtime concerns.

## 3. Choose the application boundary

Use [Spring Boot versus Ktor](spring-vs-ktor.md). Complete one framework workshop:

- [Ktor image API](../modules/ktor-image-api.md) for thumbnail and CAPTCHA routes;
- [Spring Boot image API](../modules/spring-boot-image-api.md) for storage, upload, and download;
- the matching [Ktor OCR](../modules/ktor-ocr-api.md) or [Spring Boot OCR](../modules/spring-boot-ocr-api.md) workshop after host Tesseract is ready.

## 4. Move to native processing only with a reason

Read [backend selection](backend-selection.md), [Vips API](../native/vips-api.md), and [native lifecycle](native-resource-lifecycle.md). Choose JDK 25 JVips JNI (the legacy `java21` artifact) or JDK 25 FFM from the native package constraints. Close every derived image.

## 5. Validate the production boundary

Finish with [testing and operations](testing-and-operations.md), [failure diagnosis](failure-diagnosis.md), and [performance selection](performance-selection.md). Record input limits, native prerequisites, resource ownership, storage policy, and the benchmark or load test that supports the chosen backend.

At the end, the application should answer: which backend performs each task, who closes its resources, which inputs are rejected before decode, and which test proves the deployment boundary.

## Sources

- [Release examples](https://github.com/bluetape4k/bluetape4k-image/tree/ea5175b083babf8880f53cf80c9a264a0c61777e/examples)
- [Release module guide](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/README.md#modules)
