---
manualId: "getting-started"
title: "Getting Started"
locale: "en"
releaseRef: "0.4.0"
---

# Getting Started

Begin with the smallest dependency and runtime that can complete one image task. Do not add every backend: Scrimage and libvips solve overlapping problems but have different deployment and ownership costs.

## 1. Import the central BOM

Choose the released <code>bluetape4k-dependencies</code> version used by the rest of the application.

    dependencies {
        implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<version>"))
        implementation("io.github.bluetape4k.image:bluetape4k-images")
    }

The repository also publishes <code>bluetape4k-image-bom</code>, but normal ecosystem consumers should use the central BOM so Kotlin, coroutines, framework, and sibling Bluetape libraries remain aligned. See the [Image BOM reference](modules/bluetape4k-image-bom.md) when maintaining a narrower standalone dependency graph.

## 2. Pick one execution path

- Choose [immutable image processing](modules/bluetape4k-images.md) for portable JVM loading, writing, filters, transforms, and analysis.
- Add [CAPTCHA](modules/bluetape4k-images-captcha.md), [OCR](modules/bluetape4k-images-ocr.md), [Ktor](modules/bluetape4k-images-ktor.md), or [Spring Boot](modules/bluetape4k-images-spring-boot.md) only when the application uses that capability.
- Choose [Java 21 JVips](modules/bluetape4k-images-vips-java21.md) when the service already runs JDK 21 and can install libvips.
- Choose [Java 25 FFM](modules/bluetape4k-images-vips-java25.md) when JDK 25 and native-access flags are acceptable.

Use [Backend selection](guides/backend-selection.md) for a fuller comparison.

## 3. Run a workshop

The [basic processing workshop](modules/basic-processing.md) is the shortest portable path. Framework applications can continue with the [Ktor image API](modules/ktor-image-api.md) or [Spring Boot image API](modules/spring-boot-image-api.md). OCR has separate [Ktor](modules/ktor-ocr-api.md) and [Spring Boot](modules/spring-boot-ocr-api.md) workshops because host Tesseract setup is part of the exercise.

## 4. Verify the real boundary

Run the target module test, not only the root compile:

    ./gradlew :bluetape4k-images:test

Native and OCR checks require host software and must run sequentially. Read [OCR setup](guides/ocr-setup.md) and [native resource lifecycle](guides/native-resource-lifecycle.md) before enabling them.

## Sources

- [Release build configuration](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/build.gradle.kts)
- [Release dependency examples](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/README.md#installation)
