---
manualId: "bluetape4k-images-benchmark"
id: "bluetape4k-images-benchmark"
title: "Image processing benchmarks"
locale: "en"
kind: "benchmark"
gradlePath: ":bluetape4k-images-benchmark"
sourceDir: "benchmark/images-benchmark"
releaseRef: "0.4.0"
artifact: null
---

# Image processing benchmarks

> Performance benchmark

## Problem {#problem}

This non-published module keeps reproducible evidence for Scrimage and libvips decisions. It measures geometry, encoding, filter chains, IO boundaries, concurrent compressed-file IO, managed allocation, and complete large-file load-transform-write pipelines using `kotlinx-benchmark` on its JVM JMH backend.

## When to use it {#when-to-use}

Use it before claiming that a backend or IO boundary is faster, when checking a performance regression, or when choosing Scrimage versus libvips for a measured workload. Do not use one headline ratio as a production capacity estimate.

## Coordinates {#coordinates}

The benchmark is repository-only and publishes no artifact. Its project dependencies are aligned from the same `bluetape4k-dependencies` release catalog used by the 0.4.0 source.

## Core concepts {#concepts}

- `AverageTime` is lower-is-better; throughput is higher-is-better.
- Fixture, host, JVM, backend, warmups, iterations, fork count, command, and raw JSON belong to every comparison.
- Java 25 selects the FFM backend; `-Pvips.impl=java21` selects the Java 21 JNI backend.
- GC-profiler results cover managed heap only, not libvips native memory.
- Full pipelines provide stronger application evidence than isolated, potentially lazy geometry operations.

## Quick start {#quick-start}

For the Java 25 FFM lane, install JDK 25 and system libvips (`brew install vips` on macOS).

```bash
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmark \
  -Pvips.impl=java25 --console=plain
```

Focused large-file evidence:

```bash
./gradlew :bluetape4k-images-benchmark:benchmarkLargeStreamingBenchmark \
  -Pvips.impl=java25 --console=plain
```

Run backends sequentially on the same compatible host when comparing Java 21 JNI with Java 25 FFM.

## API by task {#api-by-task}

| Question | Benchmark |
| --- | --- |
| Resize/encode backend latency | `ImageResizeBenchmark`, `ImageEncodeBenchmark` |
| Scrimage filter cost | `ImageFilterBenchmark` |
| Chained transform allocation | `ImagePipelineBenchmark` |
| Path/stream/Okio/suspended boundary | `ImageIoBoundaryBenchmark` |
| Concurrent compressed-file throughput | `ImageFileIoThroughputBenchmark` |
| End-to-end large-file behavior | `ImageLargeStreamingBenchmark` |
| JNI versus FFM wrapper | `VipsBackendBenchmark`, `VipsBackendEncodeBenchmark` |

## Recommended patterns {#patterns}

Start with the production-shaped scenario, then isolate a suspected boundary. Preserve raw JSON and environment metadata. Re-run both candidates on one host, one JVM policy, and equivalent fixtures. Use native profiling in addition to JMH GC data for libvips lifetime questions.

## Integrations {#integrations}

The suite compares [`bluetape4k-images`](./bluetape4k-images.md) with the binding-neutral [`vips API`](./bluetape4k-images-vips-api.md) and the [Java 21 JNI](./bluetape4k-images-vips-java21.md) or [Java 25 FFM](./bluetape4k-images-vips-java25.md) runtime.

## Configuration {#configuration}

The complete benchmark uses three warmups, five measurements, one fork, average-time mode, and milliseconds. Focused configurations use one warmup and three one-second measurements. The build selects a JDK 21 or 25 toolchain from `vips.impl` and adds `--enable-native-access=ALL-UNNAMED` for benchmark forks.

## Failure modes {#failures}

- Near-zero vips rows: verify native availability; unavailable methods consume `null` and return immediately.
- Java 21 JNI skips on macOS arm64: the recorded 0.4.0 host found an incompatible x86_64 JVips dylib; use a compatible host.
- Large variance: avoid parallel benchmark processes, check thermal/load state, and keep fixtures and forks identical.
- Native load failure: verify system libvips and the FFM/JNI library path before interpreting results.

## Operations {#operations}

Representative 0.4.0 evidence on macOS arm64, GraalVM Java 25.0.3 found the full libvips `Path` pipeline at `7.13 ms/op` for `large-photo` and `5.47 ms/op` for `ocr-document`; Scrimage `Path` measured `223.19` and `145.13 ms/op`. These are local comparable snapshots, not a universal ranking. Scrimage suspended boundaries were slower in both boundary and many-file tests, so they are documented as lifecycle/integration APIs rather than performance optimizations.

## Testing {#testing}

Validate task wiring without paying for a full run:

```bash
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmark \
  -Pvips.impl=java25 --dry-run --console=plain
```

For publishable evidence, retain generated JSON, record the exact command/environment, update the dated report, and regenerate matching SVG/PNG charts.

## Workshops and learning path {#workshops}

1. Run [`basic-processing`](./basic-processing.md) to understand the user-facing operations.
2. Read the natural-photo result and benchmark source together.
3. Study IO-boundary and allocation reports before interpreting coroutine APIs.
4. Use the large-streaming report for the strongest end-to-end backend comparison.
5. Re-run the focused scenario on deployment-like hardware before deciding.

## Limitations {#limitations}

- The 0.4.0 reports mix a fresh macOS Java 25 run with explicitly historical Linux rows; they are not one experiment.
- The macOS arm64 run did not produce compatible Java 21 JNI measurements.
- `vips_resize` does not encode its result; libvips lazy evaluation means geometry-only speedups do not equal completed pixel-pipeline speedups.
- GC allocation excludes native memory.
- IO API convenience, latency, throughput, and backend selection are separate questions.
- No benchmark proves production behavior under different data, concurrency, hardware, JVM, codecs, or storage.

<!-- release-readme-diagrams:start -->
## Release diagrams {#release-diagrams}

These diagrams are loaded directly from README assets published with the `0.4.0` release and pinned to its immutable commit. They describe this manual's released structure and runtime flows, not later Snapshot changes. Select a preview to open the SVG at the same release commit.

### images benchmark Architecture diagram

[![images benchmark Architecture diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-image/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/images-benchmark-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/docs/images/readme-diagrams/images-benchmark-architecture-01.svg)

_Release README: [`benchmark/images-benchmark/README.md`](https://github.com/bluetape4k/bluetape4k-image/blob/ea5175b083babf8880f53cf80c9a264a0c61777e/benchmark/images-benchmark/README.md)_

<!-- release-readme-diagrams:end -->

## Sources {#sources}

- [Benchmark README](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/benchmark/images-benchmark/README.md)
- [Natural-photo results](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/benchmark/images-benchmark/docs/benchmark-results-2026-05-28-natural-photos.md)
- [IO-boundary baseline](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/benchmark/images-benchmark/docs/io-boundary-baseline-2026-05-29.md)
- [File IO throughput](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/benchmark/images-benchmark/docs/file-io-throughput-2026-05-29.md)
- [Memory profile](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/benchmark/images-benchmark/docs/memory-profile-2026-05-29.md)
- [Large streaming pipeline](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/benchmark/images-benchmark/docs/large-streaming-2026-06-05.md)
- [Gradle build file](https://github.com/bluetape4k/bluetape4k-image/blob/0.4.0/benchmark/images-benchmark/build.gradle.kts)
