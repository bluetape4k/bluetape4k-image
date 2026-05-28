# Image Pipeline Allocation Baseline (2026-05-29)

This report records the first allocation-sensitive baseline for
`ImagePipelineBenchmark`.

## Environment

| Item | Value |
|------|-------|
| Host | macOS arm64 |
| JVM | GraalVM Java 25.0.3 |
| Primary command | `JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkPipelineAllocationBenchmark --console=plain` |
| Primary raw JSON | [`raw/benchmark-pipeline-allocation-2026-05-29-macos-java25.json`](raw/benchmark-pipeline-allocation-2026-05-29-macos-java25.json) |
| Allocation addendum | [`raw/benchmark-pipeline-allocation-jmh-gc-2026-05-29-macos-java25.json`](raw/benchmark-pipeline-allocation-jmh-gc-2026-05-29-macos-java25.json) |

> The benchmark source and primary execution path use `kotlinx-benchmark`.
> On JVM, `kotlinx-benchmark` uses JMH as its backend. The Gradle DSL does not
> expose JMH profilers, so allocation values are recorded in the separate JMH GC
> profiler addendum.

## Fixtures

| Fixture | Source | Dimensions | Role |
|---------|--------|------------|------|
| `landscape.jpg` | `images/src/test/resources/images/landscape.jpg` | 4032x3024 | Natural photo preview |
| `homer.png` | `images/src/test/resources/images/homer.png` | 1248x702 | Illustration/document-style PNG path |

## Results

AverageTime is lower-is-better. `gc.alloc.rate.norm` is the allocation estimate
per operation from the JMH GC profiler.

| Benchmark | Pipeline | AverageTime | Allocation |
|-----------|----------|-------------|------------|
| `scrimage_photoPreviewJpeg` | 4032x3024 landscape -> resize 1280x720 -> grayscale -> JPEG | 113.82 ms/op | 53,217,235 B/op (50.75 MB/op) |
| `scrimage_documentPreviewPng` | 1248x702 homer -> resize 640x905 -> blur -> sepia -> PNG | 57.86 ms/op | 63,850,031 B/op (60.89 MB/op) |

![Image pipeline allocation benchmark chart](../../docs/images/readme-charts/images-benchmark-pipeline-allocation-chart-01.png)

## Interpretation

Both pipelines allocate tens of megabytes per operation. That makes chained
high-level scrimage transforms a useful regression target before considering
pipeline fusion or API guidance.

The adjacent production change also removes avoidable whole-file `ByteArray`
copies from coroutine `Path` load/write helpers and exposes `bluetape4k-okio`
`BufferedSource`/`BufferedSink` overloads for caller-owned streaming
boundaries. Scrimage decode/encode itself still owns the dominant intermediate
image allocation measured here.

The separate [`io-boundary-baseline-2026-05-29.md`](io-boundary-baseline-2026-05-29.md)
report adds baseline comparisons for `Path`, Okio, and
`SuspendedSource`/`SuspendedSink` file-channel boundaries. In that Scrimage
bridge benchmark the suspended file-channel path is semantically useful for
coroutine file IO, but it is not a latency optimization because Scrimage still
decodes and encodes through blocking streams.
