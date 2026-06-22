# Image Workload Memory Profile (2026-05-29)

This report adds allocation-oriented evidence for representative resize, crop,
encode, and thumbnail workloads.

## Environment

| Item | Value |
|------|-------|
| Host | macOS arm64 |
| JVM | GraalVM Java 25.0.3 |
| Primary command | `JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkMemoryProfileBenchmark --console=plain` |
| Primary raw JSON | [`raw/benchmark-memory-profile-2026-05-29-macos-java25.json`](raw/benchmark-memory-profile-2026-05-29-macos-java25.json) |
| Allocation addendum | [`raw/benchmark-memory-profile-jmh-gc-2026-05-29-macos-java25.json`](raw/benchmark-memory-profile-jmh-gc-2026-05-29-macos-java25.json) |

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :bluetape4k-images-benchmark:benchmarkMemoryProfileBenchmark --console=plain
```

> The benchmark source and primary execution path use `kotlinx-benchmark`.
> On JVM, `kotlinx-benchmark` uses JMH as its backend. The Gradle DSL does not
> expose JMH profilers, so allocation values are recorded in the separate JMH GC
> profiler addendum.

## Fixtures

| Fixture | Source | Dimensions | Role |
|---------|--------|------------|------|
| `landscape.jpg` | `images/src/test/resources/images/landscape.jpg` | 4032x3024 | Photo resize/encode/vips input |
| `homer.jpg` | `images/src/test/resources/images/homer.jpg` | 1248x702 | Thumbnail fixture |

## Results

AverageTime is lower-is-better. Allocation is `gc.alloc.rate.norm`.

| Benchmark | Resolution | AverageTime | Allocation |
|-----------|------------|-------------|------------|
| `scrimage_encodeJpeg` | N/A | 146.09 ms/op | 101,017,430 B/op (96.34 MB/op) |
| `scrimage_encodePng` | N/A | 832.79 ms/op | 268,386 B/op (0.26 MB/op) |
| `scrimage_scaleTo` | 1920x1080 | 115.34 ms/op | 25,206,811 B/op (24.04 MB/op) |
| `scrimage_scaleTo` | 1280x720 | 93.66 ms/op | 14,127,469 B/op (13.47 MB/op) |
| `vips_crop` | 1920x1080 | 0.085 ms/op | 4,744 B/op (4.63 KB/op) |
| `vips_crop` | 1280x720 | 0.085 ms/op | 4,744 B/op (4.63 KB/op) |
| `vips_resize` | 1920x1080 | 0.246 ms/op | 4,242 B/op (4.14 KB/op) |
| `vips_resize` | 1280x720 | 0.271 ms/op | 4,246 B/op (4.15 KB/op) |
| `vips_thumbnail` | 1920x1080 | 0.266 ms/op | 4,043 B/op (3.95 KB/op) |
| `vips_thumbnail` | 1280x720 | 0.274 ms/op | 4,052 B/op (3.96 KB/op) |
| `vips_encodeJpeg` | N/A | 44.16 ms/op | 271,075 B/op (0.26 MB/op) |

![Image workload memory profile chart](../../../docs/images/readme-charts/images-benchmark-memory-profile-chart-01.png)

## Notes

- JMH GC profiler reports managed heap allocation, not total native memory
  retained by libvips during an operation.
- The vips transform rows still matter because they verify that wrapper objects
  and Java-side lifecycle code stay in the single-digit KB/op range.
- Native lifetime regressions should be investigated with OS/native memory tools
  in addition to this managed allocation profile.
- The coroutine `Path` load/write helpers now stream directly through Scrimage
  instead of materializing whole compressed files as intermediate `ByteArray`
  values. `bluetape4k-okio` `BufferedSource`/`BufferedSink` overloads now cover
  caller-owned Okio streaming boundaries.
