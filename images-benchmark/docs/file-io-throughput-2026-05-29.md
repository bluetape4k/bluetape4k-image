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

## Fixtures

| Fixture | Source | Size | Read batch | Write batch | Workload |
|---------|--------|------|------------|-------------|----------|
| `cafe.jpg` | `images/src/test/resources/images/cafe.jpg` | 2.9 MB | 6,400 hard-linked paths | 256 output files | Concurrent compressed file read/write |
| `landscape.jpg` | `images/src/test/resources/images/landscape.jpg` | 3.4 MB | 6,400 hard-linked paths | 256 output files | Concurrent compressed file read/write |

The benchmark intentionally excludes Scrimage decode/encode and streams bytes
through a fixed 128 KiB buffer. Read inputs use hard links to avoid creating
18-22 GB of setup copies per scenario; write benchmarks create real output
files.

## Results

Throughput is higher-is-better. The raw `ops/s` score is batch operations per
second; the table below reports derived file operations per second.

| Scenario | Boundary | Read throughput | Write throughput |
|----------|----------|-----------------|------------------|
| `cafe-6400` | `Path` | 16,904 files/s | 1,507 files/s |
| `cafe-6400` | Okio `Source`/`Sink` | 2,513 files/s | 767 files/s |
| `cafe-6400` | `AsynchronousFileChannel` `SuspendedSource`/`SuspendedSink` | 74 files/s | 147 files/s |
| `landscape-6400` | `Path` | 15,981 files/s | 1,280 files/s |
| `landscape-6400` | Okio `Source`/`Sink` | 2,072 files/s | 778 files/s |
| `landscape-6400` | `AsynchronousFileChannel` `SuspendedSource`/`SuspendedSink` | 70 files/s | 154 files/s |

![Concurrent image file IO throughput chart](../../docs/images/readme-charts/images-benchmark-file-io-throughput-chart-01.png)

## Interpretation

The earlier 64-file `homer.jpg` benchmark was biased toward tiny-file API
overhead. This run uses larger photo fixtures, 6,400 read paths, streaming
instead of `readByteArray`, and real large-file writes. The throughput
hypothesis still did not hold on this local macOS Java 25 run: `Path` was
fastest, Okio was lower, and `AsynchronousFileChannel` suspended boundaries were
substantially slower for both read and write.

The practical guidance stays conservative: expose suspended file-channel
overloads for coroutine integration and lifecycle ergonomics, but do not claim
they improve image file IO throughput without workload-specific evidence.
