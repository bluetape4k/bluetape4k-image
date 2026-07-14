---
manualId: "backend-selection"
title: "Backend Selection"
locale: "en"
releaseRef: "0.3.0"
---

# Backend Selection

Choose a backend from deployment constraints and measured workload, not from the expectation that native code is always faster.

| Path | Best fit | Runtime cost | Ownership |
|---|---|---|---|
| <code>bluetape4k-images</code> | portable filters, transforms, analysis, JVM services | JDK 21; no libvips | JVM values and caller-owned streams |
| Java 21 JVips | native resize, thumbnail, crop, encode on JDK 21 | system libvips and JNI | close every <code>VipsImage</code> |
| Java 25 FFM | native processing on a JDK 25 deployment | system libvips and native-access flag | close every image and manage runtime shutdown |

## Choose Scrimage when

The application needs the broad immutable-image helpers, Java2D drawing, filter DSL, similarity algorithms, OCR input, or a deployment with no native package. It is also the simplest first implementation and the reference path for [the basic workshop](../modules/basic-processing.md).

## Choose libvips when

The workload is dominated by native-supported resize, crop, thumbnail, or encoding operations and the service can install and monitor libvips. Validate the exact codec on the target host. Library support and the host libvips build are separate facts.

Choose [Java 21 JNI](../native/java21-jni.md) if the application must stay on JDK 21. Choose [Java 25 FFM](../native/java25-ffm.md) only if JDK 25 and <code>--enable-native-access=ALL-UNNAMED</code> are part of the deployment contract.

## Avoid accidental dual backends

Do not place both native implementations on the normal runtime classpath merely for convenience. Keep the common [Vips API](../native/vips-api.md) at compile time and select one implementation deliberately. A migration can run both in a benchmark or validation environment, but production ownership must remain explicit.

## Decide with evidence

Use the repository benchmark as directional evidence, then measure representative images, concurrency, file and network boundaries, and memory in the real service. See [performance selection](performance-selection.md).

## Sources

- [Release backend overview](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/README.md#architecture)
- [Benchmark documentation](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/benchmark/images-benchmark/README.md)
