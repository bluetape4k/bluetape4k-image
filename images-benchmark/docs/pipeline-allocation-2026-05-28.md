# Image Pipeline Allocation Baseline (2026-05-28)

This report records the first allocation-sensitive baseline for
`ImagePipelineBenchmark`.

## Environment

| Item | Value |
|------|-------|
| Host | macOS arm64 |
| JVM | GraalVM Java 25.0.3 |
| Command | `java --enable-native-access=ALL-UNNAMED -jar images-benchmark/build/benchmarks/benchmark/jars/bluetape4k-images-benchmark-benchmark-jmh-0.1.3-JMH.jar '.*ImagePipelineBenchmark.*' -wi 1 -i 3 -w 1s -r 1s -f 1 -prof gc -rf json -rff images-benchmark/docs/raw/benchmark-pipeline-allocation-2026-05-28-macos-java25.json` |
| Raw JSON | [`raw/benchmark-pipeline-allocation-2026-05-28-macos-java25.json`](raw/benchmark-pipeline-allocation-2026-05-28-macos-java25.json) |

> The run used synthetic fallback images because `bench/photo-4k.jpg` and
> `bench/document.png` are not committed.

## Results

AverageTime is lower-is-better. `gc.alloc.rate.norm` is the allocation estimate
per operation from the JMH GC profiler.

| Benchmark | Pipeline | AverageTime | Allocation |
|-----------|----------|-------------|------------|
| `scrimage_photoPreviewJpeg` | 4K photo -> resize 1280x720 -> grayscale -> JPEG | 61.86 ms/op | 53,217,235 B/op (50.75 MB/op) |
| `scrimage_documentPreviewPng` | 1240x1754 document -> resize 640x905 -> blur -> sepia -> PNG | 48.04 ms/op | 63,850,035 B/op (60.89 MB/op) |

![Image pipeline allocation benchmark chart](../../docs/images/readme-charts/images-benchmark-pipeline-allocation-chart-01.png)

## Interpretation

Both pipelines allocate tens of megabytes per operation. That makes chained
high-level scrimage transforms a useful regression target before considering
pipeline fusion or API guidance.
