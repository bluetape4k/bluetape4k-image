---
manualId: "java25-ffm"
title: "Java 25 FFM Backend"
locale: "en"
releaseRef: "0.3.0"
---

# Java 25 FFM Backend

<code>bluetape4k-images-vips-java25</code> implements the common Vips API with the vips-ffm binding and Java's Foreign Function and Memory API.

## Runtime requirements

The module requires a Java 25 toolchain, system libvips, and:

    --enable-native-access=ALL-UNNAMED

On macOS with Homebrew, the Gradle test config exposes <code>/opt/homebrew/lib</code> through <code>DYLD_LIBRARY_PATH</code> when present. Production launch configuration must provide an equivalent library path rather than depending on the Gradle test environment.

## Programming model

Use <code>FfmVipsRuntime</code> and <code>FfmVipsImage</code> behind the common API. Close source and derived images with <code>use</code>. Supported operations include loading, resize, thumbnail, crop, coroutine helpers, Okio sources, and JPEG/PNG/WebP/HEIF-family writing as supported by the native installation.

The module disables AtomicFU JVM transformation because the FFM dependency uses Java 25 class files that a Java 21 build JVM cannot transform safely. Preserve this build constraint.

## Choose deliberately

Use this backend only when JDK 25 and the native-access flag are accepted deployment requirements. Do not choose it solely from repository benchmark numbers; validate startup, codecs, memory, and throughput in the target container or host.

## Sources

- [FfmVipsRuntime](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images-vips-java25/src/main/kotlin/io/bluetape4k/images/vips/java25/FfmVipsRuntime.kt)
- [Java 25 module configuration](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images-vips-java25/build.gradle.kts)
