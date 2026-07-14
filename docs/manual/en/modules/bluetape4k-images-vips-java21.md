---
manualId: "bluetape4k-images-vips-java21"
id: "bluetape4k-images-vips-java21"
title: "Java 21 JVips backend"
locale: "en"
kind: "library"
gradlePath: ":bluetape4k-images-vips-java21"
sourceDir: "images-vips-java21"
releaseRef: "0.3.0"
artifact: io.github.bluetape4k.image:bluetape4k-images-vips-java21
---

# Java 21 JVips backend

> Library module

## Problem {#problem}

This module implements the common libvips API with JVips and JNI on Java 21. It is the native option for services that stay on the Java 21 toolchain.

## When to use it {#when-to-use}

Choose it when Java 21 compatibility is required and a matching JVips/libvips native library is available. Prefer the pure-JVM module when native deployment is undesirable; evaluate the Java 25 backend separately rather than assuming one binding is universally faster.

## Coordinates {#coordinates}

Maven coordinate: `io.github.bluetape4k.image:bluetape4k-images-vips-java21`

Import `bluetape4k-dependencies`, compile against `bluetape4k-images-vips-api`, and add this artifact as the selected backend.

## Core concepts {#concepts}

`JVipsRuntime` is a CAS-based process singleton. `vipsImageOf` validates bytes, format, decode result, and pixel count. `JVipsImage` clones the native image before resize/thumbnail/crop, so each returned image owns an independent `NativeHandle`.

## Quick start {#quick-start}

```kotlin
JVipsRuntime.init(concurrency = 4)

vipsImageOf(Path.of("input.jpg")).use { source ->
    source.thumbnail(800).use { thumbnail ->
        thumbnail.writeTo(Path.of("output.webp"), VipsImageFormat.WEBP)
    }
}
```

## API by task {#api-by-task}

- Load bytes, files, paths, streams, Okio sources, and suspended sources with `vipsImageOf`/`suspendVipsImageOf`.
- Resize, thumbnail, crop, and encode JPEG, PNG, WebP, or capability-gated AVIF.
- Close each source and transformed image independently.
- Shut the runtime down only when the JVM is terminating.

## Recommended patterns {#patterns}

Use `Path` only after validating it against an allowed application root. Keep `BufferedSource` caller-owned; the plain `Source` overload closes it. Bound parallel work at the application layer and never share an image across threads.

## Integrations {#integrations}

The module implements `bluetape4k-images-vips-api` and uses JVips internally without exposing its classes. It needs system libvips/JVips native compatibility at runtime.

## Configuration {#configuration}

Run on Java 21. Inputs are capped at 50 MiB, formats are magic-byte allowlisted to JPEG/PNG/WebP/AVIF/HEIC, and decoded pixels are checked against `JVipsRuntime.maxPixels` (150 million by default). Stream and path inputs are materialized into bytes in `0.3.0`.

## Failure modes {#failures}

Unsupported/corrupt/oversize inputs become `VipsDecodeException`; geometry failures become `VipsOperationException`; encode failures become `VipsEncodeException`. `Error` during initialization is rethrown after state recovery; ordinary failures allow a retry. After `shutdown`, initialization always fails.

## Operations {#operations}

Install libvips and verify the JVM/native architecture match. JNI tests run one class per fork with one parallel fork to isolate native state. On the release macOS arm64 benchmark host, the available JVips dylib was x86_64, so JNI measurements were correctly reported as unavailable.

## Testing {#testing}

Run `./gradlew :bluetape4k-images-vips-java21:test`. Tests auto-detect libvips and skip if initialization fails; use `-Dvips.enabled=false` only for explicit opt-out. The suite covers runtime concurrency, operations, writers, properties, and golden outputs.

## Workshops and learning path {#workshops}

Start with the runtime/image tests on the deployment architecture, add a small encode smoke test, then run the benchmark sequentially with `-Pvips.impl=java21`.

## Limitations {#limitations}

HEIC encoding is explicitly unsupported by this backend in `0.3.0`. AVIF and HEIF decoding/AVIF encoding still depend on host codec capability. Path loads read the complete compressed file after enforcing the 50 MiB bound.

## Sources {#sources}

- [Factories and guards](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-vips-java21/src/main/kotlin/io/bluetape4k/images/vips/java21/JVipsImageSupport.kt)
- [JNI image lifecycle](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-vips-java21/src/main/kotlin/io/bluetape4k/images/vips/java21/JVipsImage.kt)
- [Runtime lifecycle](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-vips-java21/src/main/kotlin/io/bluetape4k/images/vips/java21/JVipsRuntime.kt)
- [Release build and test isolation](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-vips-java21/build.gradle.kts)
