# Image Processing JMH Benchmark Results - 2026-05-28 Natural Photos

## Summary

This run refreshes the `images-benchmark` comparison with real natural photo
fixtures instead of the previous synthetic fallback image.

Lower `ms/op` is better. Scores are JMH `AverageTime` results with 3 warmup
iterations, 5 measurement iterations, and 1 fork.

## Environment

| Item | Value |
|------|-------|
| Date | 2026-05-28 |
| Host | macOS Darwin arm64 |
| JVM | Oracle GraalVM Java 25.0.3 |
| vips implementation | `-Pvips.impl=java25` / vips-ffm |
| Command | `./gradlew :bluetape4k-images-benchmark:benchmarkBenchmark -Pvips.impl=java25 --console=plain` |
| Raw JSON | [`raw/benchmark-results-2026-05-28-macos-java25-natural-photos.json`](raw/benchmark-results-2026-05-28-macos-java25-natural-photos.json) |

## Inputs

The resize and encode comparison now runs against two committed natural photo
fixtures:

| Image | Source path | Dimensions | Original size |
|-------|-------------|------------|---------------|
| `cafe` | `benchmark/images-benchmark/src/main/resources/bench/cafe.jpg` | 4032x3024 | 2.9 MiB |
| `landscape` | `benchmark/images-benchmark/src/main/resources/bench/landscape.jpg` | 4032x3024 | 3.4 MiB |

`BenchmarkImageSets` still keeps synthetic fallback generation for optional
document and thumbnail resources, but the headline resize and encode rows below
use real JPEG photo inputs.

## Natural Photo Results

### Resize To 1920x1080

| Image | scrimage `scaleTo` (ms/op) | libvips Java 25 FFM `resize` (ms/op) | Speedup |
|-------|----------------------------|--------------------------------------|---------|
| `cafe` | 114.885 ± 3.207 | 0.257 ± 0.083 | 446x |
| `landscape` | 115.641 ± 2.242 | 0.244 ± 0.028 | 473x |

### Encode

| Image | Format | scrimage (ms/op) | libvips Java 25 FFM (ms/op) | Speedup |
|-------|--------|------------------|------------------------------|---------|
| `cafe` | JPEG | 137.947 ± 2.417 | 58.351 ± 23.828 | 2.4x |
| `landscape` | JPEG | 144.961 ± 5.511 | 46.749 ± 6.066 | 3.1x |
| `cafe` | PNG | 884.105 ± 156.993 | 585.288 ± 186.247 | 1.5x |
| `landscape` | PNG | 989.370 ± 346.605 | 546.388 ± 25.444 | 1.8x |

## Caveats

- These rows are natural photo results, not document, flat graphic, or animated
  image results. Encode speed can shift materially with image content.
- Java 21 JNI is still `N/A` on this macOS arm64 host because the available
  JVips dylib is x86_64.
- The benchmark output includes backend-only libvips rows and scrimage filter
  rows. This report highlights the rows used by the blog and README comparison.
