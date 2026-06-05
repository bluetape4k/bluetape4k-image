# Large Image Streaming Pipeline Benchmark (2026-06-05)

This report adds full load-transform-write pipeline evidence for milestone
`0.3.0` large-file and OCR-preprocessing work.

## Environment

| Item | Value |
|------|-------|
| Host | macOS arm64 |
| JVM | GraalVM Java 25.0.3 |
| Primary command | `JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkLargeStreamingBenchmark -Pvips.impl=java25 --console=plain` |
| Primary raw JSON | [`raw/benchmark-large-streaming-2026-06-05-macos-java25.json`](raw/benchmark-large-streaming-2026-06-05-macos-java25.json) |
| Allocation addendum | [`raw/benchmark-large-streaming-jmh-gc-2026-06-05-macos-java25.json`](raw/benchmark-large-streaming-jmh-gc-2026-06-05-macos-java25.json) |

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :bluetape4k-images-benchmark:benchmarkLargeStreamingBenchmark \
  -Pvips.impl=java25 --console=plain

./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkJar --console=plain

JAVA25=$(/usr/libexec/java_home -v 25)
"$JAVA25/bin/java" --enable-native-access=ALL-UNNAMED \
  -jar images-benchmark/build/benchmarks/benchmark/jars/bluetape4k-images-benchmark-benchmark-jmh-0.3.0-JMH.jar \
  '.*ImageLargeStreamingBenchmark.*' -wi 1 -i 3 -f 1 -bm avgt -tu ms \
  -prof gc -rf json \
  -rff images-benchmark/docs/raw/benchmark-large-streaming-jmh-gc-2026-06-05-macos-java25.json
```

> The benchmark source and primary execution path use `kotlinx-benchmark`.
> On JVM, `kotlinx-benchmark` uses JMH as its backend. The Gradle DSL does not
> expose JMH profilers, so managed heap allocation and GC counters are recorded
> in the separate JMH GC profiler addendum.

## Fixtures

The benchmark generates deterministic JPEG fixtures during JMH setup. Large
binary files are not committed to the repository.

| Scenario | Generated dimensions | Transform | Role |
|----------|----------------------|-----------|------|
| `large-photo` | 4032x3024 | resize to 1920x1440, grayscale, JPEG encode | Large natural-photo-like pipeline |
| `ocr-document` | 2480x3508 | resize to 1240x1754, grayscale, JPEG encode | Document/OCR-preprocessing-like pipeline |

## Results

AverageTime is lower-is-better. This is a local comparable snapshot, not a
production ranking.

![Large streaming pipeline benchmark chart](../../docs/images/readme-charts/images-benchmark-large-streaming-chart-01.png)

### Scrimage Rows

| Boundary | `large-photo` | `ocr-document` | Interpretation |
|----------|---------------|----------------|----------------|
| `ByteArray` | 224.04 ms/op | 143.64 ms/op | In-memory baseline; convenient but stages compressed input bytes. |
| `Path` | 223.19 ms/op | 145.13 ms/op | Comparable with other blocking Scrimage boundaries in this run. |
| `InputStream` / `OutputStream` | 221.64 ms/op | 148.39 ms/op | Fastest large-photo Scrimage row; useful for caller-owned stream boundaries. |
| Okio `Source` / `Sink` | 222.00 ms/op | 145.59 ms/op | Comparable with stream/path; not a latency win. |
| Suspended file source/sink | 254.95 ms/op | 170.69 ms/op | Slower because Scrimage still bridges to blocking streams. |

### libvips Java 25 FFM Rows

| Boundary | `large-photo` | `ocr-document` | Interpretation |
|----------|---------------|----------------|----------------|
| `ByteArray` | 23.65 ms/op | 15.38 ms/op | Much faster than Scrimage, but still stages compressed input bytes. |
| `Path` | 7.13 ms/op | 5.47 ms/op | Best row in this run; vips can decode directly from a file path. |
| `InputStream` / `OutputStream` | 23.99 ms/op | 15.59 ms/op | Similar to `ByteArray` because the vips stream path reads bounded bytes. |

### Managed Heap Allocation Addendum

Allocation is `gc.alloc.rate.norm` from the JMH GC profiler. Values are managed
heap allocation only; libvips native memory must still be checked with native
profiling tools when native lifetime is the concern.

| Boundary | `large-photo` allocation | `ocr-document` allocation | GC observation |
|----------|--------------------------|---------------------------|----------------|
| Scrimage `ByteArray` | 226,613,134 B/op (216.12 MiB/op) | 172,450,359 B/op (164.46 MiB/op) | 5-6 young GCs per run |
| Scrimage `Path` | 226,896,636 B/op (216.39 MiB/op) | 172,320,109 B/op (164.34 MiB/op) | Similar to `ByteArray` |
| Scrimage `InputStream` / `OutputStream` | 227,932,451 B/op (217.37 MiB/op) | 173,361,261 B/op (165.33 MiB/op) | Similar to `Path` and Okio |
| Scrimage Okio `Source` / `Sink` | 227,950,815 B/op (217.39 MiB/op) | 173,368,561 B/op (165.34 MiB/op) | No managed-allocation win |
| Scrimage suspended source/sink | 229,457,449 B/op (218.83 MiB/op) | 174,171,066 B/op (166.10 MiB/op) | Slightly more allocation plus bridge overhead |
| vips `ByteArray` | 576,111 B/op (0.55 MiB/op) | 361,436 B/op (0.34 MiB/op) | Near-zero GC |
| vips `Path` | 569,114 B/op (0.54 MiB/op) | 359,858 B/op (0.34 MiB/op) | Near-zero GC |
| vips `InputStream` / `OutputStream` | 2,601,939 B/op (2.48 MiB/op) | 1,467,317 B/op (1.40 MiB/op) | Low allocation, one GC count in this run |

## Recommendation

For #165, position Okio and suspended boundaries as lifecycle and integration
features, not as latency, throughput, or managed-allocation optimizations for
Scrimage. The large-file API should still avoid unnecessary compressed
`ByteArray` staging where a `Path`, `InputStream`, `Source`, or caller-owned
sink boundary already exists, but README/API wording should be explicit that
Scrimage decode/encode remains blocking internally and still dominates decoded
image heap allocation.

For #1, use the document/OCR-like row as preprocessing evidence: large document
resize/encode is feasible in the benchmark lane, but OCR implementation should
prefer an optional OCR module and keep native/model dependencies isolated.

For performance-sensitive large-image transforms, libvips remains the primary
recommendation. In this Java 25 FFM run, the `Path` pipeline was the strongest
large-file row on both latency and managed heap allocation, because the Java
wrapper work stays under 1 MiB/op while the native backend owns the transform.
