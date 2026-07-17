---
manualId: "bluetape4k-images"
id: "bluetape4k-images"
title: "Immutable image processing"
locale: "en"
kind: "library"
gradlePath: ":bluetape4k-images"
sourceDir: "images"
releaseRef: "0.3.0"
artifact: io.github.bluetape4k.image:bluetape4k-images
---

# Immutable image processing

> Library module

## Problem {#problem}

This module is the pure-JVM image-processing foundation. It wraps Scrimage and Java2D with Kotlin factories, immutable drawing, filter and transformation DSLs, suspend-friendly I/O, batch pipelines, thumbnails, tiles, analysis, and similarity metrics.

It is the default choice when native libvips setup is unnecessary or when a service needs rich Java2D/Scrimage operations rather than a narrow high-throughput resize pipeline.

## When to use it {#when-to-use}

Use it for loading common web images, composing filters and watermarks, generating thumbnails, bounded batch work, similarity analysis, SVG rasterization, and coroutine-facing encoders. Choose a `vips` backend instead when native throughput, native memory behavior, or HEIF-family codecs dominate the decision.

## Coordinates {#coordinates}

Maven coordinate: `io.github.bluetape4k.image:bluetape4k-images`

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<version>"))
    implementation("io.github.bluetape4k.image:bluetape4k-images")
}
```

## Core concepts {#concepts}

- `ImmutableImage` operations return new values. `withGraphics` draws on a copy and always disposes `Graphics2D`.
- `BufferedImage.useGraphics` is intentionally mutable; it disposes the graphics object but changes the receiver.
- Scrimage codecs are blocking. Suspend wrappers move them to `Dispatchers.IO`; they do not turn the codec into non-blocking I/O.
- Resource ownership is overload-specific: `BufferedSource` and `InputStream` remain caller-owned, while `Source` and `SuspendedSource` overloads buffer and close the source.
- Batch work is bounded by per-image pixels, in-flight pixels, and parallelism.

## Quick start {#quick-start}

```kotlin
val image = immutableImageOf(Path.of("photo.jpg"))

val output = image.applyFilters {
    brightness(1.08f)
    gaussianBlur(radius = 2)
    watermark("© bluetape4k")
}

