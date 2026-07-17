---
manualId: "basic-processing"
id: "basic-processing"
title: "Basic processing workshop"
locale: "en"
kind: "example"
gradlePath: ":basic-processing"
sourceDir: "examples/basic-processing"
releaseRef: "0.3.0"
artifact: null
---

# Basic processing workshop

> Runnable example

## Problem {#problem}

This workshop turns three bundled images into five deterministic outputs without a web framework or native runtime. It demonstrates file-backed loading, bounded resizing, saliency-aware smart cropping, format conversion, a text watermark, and suspend-aware encoding.

## When to use it {#when-to-use}

Start here when learning `bluetape4k-images`, validating a workstation, or reducing a processing problem to a small JVM-only reproduction. It is also the best baseline before comparing the same workload with libvips.

## Coordinates {#coordinates}

This runnable example is not published. Consumers should select one `bluetape4k-dependencies` release-train version and then add `bluetape4k-images` without managing an image-module version separately.

## Core concepts {#concepts}

- `suspendLoadImage(Path)` reads a file-backed resource without an application-owned compressed `ByteArray` copy.
- `fit` preserves aspect ratio; `smartCropTo` fills an exact target using saliency-aware cropping.
- `withGraphics` adds a watermark while keeping the transformation in the immutable-image pipeline.
- `suspendWrite` chooses a JPEG or PNG writer from the destination extension.

## Quick start {#quick-start}

Prerequisite: JDK 21 or newer. No service or native package is required.

```bash
./gradlew :basic-processing:run
./gradlew :basic-processing:run --args="/tmp/bluetape4k-basic-processing"
```

The default directory is `build/tmp/basic-processing`. The run produces a `320x240` thumbnail, a `640x360` smart crop, an `800x600` PNG conversion, and two `960x540` previews.

## API by task {#api-by-task}

| Task | Release implementation |
| --- | --- |
| Load fixtures | `suspendLoadImage(resourcePath(...))` |
| Preserve aspect ratio | `image.fit(width, height)` |
| Produce an exact frame | `image.smartCropTo(640, 360)` |
| Draw a watermark | `image.withGraphics { graphics -> ... }` |
| Encode to a path | `image.suspendWrite(writer, output)` |

## Recommended patterns {#patterns}

Keep path resolution, transformation, and encoding as separate steps. Select the writer explicitly, bound dimensions before expensive work, and return metadata such as width, height, byte count, and output path so tests can assert observable results.

## Integrations {#integrations}

The workshop depends only on `bluetape4k-images` and Kotlin coroutines. Continue to the Spring Boot or Ktor workshops when the processing function must be exposed over HTTP; continue to the vips modules when native acceleration is justified by measured workloads.

## Configuration {#configuration}

The first command uses `build/tmp/basic-processing`; the first program argument overrides it. Source fixtures are included by the example build from `images/src/test/resources` and `docs/images`.

## Failure modes {#failures}

- `Example resource is missing`: run from the repository Gradle project so its resource source sets are assembled.
- A resource is not a `file:` URL: the release example deliberately uses path-based loading; use the library stream/byte APIs for packaged-jar resources.
- Unexpected dimensions: distinguish aspect-preserving `fit` from exact-frame `smartCropTo`.
- Empty or unreadable output: inspect the selected writer and destination permissions, then run the deterministic test.

## Operations {#operations}

The command prints every filename, final dimensions, byte count, and the normalized output directory. Treat these as the smoke-test contract rather than comparing compressed byte counts, which can vary with encoder/runtime changes.

## Testing {#testing}

```bash
./gradlew :basic-processing:test
```

The test invokes the same generator, asserts five non-empty files, decodes every result, and checks all expected dimensions.

## Workshops and learning path {#workshops}

1. Run this workshop and inspect all five outputs.
2. Read the [`bluetape4k-images` module](./bluetape4k-images.md) to understand the underlying load, transform, and writer APIs.
3. Choose [`spring-boot-image-api`](./spring-boot-image-api.md) or [`ktor-image-api`](./ktor-image-api.md) for HTTP integration.
4. Use the [image benchmark](./bluetape4k-images-benchmark.md) before moving a production workload to libvips.

## Limitations {#limitations}

This example is a single-process file generator. It does not cover upload validation, storage policy, native codecs, backpressure, authentication, or public delivery URLs.

<!-- release-readme-diagrams:start -->
## Release diagrams {#release-diagrams}

These diagrams are copied byte-for-byte from README assets in the `0.3.0` release tag. They describe this manual's released structure and runtime flows, not later Snapshot changes. Select a preview to open the SVG source.

### Basic Processing Architecture

[![Basic Processing Architecture](../../assets/readme-diagrams/examples-basic-processing-architecture-01.png)](../../assets/readme-diagrams/examples-basic-processing-architecture-01.svg)

_Release README: [`examples/basic-processing/README.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/examples/basic-processing/README.md)_

### Basic Processing Scenario

[![Basic Processing Scenario](../../assets/readme-diagrams/examples-basic-processing-scenario-01.png)](../../assets/readme-diagrams/examples-basic-processing-scenario-01.svg)

_Release README: [`examples/basic-processing/README.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/examples/basic-processing/README.md)_

### Basic Processing Sequence

[![Basic Processing Sequence](../../assets/readme-diagrams/examples-basic-processing-sequence-01.png)](../../assets/readme-diagrams/examples-basic-processing-sequence-01.svg)

_Release README: [`examples/basic-processing/README.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/examples/basic-processing/README.md)_

<!-- release-readme-diagrams:end -->

## Sources {#sources}

- [Release README](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/examples/basic-processing/README.md)
- [Quickstart source](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/examples/basic-processing/src/main/kotlin/io/bluetape4k/images/examples/basic/BasicImageProcessingQuickstart.kt)
- [Deterministic test](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/examples/basic-processing/src/test/kotlin/io/bluetape4k/images/examples/basic/BasicImageProcessingQuickstartTest.kt)
- [Gradle build file](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/examples/basic-processing/build.gradle.kts)
