---
manualId: "bluetape4k-images-vips-api"
id: "bluetape4k-images-vips-api"
title: "libvips common API"
locale: "en"
kind: "library"
gradlePath: ":bluetape4k-images-vips-api"
sourceDir: "images-vips-api"
releaseRef: "0.3.0"
artifact: io.github.bluetape4k.image:bluetape4k-images-vips-api
---

# libvips common API

> Library module

## Problem {#problem}

This module is the binding-neutral contract for libvips processing. Applications can compile against `VipsImage` and `VipsRuntime`, then select the Java 21 JNI or Java 25 FFM backend without exposing binding classes in domain code.

## When to use it {#when-to-use}

Use it for high-throughput resize, thumbnail, crop, and encode pipelines that can accept a native runtime. Keep using Scrimage when Java2D drawing, the rich filter DSL, or a native-free deployment is more important.

## Coordinates {#coordinates}

Maven coordinate: `io.github.bluetape4k.image:bluetape4k-images-vips-api`

Add this API plus exactly one runtime backend through the centrally versioned platform.

## Core concepts {#concepts}

`VipsImage` owns native resources and is single-thread oriented. Every transform returns another closeable image. `VipsRuntime` manages process-wide libvips initialization and a terminal shutdown. `VipsEncodeOptions` standardizes quality, effort, lossless mode, and metadata stripping.

## Quick start {#quick-start}

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<version>"))
    implementation("io.github.bluetape4k.image:bluetape4k-images-vips-api")
    runtimeOnly("io.github.bluetape4k.image:bluetape4k-images-vips-java25")
}
```

Backend factories create the concrete image; always scope it with `use` and close transformed results as required by that backend's ownership model.

## API by task {#api-by-task}

- Inspect `width`, `height`, and `bands`.
- Transform with `resize`, `thumbnail`, or `crop`.
- Encode with `toBytes` or `writeTo(Path|OutputStream)`.
- Use Okio and coroutine write extensions for an existing source/sink boundary.
- Initialize with `VipsRuntime.init(concurrency, maxPixels)` and inspect `isInitialized`/`isShutdown`.

## Recommended patterns {#patterns}

Initialize once near process startup and register shutdown only with a JVM shutdown hook. Never place `shutdown()` in a Spring `@PreDestroy` hook when devtools can restart the context. Do not share a `VipsImage` instance across concurrent coroutines.

## Integrations {#integrations}

`images-vips-java21` and `images-vips-java25` implement this API. The API module also exports golden-test fixtures for backend parity and depends on `bluetape4k-images` for the incubating annotation/test comparison surface.

## Configuration {#configuration}

`VipsLimits` caps compressed input at 50 MiB and defaults runtime validation to 150,000,000 `width × height × bands`. `init` defaults to four libvips threads. `VipsEncodeOptions` accepts quality 0..100 and effort 1..9; default is 85/4 with metadata stripping.

## Failure modes {#failures}

Use `VipsDecodeException`, `VipsEncodeException`, `VipsOperationException`, and `VipsInitializationException` to separate stages. Messages are sanitized; native errors remain in the cause. Calling `init` after terminal shutdown requires a process restart.

## Operations {#operations}

Monitor decode rejection by category, native initialization, operation/encode latency, and process native memory. Managed-heap benchmarks do not measure libvips allocations. Validate output paths before `writeTo`; the API does not provide a storage-root traversal guard.

## Testing {#testing}

The API tests cover option validation and Okio ownership. Backend golden, property, writer, and concurrency tests live in each implementation module. Run native suites sequentially on a host with matching libvips support.

## Workshops and learning path {#workshops}

Read the API first, then the lifecycle guide for the chosen backend, then compare benchmark evidence. The Java 21 and Java 25 factories have deliberately different names, making backend selection visible at composition time.

## Limitations {#limitations}

The common API does not discover or instantiate a backend. AVIF/HEIC support depends on both the implementation and host libvips codecs. `shutdown()` is irreversible, and transformed image ownership differs between JNI and FFM—read the backend page before composing nested `use` blocks.

## Sources {#sources}

- [VipsImage contract](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/VipsImage.kt)
- [Runtime terminal contract](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/VipsRuntime.kt)
- [Limits](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/VipsLimits.kt)
- [Exception policy](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/VipsExceptions.kt)