output.suspendWrite(
    SuspendJpegWriter.Default.withCompression(85),
    Path.of("photo-ready.jpg"),
)
```

## API by task {#api-by-task}

- Load: `immutableImageOf` and `suspendImmutableImageOf` accept bytes, streams, Okio sources, files, and paths.
- Filter: `applyFilters` composes blur, color, effect, style, border, caption, and watermark operations.
- Transform: use `autoCrop`, `smartCropTo`, `clahe`, `perspectiveTransform`, rotation, and flips.
- Compare: use perceptual hashes, histogram similarity, MSE, PSNR, SSIM/MSSIM, or block descriptors.
- Batch: `processImages` returns `ImageBatchResult`; select a writer in `ImageProcessingDsl` and finish with `writeImagesTo`.
- Generate variants: configure `ThumbnailPipeline` or split/process/merge through `TileProcessor`.

## Recommended patterns {#patterns}

Prefer `ImmutableImage` at application boundaries. For batch jobs, probe dimensions before full decode, keep `skipFailures=false` for fail-fast jobs, and enable it only with an `onFailure` observer. Keep output names relative: `writeImagesTo` rejects paths that normalize outside its output directory.

Use suspend Okio overloads for lifecycle consistency when the caller already owns that boundary, not as a latency optimization. Release benchmarks found the `Path` boundary faster for the measured local workloads.

## Integrations {#integrations}

CAPTCHA, OCR, and Ktor modules build on this module. Spring storage is separate from the processing DSL. `images-vips-api` is a different binding-neutral native surface rather than a drop-in implementation of Scrimage APIs.

## Configuration {#configuration}

`ImageProcessingOptions` defaults to available-processor parallelism, 16,777,216 pixels per image, and 33,554,432 in-flight pixels. `largeJobs()` raises the bounds explicitly. The default maximum tile count is 65,536.

`BatikSvgRasterizer` blocks external entities and DTD loading, disables external resources by default, and enforces dimensions and timeout. Keep those defaults for untrusted SVG.

## Failure modes {#failures}

Batch failures identify `VALIDATION`, `LOAD`, `TRANSFORM`, or `WRITE` through `ImageBatchException`/`ImageBatchResult.Failure`. Decode and writer exceptions otherwise propagate from Scrimage or ImageIO. Cancellation is rethrown rather than converted into a skipped item.

## Operations {#operations}

Monitor failure counts by stage and actual image dimensions, not only compressed byte size. Set pixel budgets from production traffic. Golden-image updates are opt-in; do not enable `bluetape4k.images.golden.update` in ordinary CI.

## Testing {#testing}

The release suite covers factories and ownership, filters, properties, golden outputs, batch limits, thumbnail and tile pipelines, similarity, SVG security, and suspend writers. Run `./gradlew :bluetape4k-images:test` for the pure-JVM lane.

## Workshops and learning path {#workshops}

Begin with `examples/basic-processing`, then study `ImageProcessingDsl`, `ThumbnailPipeline`, and `TileProcessor`. Read the benchmark reports before choosing libvips solely for speed; the published figures are workload- and host-specific.

## Limitations {#limitations}

Suspend wrappers still bridge to blocking codecs. AVIF/HEIC types in this module are incubating contracts without a pure-JVM implementation in `0.3.0`. Compressed-byte limits do not replace pixel limits, and ImageIO codec availability can vary with the runtime classpath.

<!-- release-readme-diagrams:start -->
## Release diagrams {#release-diagrams}

These diagrams are copied byte-for-byte from README assets in the `0.3.0` release tag. They describe this manual's released structure and runtime flows, not later Snapshot changes. Select a preview to open the SVG source.

### Processing Pipeline diagram

[![Processing Pipeline diagram](../../assets/readme-diagrams/images-architecture-01.png)](../../assets/readme-diagrams/images-architecture-01.svg)

_Release README: [`images/README.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images/README.md)_

### Transform Architecture diagram

[![Transform Architecture diagram](../../assets/readme-diagrams/images-architecture-03.png)](../../assets/readme-diagrams/images-architecture-03.svg)

_Release README: [`images/README.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images/README.md)_

### Image Analysis diagram

[![Image Analysis diagram](../../assets/readme-diagrams/images-class-04.png)](../../assets/readme-diagrams/images-class-04.svg)

_Release README: [`images/README.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images/README.md)_

### Images Core API Classes diagram

[![Images Core API Classes diagram](../../assets/readme-diagrams/images-class-core-01.png)](../../assets/readme-diagrams/images-class-core-01.svg)

_Release README: [`images/README.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images/README.md)_

### Images Filter Classes diagram

[![Images Filter Classes diagram](../../assets/readme-diagrams/images-class-filters-01.png)](../../assets/readme-diagrams/images-class-filters-01.svg)

_Release README: [`images/README.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images/README.md)_

### Images Writer Classes diagram

[![Images Writer Classes diagram](../../assets/readme-diagrams/images-class-writers-01.png)](../../assets/readme-diagrams/images-class-writers-01.svg)

_Release README: [`images/README.md`](https://github.com/bluetape4k/bluetape4k-image/blob/a571c30004f571fe8cfcddc29670c1404d212ec6/images/README.md)_

<!-- release-readme-diagrams:end -->

## Sources {#sources}

- [Immutable image factories and ownership](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images/src/main/kotlin/io/bluetape4k/images/ImmutableImageSupport.kt)
- [Batch result and options](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images/src/main/kotlin/io/bluetape4k/images/batch/ImageBatchModels.kt)
- [Batch flow implementation](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images/src/main/kotlin/io/bluetape4k/images/batch/ImageBatchFlow.kt)
- [Processing DSL](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images/src/main/kotlin/io/bluetape4k/images/batch/ImageProcessingDsl.kt)
- [Release build](https://github.com/bluetape4k/bluetape4k-image/blob/0.3.0/images/build.gradle.kts)
