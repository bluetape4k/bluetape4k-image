[한국어](./README.ko.md) | English

# Module bluetape4k-images-benchmark

`kotlinx-benchmark` benchmarks comparing [scrimage](https://sksamuel.github.io/scrimage/) and [libvips](https://www.libvips.org/) image processing performance on the JVM JMH backend.

## Architecture

![images benchmark Architecture diagram](../docs/images/readme-diagrams/images-benchmark-architecture-01.png)

## Benchmark Results

> AverageTime ms/op. Current macOS Java 25 run: [`docs/benchmark-results-2026-05-25.md`](docs/benchmark-results-2026-05-25.md). Historical CI Linux rows remain from [`docs/benchmark-results-2026-04-29.md`](docs/benchmark-results-2026-04-29.md).

### Resize (4K 3840×2160 → 1920×1080)

![Resize latency benchmark chart](../docs/images/readme-charts/images-benchmark-resize-latency-chart-01.png)

| Environment | scrimage (ms/op) | vips (ms/op) | Speedup |
|-------------|-----------------|--------------|---------|
| macOS, java25 vips-ffm | 65.64 ± 0.76 | 0.170 ± 0.006 | **386×** |
| CI Linux, java25 | 187.29 ± 9.07 | 0.591 ± 0.046 | **317×** |
| CI Linux, java21 | 195.63 ± 7.39 | 0.495 ± 0.062 | **395×** |

### Encode (4K photo image)

![Encode latency benchmark chart](../docs/images/readme-charts/images-benchmark-encode-latency-chart-01.png)

| Format | Environment | scrimage (ms/op) | vips (ms/op) | Speedup |
|--------|-------------|-----------------|--------------|---------|
| JPEG | macOS, java25 vips-ffm | 46.55 ± 0.75 | 15.18 ± 0.55 | **3.1×** |
| JPEG | CI Linux, java25 | 171.16 ± 121.3 | 37.20 ± 0.99  | **4.6×** |
| JPEG | CI Linux, java21 | 161.09 ± 38.9  | 37.22 ± 1.50  | **4.3×** |
| PNG  | macOS, java25 vips-ffm | 84.91 ± 4.21 | 46.91 ± 0.52 | **1.8×** |
| PNG  | CI Linux, java25 | 249.01 ± 2.14  | 137.95 ± 2.93 | **1.8×** |
| PNG  | CI Linux, java21 | 246.44 ± 2.14  | 255.90 ± 10.2 | −1.04× ⚠️ |

> ⚠️ **java21 (JNI) PNG**: JNI boundary overhead exceeds compression gain vs scrimage. Use java25 (FFM) for PNG encoding on Linux.

### Vips Backend Comparison

`VipsBackendBenchmark` and `VipsBackendEncodeBenchmark` compare the Java 21
JVips JNI backend and the Java 25 FFM backend with stable benchmark names
across both runs.

![Vips backend comparison benchmark chart](../docs/images/readme-charts/images-benchmark-vips-backend-comparison-chart-01.png)

| Benchmark | Workload |
|-----------|----------|
| `vips_resize` | 4K JPEG resize to `1920x1080` and `1280x720` |
| `vips_thumbnail` | 4K JPEG thumbnail at matching max dimensions |
| `vips_crop` | 4K JPEG top-left crop to matching dimensions |
| `vips_encodeJpeg` | 4K JPEG decode and JPEG encode |

See [`docs/vips-backend-comparison.md`](docs/vips-backend-comparison.md) for
the side-by-side run commands, raw JSON reporting shape, and local validation
notes.

### Filter (scrimage only, 1240×1754 document image)

![Filter latency benchmark chart](../docs/images/readme-charts/images-benchmark-filter-latency-chart-01.png)

| Filter    | macOS (ms/op) | CI Linux java25 (ms/op) | CI Linux java21 (ms/op) |
|-----------|--------------|------------------------|------------------------|
| Sepia     | 14.51 ± 8.45 | 60.83 ± 0.42 | 60.70 ± 0.59 |
| Grayscale | 6.26 ± 0.12  | 99.72 ± 23.9 | 97.05 ± 12.6 |
| Blur      | 27.76 ± 0.15 | 73.64 ± 1.28 | 84.81 ± 6.31 |

### Pipeline Allocation (scrimage chained operations)

![Image pipeline allocation benchmark chart](../docs/images/readme-charts/images-benchmark-pipeline-allocation-chart-01.png)

| Benchmark | Pipeline | AverageTime | Allocation |
|-----------|----------|-------------|------------|
| `scrimage_photoPreviewJpeg` | resize `landscape.jpg` to `1280x720`, grayscale, JPEG encode | 113.82 ms/op | 50.75 MB/op |
| `scrimage_documentPreviewPng` | resize `homer.png` to `640x905`, blur, sepia, PNG encode | 57.86 ms/op | 60.89 MB/op |

See [`docs/pipeline-allocation-2026-05-29.md`](docs/pipeline-allocation-2026-05-29.md)
and the raw `kotlinx-benchmark` JSON
[`docs/raw/benchmark-pipeline-allocation-2026-05-29-macos-java25.json`](docs/raw/benchmark-pipeline-allocation-2026-05-29-macos-java25.json).

### IO Boundary Baseline (Path, Okio, Suspended File Channel)

![Image IO boundary benchmark chart](../docs/images/readme-charts/images-benchmark-io-boundary-chart-01.png)

| Workload | Fastest baseline | Okio boundary | Suspended file channel |
|----------|------------------|---------------|------------------------|
| `homer.jpg` load | `ByteArray` 7.70 ms/op | `Source` 8.23 ms/op | `SuspendedSource` 10.81 ms/op |
| `landscape.jpg` load | `Path` 152.22 ms/op | N/A | `SuspendedSource` 216.62 ms/op |
| `homer.jpg` JPEG write | `ByteArray` 6.90 ms/op | `Sink` 7.40 ms/op | `SuspendedSink` 14.03 ms/op |

The suspended file-channel overloads are useful coroutine IO boundaries, but
they are not a latency win for Scrimage load/write because Scrimage still
bridges through blocking streams. See
[`docs/io-boundary-baseline-2026-05-29.md`](docs/io-boundary-baseline-2026-05-29.md)
and the raw `kotlinx-benchmark` JSON
[`docs/raw/benchmark-io-boundary-2026-05-29-macos-java25.json`](docs/raw/benchmark-io-boundary-2026-05-29-macos-java25.json).

### Concurrent File IO Throughput

![Concurrent image file IO throughput chart](../docs/images/readme-charts/images-benchmark-file-io-throughput-chart-01.png)

| Workload | Path | Okio | Suspended file channel |
|----------|------|------|------------------------|
| 64-file concurrent read | 83,394 files/s | 69,907 files/s | 8,713 files/s |
| 64-file concurrent write | 1,434 files/s | 1,377 files/s | 1,341 files/s |

This compressed-file IO benchmark intentionally excludes Scrimage decode/encode.
The local Java 25 result does not support treating suspended file channels as a
throughput optimization. See
[`docs/file-io-throughput-2026-05-29.md`](docs/file-io-throughput-2026-05-29.md)
and the raw `kotlinx-benchmark` JSON
[`docs/raw/benchmark-file-io-throughput-2026-05-29-macos-java25.json`](docs/raw/benchmark-file-io-throughput-2026-05-29-macos-java25.json).

### Memory Profile (kotlinx-benchmark + GC addendum)

![Image workload memory profile chart](../docs/images/readme-charts/images-benchmark-memory-profile-chart-01.png)

| Workload | AverageTime | Allocation |
|----------|-------------|------------|
| `scrimage_encodeJpeg` | 146.09 ms/op | 96.34 MB/op |
| `scrimage_scaleTo` 1920x1080 | 115.34 ms/op | 24.04 MB/op |
| `vips_encodeJpeg` | 44.16 ms/op | 0.26 MB/op |
| `vips_resize` 1920x1080 | 0.246 ms/op | 4.14 KB/op |
| `vips_crop` 1920x1080 | 0.085 ms/op | 4.63 KB/op |
| `vips_thumbnail` 1920x1080 | 0.266 ms/op | 3.95 KB/op |

See [`docs/memory-profile-2026-05-29.md`](docs/memory-profile-2026-05-29.md)
and the raw `kotlinx-benchmark` JSON
[`docs/raw/benchmark-memory-profile-2026-05-29-macos-java25.json`](docs/raw/benchmark-memory-profile-2026-05-29-macos-java25.json).

---

## Running Benchmarks

```bash
# Java 25 - scrimage + vips-ffm (Panama FFM, macOS/Linux)
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmark -Pvips.impl=java25

# Java 21 - scrimage + JVips JNI (Linux only)
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmark -Pvips.impl=java21

# Focused evidence used by the 2026-05-29 reports
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkPipelineAllocationBenchmark --console=plain
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkIoBoundaryBenchmark --console=plain
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkIoThroughputBenchmark --console=plain
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkMemoryProfileBenchmark --console=plain
```

**macOS prerequisites**: `brew install vips`

`VipsBenchmarkState` auto-detects macOS and sets Homebrew library paths
(`vipsffm.libpath.*.override`) so libvips is found even with SIP stripping `DYLD_LIBRARY_PATH`.

### Regenerating Reports and Charts

Use the Gradle `kotlinx-benchmark` tasks as the primary execution surface. The
benchmark target is named `benchmark`, so Gradle exposes these tasks:

| Task | Purpose |
|------|---------|
| `benchmarkBenchmark` | Run the full benchmark target and write JMH reports |
| `benchmarkBenchmarkJar` | Build the JMH jar for focused/debug runs |
| `benchmarkBenchmarkGenerate` | Generate JMH sources |
| `benchmarkBenchmarkCompile` | Compile generated JMH sources |

Fresh report workflow:

1. Install native prerequisites for vips rows.
   - macOS: `brew install vips`
   - Linux: install `libvips-tools` and `libvips-dev`
2. Run one backend at a time. Do not run Java 21 and Java 25 benchmark
   processes in parallel on the same host.
3. Copy the generated JMH JSON from
   `images-benchmark/build/reports/benchmarks/<target>/<timestamp>/benchmark.json`
   to `images-benchmark/docs/raw/` with an environment-specific filename such
   as `benchmark-results-YYYY-MM-DD-macos-java25.json`.
4. Update the matching Markdown report under `images-benchmark/docs/` with the
   measured command, host/JVM/libvips conditions, raw JSON link, and result
   tables. Every latency table uses `AverageTime ms/op`; lower is better.
5. Update the benchmark chart SVG sources under `docs/images/readme-charts/`,
   then render the matching PNG files. README files embed PNGs only; keep SVG
   sources beside them for review and regeneration.

Chart assets currently referenced by this module:

| Chart | SVG source | README PNG |
|-------|------------|------------|
| Resize latency | `../docs/images/readme-charts/images-benchmark-resize-latency-chart-01.svg` | `../docs/images/readme-charts/images-benchmark-resize-latency-chart-01.png` |
| Encode latency | `../docs/images/readme-charts/images-benchmark-encode-latency-chart-01.svg` | `../docs/images/readme-charts/images-benchmark-encode-latency-chart-01.png` |
| Filter latency | `../docs/images/readme-charts/images-benchmark-filter-latency-chart-01.svg` | `../docs/images/readme-charts/images-benchmark-filter-latency-chart-01.png` |
| Vips backend comparison | `../docs/images/readme-charts/images-benchmark-vips-backend-comparison-chart-01.svg` | `../docs/images/readme-charts/images-benchmark-vips-backend-comparison-chart-01.png` |

Render and validate chart updates:

```bash
# Render touched chart SVG files to PNG
rsvg-convert docs/images/readme-charts/images-benchmark-resize-latency-chart-01.svg \
  -o docs/images/readme-charts/images-benchmark-resize-latency-chart-01.png

# Validate SVG syntax and PNG readability
xmllint --noout docs/images/readme-charts/*.svg
identify docs/images/readme-charts/*.png

# Validate the documented benchmark task path without running a full benchmark
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmark \
  -Pvips.impl=java25 --dry-run --console=plain
```

When only one environment row was rerun, label the refreshed row explicitly and
preserve older CI rows as historical data. Do not imply Linux CI or Java 21 JNI
numbers are current unless those rows were rerun on a compatible host.

---

## Benchmark Classes

### `ImageResizeBenchmark`

Resizes the natural `landscape.jpg` fixture (4032×3024) to multiple target resolutions.

| Parameter    | Values |
|--------------|--------|
| `resolution` | `1920x1080`, `1280x720` |

```kotlin
@Benchmark
fun scrimage_scaleTo(bh: Blackhole) {
    val resized = BenchmarkImageSets.photo4k.scaleTo(targetWidth, targetHeight)
    bh.consume(resized)
}

@Benchmark
fun vips_resize(state: VipsBenchmarkState, bh: Blackhole) {
    if (!state.vipsAvailable) { bh.consume(null); return }
    state.createVipsImage(state.photo4kJpegBytes).use { img ->
        bh.consume(img.resize(targetWidth, targetHeight))
    }
}
```

### `ImageEncodeBenchmark`

Encodes the natural `landscape.jpg` fixture to JPEG and PNG.

```kotlin
@Benchmark
fun scrimage_encodeJpeg(bh: Blackhole) {
    bh.consume(BenchmarkImageSets.document.bytes(JpegWriter(85, false)))
}

@Benchmark
fun vips_encodeJpeg(state: VipsBenchmarkState, bh: Blackhole) {
    if (!state.vipsAvailable) { bh.consume(null); return }
    state.createVipsImage(state.photo4kJpegBytes).use { img ->
        bh.consume(img.toJpegBytes(85))
    }
}
```

### `ImageFilterBenchmark`

Applies scrimage filters to a 1240×1754 document image.

| Benchmark          | Filter          |
|--------------------|-----------------|
| `scrimage_blur`    | `BlurFilter`    |
| `scrimage_grayscale` | `GrayscaleFilter` |
| `scrimage_sepia`   | `SepiaFilter`   |

### `ImagePipelineBenchmark`

Measures chained high-level scrimage operation paths through `kotlinx-benchmark`.
Allocation rows in the report use a separate JVM GC-profiler addendum because
the `kotlinx-benchmark` Gradle DSL does not expose profiler arguments.

| Benchmark | Workload |
|-----------|----------|
| `scrimage_photoPreviewJpeg` | 4K photo resize -> grayscale -> JPEG encode |
| `scrimage_documentPreviewPng` | document resize -> blur -> sepia -> PNG encode |

### `ImageIoBoundaryBenchmark`

Compares baseline load/write entry points with Okio and
`bluetape4k-okio` suspended file-channel boundaries.

| Benchmark group | Boundaries |
|-----------------|------------|
| `load_homer_*` | `ByteArray`, `InputStream`, `Path`, Okio `Source`, `SuspendedSource` |
| `load_landscape_*` | `Path`, `SuspendedSource` |
| `write_homer_*` | `ByteArray`, `OutputStream`, `Path`, Okio `Sink`, `SuspendedSink` |

### `ImageFileIoThroughputBenchmark`

Measures compressed image file IO throughput under 64 concurrent tasks per
benchmark invocation. It excludes Scrimage decode/encode to isolate the file
boundary.

| Benchmark group | Boundaries |
|-----------------|------------|
| `read_*_concurrent` | `Path`, Okio `Source`, `SuspendedSource` |
| `write_*_concurrent` | `Path`, Okio `Sink`, `SuspendedSink` |

### `VipsBenchmarkState`

JMH `@State(Scope.Thread)` — initializes the vips runtime once per trial via reflection
(supports both `FfmVipsRuntime` Java 25 and `JVipsRuntime` Java 21).

```kotlin
@Setup(Level.Trial)
fun setup() {
    photo4kJpegBytes = BenchmarkImageSets.photo4k.bytes(JpegWriter(80, false))
    applyMacOsVipsLibraryPaths()   // sets vipsffm.libpath.*.override on macOS
    vipsAvailable = tryInitVipsRuntime()
}
```
