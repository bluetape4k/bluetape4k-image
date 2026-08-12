---
manualId: "vips-api"
title: "libvips Common API"
locale: "en"
releaseRef: "0.4.0"
---

# libvips Common API

<code>bluetape4k-images-vips-api</code> separates image-processing contracts from the JDK 25 JVips JNI and JDK 25 FFM bindings. Compile application code against <code>VipsImage</code> and <code>VipsRuntime</code>, then select one implementation at runtime. The JNI artifact keeps the legacy <code>java21</code> name for compatibility.

## Core contracts

<code>VipsRuntime</code> initializes the native library and loads images. <code>VipsImage</code> exposes dimensions, resize, thumbnail, crop, encode, and close. Format and writer option types keep binding classes out of the public application boundary. Okio support writes encoded output to sinks.

Every image is closeable. Operations that return a <code>VipsImage</code> transfer ownership of a new native object to the caller. See [native resource lifecycle](../guides/native-resource-lifecycle.md).

## Binding-neutral application design

Keep backend selection in composition code:

- business code accepts the common runtime or a smaller application adapter;
- startup initializes exactly one backend;
- processing code closes source and derived images;
- shutdown runs after requests and workers stop.

Do not expose JVips or FFM binding types from public services unless the application intentionally depends on one backend.

## Security boundary

Validate path roots, encoded input size, output options, and allowed formats before calling the native implementation. Native code does not remove the need for application limits. Error messages returned to clients should not expose filesystem paths or native loader details.

## Sources

- [VipsImage](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/VipsImage.kt)
- [VipsRuntime](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/VipsRuntime.kt)
