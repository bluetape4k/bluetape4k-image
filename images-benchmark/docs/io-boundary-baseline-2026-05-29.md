# Image IO Boundary Baseline (2026-05-29)

This report compares image load/write boundary choices after adding
`bluetape4k-okio` `Source`/`Sink` and `SuspendedSource`/`SuspendedSink`
overloads.

## Environment

| Item | Value |
|------|-------|
| Host | macOS arm64 |
| JVM | GraalVM Java 25.0.3 |
| Primary command | `JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkIoBoundaryBenchmark --console=plain` |
| Primary raw JSON | [`raw/benchmark-io-boundary-2026-05-29-macos-java25.json`](raw/benchmark-io-boundary-2026-05-29-macos-java25.json) |
| Allocation addendum | [`raw/benchmark-io-boundary-jmh-gc-2026-05-29-macos-java25.json`](raw/benchmark-io-boundary-jmh-gc-2026-05-29-macos-java25.json) |

> The benchmark source and primary execution path use `kotlinx-benchmark`.
> On JVM, `kotlinx-benchmark` uses JMH as its backend. The GC-profiler file is a
> direct JMH addendum because the Gradle DSL does not expose profiler arguments.

## Fixtures

| Fixture | Source | Dimensions | Role |
|---------|--------|------------|------|
| `homer.jpg` | `images/src/test/resources/images/homer.jpg` | 1248x702 | Small illustration-style load/write boundary |
| `landscape.jpg` | `images/src/test/resources/images/landscape.jpg` | 4032x3024 | Natural photo load boundary |

## Results

AverageTime is lower-is-better. Allocation is `gc.alloc.rate.norm` from the JMH
GC-profiler addendum.

| Benchmark | Boundary | AverageTime | Allocation |
|-----------|----------|-------------|------------|
| `load_homer_byteArray` | `ByteArray` baseline | 7.70 ms/op | 5.42 MB/op |
| `load_homer_inputStream` | `InputStream` baseline | 7.81 ms/op | 5.62 MB/op |
| `load_homer_path` | `Path` baseline | 7.78 ms/op | 5.50 MB/op |
| `load_homer_okioSource` | Okio `Source` | 8.23 ms/op | 5.65 MB/op |
| `load_homer_suspendedFileSource` | `AsynchronousFileChannel` `SuspendedSource` | 10.81 ms/op | 5.76 MB/op |
| `load_landscape_path` | `Path` baseline | 152.22 ms/op | 76.88 MB/op |
| `load_landscape_suspendedFileSource` | `AsynchronousFileChannel` `SuspendedSource` | 216.62 ms/op | 86.34 MB/op |
| `write_homer_byteArray` | `ByteArray` baseline | 6.90 ms/op | 2.89 MB/op |
| `write_homer_outputStream` | `OutputStream` baseline | 7.35 ms/op | 2.72 MB/op |
| `write_homer_path` | `Path` baseline | 7.35 ms/op | 2.72 MB/op |
| `write_homer_okioSink` | Okio `Sink` | 7.40 ms/op | 2.73 MB/op |
| `write_homer_suspendedFileSink` | `AsynchronousFileChannel` `SuspendedSink` | 14.03 ms/op | 2.82 MB/op |

![Image IO boundary benchmark chart](../../docs/images/readme-charts/images-benchmark-io-boundary-chart-01.png)

## Interpretation

The baseline matters: `SuspendedSource` and `SuspendedSink` are not faster in
this Scrimage boundary benchmark. Scrimage decode/encode still requires
blocking `InputStream`/`OutputStream` adapters, so the suspended file channel
path adds coroutine-to-blocking bridge cost instead of removing Scrimage's
dominant decode/encode work.

The practical conclusion is to keep the new suspended overloads for coroutine
file IO ergonomics and lifecycle consistency, but not to market them as a
latency optimization for Scrimage-backed load/write paths. For pure streaming
pipelines where the caller already owns a `SuspendedSource` or `SuspendedSink`,
the overloads avoid forcing callers back through ad hoc byte-array staging.
