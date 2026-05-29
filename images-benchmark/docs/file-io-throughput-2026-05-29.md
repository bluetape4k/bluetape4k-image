# Concurrent Image File IO Throughput Baseline (2026-05-29)

This report tests the follow-up hypothesis that `SuspendedSource` and
`SuspendedSink` may show higher throughput when many compressed image files are
handled concurrently.

## Environment

| Item | Value |
|------|-------|
| Host | macOS arm64 |
| JVM | GraalVM Java 25.0.3 |
| Primary command | `JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkIoThroughputBenchmark --console=plain` |
| Primary raw JSON | [`raw/benchmark-file-io-throughput-2026-05-29-macos-java25.json`](raw/benchmark-file-io-throughput-2026-05-29-macos-java25.json) |

## Fixture

| Fixture | Source | Batch size | Workload |
|---------|--------|------------|----------|
| `homer.jpg` | `images/src/test/resources/images/homer.jpg` | 64 files | Concurrent compressed file read/write |

The benchmark intentionally excludes Scrimage decode/encode. It isolates
compressed file IO boundary throughput with 64 concurrent tasks per invocation.

## Results

Throughput is higher-is-better. `@OperationsPerInvocation(64)` reports the
score as file operations per second.

| Benchmark | Boundary | Throughput |
|-----------|----------|------------|
| `read_path_concurrent` | `Files.readAllBytes` / `Path` | 83,394 files/s |
| `read_okioSource_concurrent` | Okio `Source` | 69,907 files/s |
| `read_suspendedFileSource_concurrent` | `AsynchronousFileChannel` `SuspendedSource` | 8,713 files/s |
| `write_path_concurrent` | `Files.write` / `Path` | 1,434 files/s |
| `write_okioSink_concurrent` | Okio `Sink` | 1,377 files/s |
| `write_suspendedFileSink_concurrent` | `AsynchronousFileChannel` `SuspendedSink` | 1,341 files/s |

![Concurrent image file IO throughput chart](../../docs/images/readme-charts/images-benchmark-file-io-throughput-chart-01.png)

## Interpretation

The throughput hypothesis did not hold on this local macOS Java 25 run.
Concurrent reads were fastest through `Path`, close behind through Okio
`Source`, and much slower through `AsynchronousFileChannel` `SuspendedSource`.
Concurrent writes were close across all three boundaries, with `Path` still
slightly ahead.

The practical guidance stays conservative: expose suspended file-channel
overloads for coroutine integration and lifecycle ergonomics, but do not claim
they improve image file IO throughput without workload-specific evidence.
