---
manualId: "bluetape4k-images-vips-java25"
id: "bluetape4k-images-vips-java25"
title: "Java 25 FFM backend"
locale: "en"
kind: "library"
gradlePath: ":bluetape4k-images-vips-java25"
sourceDir: "images-vips-java25"
releaseRef: "0.3.0"
artifact: io.github.bluetape4k.image:bluetape4k-images-vips-java25
---

# Java 25 FFM backend

> Library module

## Problem {#problem}

This module implements the common libvips API with the Java Foreign Function & Memory API through `vips-ffm`. It is the Java 25 backend and provides native-file loading plus HEIF-family encoding when the host libvips build supports it.

## When to use it {#when-to-use}

Choose it for Java 25 services that can install libvips and configure native access. It is especially suitable for large local files because the `Path` factory calls libvips directly instead of materializing the complete compressed file in a JVM byte array.

## Coordinates {#coordinates}

Maven coordinate: `io.github.bluetape4k.image:bluetape4k-images-vips-java25`

Import `bluetape4k-dependencies`, compile against `bluetape4k-images-vips-api`, and select this backend at runtime.

## Core concepts {#concepts}

`FfmVipsRuntime` is process-wide and terminal. A root `FfmVipsImage` owns an `Arena.ofShared`; transform results share that arena and do not own it. Therefore a child image must never outlive its root/parent arena.

## Quick start {#quick-start}

```kotlin
FfmVipsRuntime.init(concurrency = 4)

ffmVipsImageOf(Path.of("input.jpg")).use { source ->
    val thumbnail = source.thumbnail(800)
    try {
        thumbnail.writeTo(Path.of("output.avif"), VipsImageFormat.AVIF)
    } finally {
        thumbnail.close()
    }
}
```

Start the JVM with `--enable-native-access=ALL-UNNAMED`.

## API by task {#api-by-task}

- Load through `ffmVipsImageOf`/`suspendFfmVipsImageOf`.
- Prefer the `Path` overload for local files; non-path streams are bounded and buffered.
- Resize, thumbnail, crop, and encode JPEG/PNG/WebP/AVIF/HEIC when native codecs exist.
- Scope every derived image inside the root image's lifetime.

## Recommended patterns {#patterns}

Create a root image per independent pipeline and close it after all derived work completes. Do not return a derived `VipsImage` from inside the root's `use` block. Validate file paths at the storage boundary and run native work with bounded concurrency.

## Integrations {#integrations}

The module implements `bluetape4k-images-vips-api` using `vips-ffm`. It requires Java 25 and system libvips. AVIF/HEIC additionally require a libvips build with libheif and relevant encoders.

## Configuration {#configuration}

Start with Java 25 and `--enable-native-access=ALL-UNNAMED`. On Homebrew macOS, make `/opt/homebrew/lib` visible through `DYLD_LIBRARY_PATH` when necessary. The same 50 MiB input and 150-million pixel defaults apply; `Path` checks file size before native loading.

## Failure modes {#failures}

Decode, operation, encode, and initialization failures use the common exception hierarchy. Missing native access is warned about at init and may fail in FFM calls. Arena cleanup failures are attached as suppressed exceptions. Initialization can retry after ordinary failure, but never after shutdown.

## Operations {#operations}

Monitor native memory as well as managed heap. The release benchmark showed very low Java-side allocation for geometry operations, but explicitly did not measure native libvips memory. Run Java 21 and Java 25 benchmark lanes sequentially to avoid native-runtime and CPU contention.

## Testing {#testing}

Run `./gradlew :bluetape4k-images-vips-java25:test` on Java 25. Gradle adds the native-access flag, isolates native test classes, and adds Homebrew's library path when present. Tests cover runtime concurrency, arena-backed operations, writers, properties, and golden images.

## Workshops and learning path {#workshops}

First prove native startup and parent/child lifetime in unit tests, then smoke-test each required codec on the production image, and finally run focused `-Pvips.impl=java25` benchmarks with representative assets.

## Limitations {#limitations}

Derived images share their root arena and cannot outlive it. Non-path input is buffered under the 50 MiB guard. AVIF/HEIC API availability does not prove host codec support. The backend cannot run on a Java 21 JVM.

<!-- release-readme-diagrams:start -->
## Release diagrams {#release-diagrams}

These diagrams are copied byte-for-byte from README assets in the `0.3.0` release tag. They describe this manual's released structure and runtime flows, not later Snapshot changes. Select a preview to open the SVG source.

### Performance vs scrimage diagram

[![Performance vs scrimage diagram](../../assets/readme-diagrams/images-vips-java25-architecture-02.png)](../../assets/readme-diagrams/images-vips-java25-architecture-02.svg)

_Release README: [`images-vips-java25/README.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images-vips-java25/README.md)_

### images vips java25 Class Structure diagram

[![images vips java25 Class Structure diagram](../../assets/readme-diagrams/images-vips-java25-class-01.png)](../../assets/readme-diagrams/images-vips-java25-class-01.svg)

_Release README: [`images-vips-java25/README.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images-vips-java25/README.md)_

<!-- release-readme-diagrams:end -->

## Sources {#sources}

- [Factories, direct path load, and guards](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-vips-java25/src/main/kotlin/io/bluetape4k/images/vips/java25/FfmVipsImageSupport.kt)
- [Arena ownership](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-vips-java25/src/main/kotlin/io/bluetape4k/images/vips/java25/FfmVipsImage.kt)
- [Runtime and native-access check](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-vips-java25/src/main/kotlin/io/bluetape4k/images/vips/java25/FfmVipsRuntime.kt)
- [Release build](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-vips-java25/build.gradle.kts)
