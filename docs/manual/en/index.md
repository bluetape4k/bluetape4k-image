---
manualId: "repository-overview"
title: "Bluetape4k Image Manual"
locale: "en"
releaseRef: "0.3.0"
---

# Bluetape4k Image Manual

<code>bluetape4k-image</code> provides two image-processing families for Kotlin/JVM applications. The <code>bluetape4k-images</code> path builds on Scrimage and Java2D for immutable image operations, filters, transforms, analysis, CAPTCHA, OCR, and framework integration. The libvips path exposes a binding-neutral API with a Java 21 JNI backend and a Java 25 FFM backend for native processing.

This manual is organized around decisions and production tasks rather than package names. Start with the runtime and backend choice, complete one runnable workshop, and then open the module reference when you need exact API or configuration details.

## Core capabilities

- **Immutable JVM processing:** The [image model](core/immutable-image-model.md), [loading and writing](core/loading-and-writing.md), and [transforms and filters](core/transforms-and-filters.md) cover safe Scrimage and Java2D pipelines.
- **Analysis and formats:** [Analysis and similarity](core/analysis-and-similarity.md) plus the [codec guide](guides/codec-and-format-selection.md) explain metadata, comparison, encoding, and format choices.
- **Barcode, CAPTCHA, and OCR:** The integration guides for [CAPTCHA](integrations/captcha.md) and [OCR](integrations/ocr.md), together with the published barcode modules, provide common extraction and challenge flows.
- **Native libvips backends:** The binding-neutral [Vips API](native/vips-api.md) supports a [Java 21 JNI](native/java21-jni.md) backend and a [Java 25 FFM](native/java25-ffm.md) backend with explicit native-resource ownership.
- **Web framework integration:** [Ktor](integrations/ktor.md) and [Spring Boot](integrations/spring-boot.md) connect image processing, upload, CAPTCHA, OCR, health, and metrics to application lifecycles.
- **Storage and production selection:** [Storage and CDN](integrations/storage-and-cdn.md), [performance selection](guides/performance-selection.md), and [testing and operations](guides/testing-and-operations.md) define the boundaries the application must own.

## Version baseline

Applications select one central BOM version: <code>io.github.bluetape4k:bluetape4k-dependencies:&lt;version&gt;</code>. They do not need to align the image BOM, Scrimage, Ktor, Spring Boot, or native binding versions independently.

The technical baseline is the immutable <code>0.3.0</code> release. It contains 8 published libraries, 1 published image BOM, 5 runnable examples, and 1 non-published benchmark project.

- [Release tag 0.3.0](https://github.com/bluetape4k/bluetape4k-image/tree/0.3.0)
- [Release commit a571c300](https://github.com/bluetape4k/bluetape4k-image/commit/a571c30004f571fe8cfcddc29670c1404d212ec6)
- Runtime baseline: JDK 21 for the Scrimage, OCR, framework, and JNI paths; JDK 25 for the FFM backend

Features added after this tag are deliberately absent. Every source link in this manual resolves against the `0.3.0` release tag or its pinned commit.

## Where to start

- Use [Getting started](getting-started.md) to establish the dependency and runtime baseline.
- Read [Backend selection](guides/backend-selection.md) before choosing Scrimage, JVips, or the Java 25 FFM binding.
- Follow the [Learning path](guides/learning-path.md) for an ordered route through processing, framework integration, OCR, and native execution.
- Open the [Repository map](architecture/repository-map.md) to see how the 15 release projects fit together.
- Read [Testing and operations](guides/testing-and-operations.md) before shipping native libraries, OCR data, storage, or CDN configuration.

## Responsibility boundary

The library owns image adapters, common operations, and framework wiring. The application still owns upload limits, accepted formats, file and object-store policy, native package installation, OCR language data, lifecycle shutdown, and production observability.

## Sources

- [Release project registry](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/settings.gradle.kts)
- [Release repository guide](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/README.md)
