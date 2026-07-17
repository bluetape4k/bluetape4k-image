---
manualId: "bluetape4k-image-bom"
id: "bluetape4k-image-bom"
title: "Image BOM"
locale: "en"
kind: "library"
gradlePath: ":bluetape4k-image-bom"
sourceDir: "bom"
releaseRef: "0.3.0"
artifact: io.github.bluetape4k.image:bluetape4k-image-bom
---

# Image BOM

> Library module

## Problem {#problem}

The image repository publishes eight libraries with different JVM and native-runtime requirements. The BOM aligns those artifact versions so an application cannot accidentally combine an API jar from one release with a backend jar from another.

Most applications should import the ecosystem-wide `bluetape4k-dependencies` BOM. It already coordinates the image release with the rest of bluetape4k, so users select one dependency-platform version rather than an image-specific version.

## When to use it {#when-to-use}

Use the image BOM directly only when this repository is consumed in isolation and the wider bluetape4k platform is intentionally not used. It is also useful for dependency-analysis tests that verify all image artifacts resolve to the same release.

## Coordinates {#coordinates}

Maven coordinate: `io.github.bluetape4k.image:bluetape4k-image-bom`

Preferred consumer platform:

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<version>"))
}
```

## Core concepts {#concepts}

This is a Gradle `java-platform`; it contains dependency constraints and no runtime classes. The release build derives its constraints from every published subproject while excluding examples, benchmarks, and the BOM itself.

The constrained artifacts are `bluetape4k-images`, `-captcha`, `-ocr`, `-ktor`, `-spring-boot`, `-vips-api`, `-vips-java21`, and `-vips-java25`.

## Quick start {#quick-start}

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<version>"))
    implementation("io.github.bluetape4k.image:bluetape4k-images")
    implementation("io.github.bluetape4k.image:bluetape4k-images-vips-api")
    runtimeOnly("io.github.bluetape4k.image:bluetape4k-images-vips-java25")
}
```

Choose either the Java 21 JNI backend or the Java 25 FFM backend for one runtime. Do not add both merely because both are constrained.

## API by task {#api-by-task}

- Align pure-JVM processing: add `bluetape4k-images`.
- Add service adapters: select CAPTCHA, OCR, Ktor, or Spring Boot independently.
- Use native acceleration: compile against `bluetape4k-images-vips-api` and add one backend.
- Inspect alignment: run Gradle `dependencyInsight` for an image coordinate.

## Recommended patterns {#patterns}

Keep the platform declaration near the root dependency convention and omit versions from individual image modules. Let `bluetape4k-dependencies` coordinate image, AWS, Ktor, Spring Boot, and Kotlin versions together.

## Integrations {#integrations}

The image BOM is aggregated by `bluetape4k-dependencies`. Native libraries such as libvips and Tesseract are operating-system dependencies; a Maven BOM cannot install or validate them.

## Configuration {#configuration}

The BOM has no runtime settings. Repository snapshot consumers additionally need the Sonatype Central snapshots repository, but stable `0.3.0` consumers use Maven Central through the central platform.

## Failure modes {#failures}

An omitted platform usually appears as unresolved versions. Conflicting versions appear in `dependencyInsight`; fix the platform/import boundary instead of pinning each module. A resolved dependency does not prove that libvips, Tesseract, language data, or required JVM flags exist.

## Operations {#operations}

Record the selected `bluetape4k-dependencies` version in the application, not a matrix of image submodule versions. During upgrades, resolve the graph and run the native smoke tests required by the selected backend.

## Testing {#testing}

Use dependency-lock or resolution tests to assert a single image version. Functional tests belong to the selected libraries; the platform itself has no executable behavior.

## Workshops and learning path {#workshops}

Start with `bluetape4k-images`, then add one service adapter or one libvips backend according to the workload. Use the repository examples and benchmark only after the dependency graph is aligned.

## Limitations {#limitations}

The BOM aligns artifacts; it does not make Java 25 bytecode run on Java 21, provide native codecs, or make optional Spring/AWS classes available. The module list in the release BOM README is incomplete; the release `bom/build.gradle.kts` constraint loop is authoritative.

<!-- release-readme-diagrams:start -->
## Release diagrams {#release-diagrams}

These diagrams are copied byte-for-byte from README assets in the `0.3.0` release tag. They describe this manual's released structure and runtime flows, not later Snapshot changes. Select a preview to open the SVG source.

### bom Architecture diagram

[![bom Architecture diagram](../../assets/readme-diagrams/bom-architecture-01.png)](../../assets/readme-diagrams/bom-architecture-01.svg)

_Release README: [`bom/README.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/bom/README.md)_

<!-- release-readme-diagrams:end -->

## Sources {#sources}

- [Release BOM build](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/bom/build.gradle.kts)
- [Release module registry](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/settings.gradle.kts)
