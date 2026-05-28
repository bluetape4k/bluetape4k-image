# Image Workload Memory Profile (2026-05-28)

This report adds allocation-oriented evidence for representative resize, crop,
encode, and thumbnail workloads.

## Environment

| Item | Value |
|------|-------|
| Host | macOS arm64 |
| JVM | GraalVM Java 25.0.3 |
| Primary command | `JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkMemoryProfileBenchmark --console=plain` |
| Primary raw JSON | [`raw/benchmark-memory-profile-2026-05-28-macos-java25.json`](raw/benchmark-memory-profile-2026-05-28-macos-java25.json) |
| Allocation addendum | [`raw/benchmark-memory-profile-jmh-gc-2026-05-28-macos-java25.json`](raw/benchmark-memory-profile-jmh-gc-2026-05-28-macos-java25.json) |

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :bluetape4k-images-benchmark:benchmarkMemoryProfileBenchmark --console=plain
```

> The run used synthetic fallback images because `bench/photo-4k.jpg`,
> `bench/document.png`, and `bench/thumbnail.jpg` are not committed.
>
> The benchmark source and primary execution path use `kotlinx-benchmark`.
> On JVM, `kotlinx-benchmark` uses JMH as its backend. The Gradle DSL does not
> expose JMH profilers, so allocation values are recorded in the separate JMH GC
> profiler addendum.

## Results

AverageTime is lower-is-better. Allocation is `gc.alloc.rate.norm`.

| Benchmark | Resolution | AverageTime | Allocation |
|-----------|------------|-------------|------------|
| `scrimage_encodeJpeg` | N/A | 53.74 ms/op | 101,017,486 B/op (96.34 MB/op) |
| `scrimage_encodePng` | N/A | 95.85 ms/op | 268,376 B/op (0.26 MB/op) |
| `scrimage_scaleTo` | 1920x1080 | 71.56 ms/op | 25,206,805 B/op (24.04 MB/op) |
| `scrimage_scaleTo` | 1280x720 | 48.76 ms/op | 14,127,462 B/op (13.47 MB/op) |
| `vips_crop` | 1920x1080 | 0.060 ms/op | 4,784 B/op (4.67 KB/op) |
| `vips_crop` | 1280x720 | 0.060 ms/op | 4,784 B/op (4.67 KB/op) |
| `vips_resize` | 1920x1080 | 0.213 ms/op | 4,255 B/op (4.15 KB/op) |
| `vips_resize` | 1280x720 | 0.209 ms/op | 4,257 B/op (4.16 KB/op) |
| `vips_thumbnail` | 1920x1080 | 0.243 ms/op | 4,052 B/op (3.96 KB/op) |
| `vips_thumbnail` | 1280x720 | 0.254 ms/op | 4,046 B/op (3.95 KB/op) |
| `vips_encodeJpeg` | N/A | 16.03 ms/op | 271,037 B/op (0.26 MB/op) |

![Image workload memory profile chart](../../docs/images/readme-charts/images-benchmark-memory-profile-chart-01.png)

## Notes

- JMH GC profiler reports managed heap allocation, not total native memory
  retained by libvips during an operation.
- The vips transform rows still matter because they verify that wrapper objects
  and Java-side lifecycle code stay in the single-digit KB/op range.
- Native lifetime regressions should be investigated with OS/native memory tools
  in addition to this managed allocation profile.
