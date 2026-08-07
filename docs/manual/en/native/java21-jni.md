---
manualId: "java21-jni"
title: "Java 21 JVips Backend"
locale: "en"
releaseRef: "0.4.0"
---

# Java 21 JVips Backend

<code>bluetape4k-images-vips-java21</code> implements the common Vips contracts with JVips/JNI. Choose it when the application must run on JDK 21 and can supply system libvips.

## Runtime requirements

Install libvips on the target host and make its shared libraries visible to the JVM. Linux and macOS packaging differ; run a startup probe on the same image used for production. The module compiles and tests with a Java 21 toolchain.

## Programming model

Initialize <code>JVipsRuntime</code>, load an image, close every <code>JVipsImage</code>, and shut the runtime down after workers drain. The module implements loading from paths, bytes and Okio sources, resize, thumbnail, crop, and JPEG/PNG/WebP/AVIF writers supported by the installed native stack.

The common API should remain the application-facing type. Keep <code>JVipsImage</code> and JVips binding details inside the backend adapter.

## Testing and failure

The release build isolates native tests with <code>forkEvery = 1</code> and <code>maxParallelForks = 1</code>. Preserve this because JNI and native global state can survive between tests. A missing library, unsupported codec, excessive pixel count, or use after runtime shutdown should fail explicitly.

Compare with [Java 25 FFM](java25-ffm.md) only on equivalent hosts and fixtures.

## Sources

- [JVipsRuntime](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images-vips-java21/src/main/kotlin/io/bluetape4k/images/vips/java21/JVipsRuntime.kt)
- [Java 21 module configuration](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/images-vips-java21/build.gradle.kts)
