# Large Image Streaming Pipeline Benchmark (2026-07-10)

This report is the refreshed, color-preserving large-streaming snapshot for
Issue #197. It uses the Java 25 FFM libvips backend and the same deterministic
large-photo and OCR-document workloads for every row.

## Environment

| Item | Value |
|------|-------|
| Host | macOS arm64 |
| JVM | Java 25 (local GraalVM distribution) |
| Backend | libvips through the Java 25 FFM binding; no JNI fallback |
| Command | `JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkLargeStreamingBenchmark -Pvips.impl=java25 --console=plain` |
| Primary raw JSON | [`raw/benchmark-large-streaming-2026-07-10-macos-java25.json`](raw/benchmark-large-streaming-2026-07-10-macos-java25.json) |
| Primary SHA-256 | `b82f80dd530c586b3827e1af7750e479ec2dec8d5e6795effe7f0f34f501962f` |
| GC addendum | [`raw/benchmark-large-streaming-jmh-gc-2026-07-10-macos-java25.json`](raw/benchmark-large-streaming-jmh-gc-2026-07-10-macos-java25.json) |
| GC SHA-256 | `fccccba2c5fb4cd8e4604fb44a208d5ef560fb6dd1b9bf74bea0904b6c9c7df6` |

The raw files are sanitized JMH JSON snapshots. Effective settings are one
fork, one warmup iteration, three one-second measurement iterations, and
`AverageTime` in `ms/op`; lower is better. The GC addendum reports only
managed-heap `gc.alloc.rate.norm` in `B/op`, not native-memory lifetime.

## Fixtures and transform

| Scenario | Dimensions | Transform |
|----------|------------|-----------|
| `large-photo` | 4032x3024 | decode -> resize to 1920x1440 -> JPEG encode |
| `ocr-document` | 2480x3508 | decode -> resize to 1240x1754 -> JPEG encode |

No grayscale or other color-changing filter is part of this comparison.

## AverageTime results

| Boundary | `large-photo` | `ocr-document` |
|----------|---------------|----------------|
| Scrimage `ByteArray` | 186.14 ms/op | 117.41 ms/op |
| Scrimage `Path` | 187.44 ms/op | 114.77 ms/op |
| Scrimage `InputStream` / `OutputStream` | 183.65 ms/op | 114.88 ms/op |
| Scrimage Okio `Source` / `Sink` | 183.37 ms/op | 115.41 ms/op |
| Scrimage suspended source/sink | 215.61 ms/op | 136.77 ms/op |
| vips `ByteArray` | 25.13 ms/op | 16.30 ms/op |
| vips `Path` | 27.34 ms/op | 16.76 ms/op |
| vips `InputStream` / `OutputStream` | 25.76 ms/op | 16.61 ms/op |

![Large streaming pipeline benchmark chart](../../../docs/images/readme-charts/images-benchmark-large-streaming-chart-01.png)

## Managed-heap allocation addendum

| Boundary | `large-photo` | `ocr-document` |
|----------|---------------|----------------|
| Scrimage `ByteArray` | 121,127,659 B/op | 89,281,836 B/op |
| Scrimage `Path` | 119,104,337 B/op | 87,489,727 B/op |
| Scrimage `InputStream` / `OutputStream` | 120,145,564 B/op | 88,532,271 B/op |
| Scrimage Okio `Source` / `Sink` | 120,158,675 B/op | 88,539,439 B/op |
| Scrimage suspended source/sink | 121,678,506 B/op | 89,306,881 B/op |
| vips `ByteArray` | 577,814 B/op | 363,620 B/op |
| vips `Path` | 2,602,946 B/op | 1,469,544 B/op |
| vips `InputStream` / `OutputStream` | 2,603,465 B/op | 1,469,863 B/op |

These are managed-heap observations only; they do not claim that native
libvips memory is absent or bounded by the Java allocation number.

## Interpretation

This is a local comparable snapshot, not a production ranking. The Java 25
FFM vips rows are materially faster than the Scrimage rows in this workload,
while Scrimage remains the color-preserving blocking implementation. Choose
the `Path` boundary for local files when vips is available; choose stream/Okio
boundaries for lifecycle or caller-owned I/O integration, not as a promise of
lower latency.

The previous asymmetric report is retained as historical evidence only:
[`large-streaming-2026-06-05.md`](large-streaming-2026-06-05.md). Its raw data
must not support current recommendations.
