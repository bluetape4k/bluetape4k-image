한국어 | [English](./README.md)

# Module bluetape4k-images-benchmark

[scrimage](https://sksamuel.github.io/scrimage/)와 [libvips](https://www.libvips.org/) 이미지 처리 성능을 JVM JMH backend 위의 `kotlinx-benchmark`로 비교하는 벤치마크 모듈.

## 아키텍처

![images benchmark Architecture diagram](../docs/images/readme-diagrams/images-benchmark-architecture-01.png)

## 벤치마크 결과

> AverageTime ms/op. 최신 macOS Java 25 실행: [`docs/benchmark-results-2026-05-25.md`](docs/benchmark-results-2026-05-25.md). CI Linux 행은 기존 [`docs/benchmark-results-2026-04-29.md`](docs/benchmark-results-2026-04-29.md) 값을 유지합니다.

### 리사이즈 (4K 3840×2160 → 1920×1080)

![Resize latency benchmark chart](../docs/images/readme-charts/images-benchmark-resize-latency-chart-01.png)

| 환경 | scrimage (ms/op) | vips (ms/op) | 속도 향상 |
|------|-----------------|--------------|----------|
| macOS, java25 vips-ffm | 65.64 ± 0.76 | 0.170 ± 0.006 | **386배** |
| CI Linux, java25 | 187.29 ± 9.07 | 0.591 ± 0.046 | **317배** |
| CI Linux, java21 | 195.63 ± 7.39 | 0.495 ± 0.062 | **395배** |

### 인코딩 (4K photo 이미지)

![Encode latency benchmark chart](../docs/images/readme-charts/images-benchmark-encode-latency-chart-01.png)

| 포맷 | 환경 | scrimage (ms/op) | vips (ms/op) | 속도 향상 |
|------|------|-----------------|--------------|----------|
| JPEG | macOS, java25 vips-ffm | 46.55 ± 0.75 | 15.18 ± 0.55 | **3.1배** |
| JPEG | CI Linux, java25 | 171.16 ± 121.3 | 37.20 ± 0.99  | **4.6배** |
| JPEG | CI Linux, java21 | 161.09 ± 38.9  | 37.22 ± 1.50  | **4.3배** |
| PNG  | macOS, java25 vips-ffm | 84.91 ± 4.21 | 46.91 ± 0.52 | **1.8배** |
| PNG  | CI Linux, java25 | 249.01 ± 2.14  | 137.95 ± 2.93 | **1.8배** |
| PNG  | CI Linux, java21 | 246.44 ± 2.14  | 255.90 ± 10.2 | −1.04배 ⚠️ |

> ⚠️ **java21 (JNI) PNG**: JNI 경계 오버헤드가 압축 이득을 상쇄합니다. Linux PNG 인코딩은 java25 (FFM) 사용을 권장합니다.

### Vips 백엔드 비교

`VipsBackendBenchmark`와 `VipsBackendEncodeBenchmark`는 Java 21 JVips JNI
백엔드와 Java 25 FFM 백엔드를 같은 벤치마크 이름으로 반복 실행해 나란히
비교할 수 있게 합니다.

![Vips backend comparison benchmark chart](../docs/images/readme-charts/images-benchmark-vips-backend-comparison-chart-01.png)

| 벤치마크 | 작업 |
|----------|------|
| `vips_resize` | 4K JPEG를 `1920x1080`, `1280x720`로 리사이즈 |
| `vips_thumbnail` | 4K JPEG 썸네일 생성 |
| `vips_crop` | 4K JPEG 좌상단 영역 크롭 |
| `vips_encodeJpeg` | 4K JPEG 디코드 후 JPEG 인코딩 |

양쪽 백엔드 실행 명령, raw JSON 리포팅 형식, 로컬 검증 노트는
[`docs/vips-backend-comparison.md`](docs/vips-backend-comparison.md)를 참고하세요.

### 필터 (scrimage 전용, 1240×1754)

![Filter latency benchmark chart](../docs/images/readme-charts/images-benchmark-filter-latency-chart-01.png)

| 필터      | macOS (ms/op) | CI Linux java25 (ms/op) | CI Linux java21 (ms/op) |
|-----------|--------------|------------------------|------------------------|
| Sepia     | 14.51 ± 8.45 | 60.83 ± 0.42 | 60.70 ± 0.59 |
| Grayscale | 6.26 ± 0.12  | 99.72 ± 23.9 | 97.05 ± 12.6 |
| Blur      | 27.76 ± 0.15 | 73.64 ± 1.28 | 84.81 ± 6.31 |

### Pipeline Allocation (scrimage chained operations)

![Image pipeline allocation benchmark chart](../docs/images/readme-charts/images-benchmark-pipeline-allocation-chart-01.png)

| 벤치마크 | 파이프라인 | AverageTime | Allocation |
|----------|------------|-------------|------------|
| `scrimage_photoPreviewJpeg` | `landscape.jpg`를 `1280x720`으로 resize, grayscale, JPEG encode | 113.82 ms/op | 50.75 MB/op |
| `scrimage_documentPreviewPng` | `homer.png`를 `640x905`로 resize, blur, sepia, PNG encode | 57.86 ms/op | 60.89 MB/op |

자세한 조건은 [`docs/pipeline-allocation-2026-05-29.md`](docs/pipeline-allocation-2026-05-29.md),
raw `kotlinx-benchmark` JSON은
[`docs/raw/benchmark-pipeline-allocation-2026-05-29-macos-java25.json`](docs/raw/benchmark-pipeline-allocation-2026-05-29-macos-java25.json)을
참고하세요.

### IO 경계 Baseline (Path, Okio, Suspended File Channel)

![Image IO boundary benchmark chart](../docs/images/readme-charts/images-benchmark-io-boundary-chart-01.png)

| 작업 | 가장 빠른 baseline | Okio 경계 | Suspended file channel |
|------|-------------------|-----------|------------------------|
| `homer.jpg` 로드 | `ByteArray` 7.70 ms/op | `Source` 8.23 ms/op | `SuspendedSource` 10.81 ms/op |
| `landscape.jpg` 로드 | `Path` 152.22 ms/op | N/A | `SuspendedSource` 216.62 ms/op |
| `homer.jpg` JPEG 쓰기 | `ByteArray` 6.90 ms/op | `Sink` 7.40 ms/op | `SuspendedSink` 14.03 ms/op |

Suspended file-channel overload는 coroutine 파일 IO 경계로는 적합하지만,
Scrimage 로드/쓰기에서는 내부적으로 blocking stream 브리지를 사용하므로 latency
개선으로 보기는 어렵습니다. 자세한 조건은
[`docs/io-boundary-baseline-2026-05-29.md`](docs/io-boundary-baseline-2026-05-29.md),
raw `kotlinx-benchmark` JSON은
[`docs/raw/benchmark-io-boundary-2026-05-29-macos-java25.json`](docs/raw/benchmark-io-boundary-2026-05-29-macos-java25.json)을
참고하세요.

### 동시 파일 IO Throughput

![Concurrent image file IO throughput chart](../docs/images/readme-charts/images-benchmark-file-io-throughput-chart-01.png)

| 작업 | Path | Okio | Suspended file channel |
|------|------|------|------------------------|
| 64-file concurrent read | 83,394 files/s | 69,907 files/s | 8,713 files/s |
| 64-file concurrent write | 1,434 files/s | 1,377 files/s | 1,341 files/s |

이 benchmark는 Scrimage decode/encode를 제외하고 compressed file IO 경계만
분리해 측정합니다. 이번 Java 25 로컬 결과는 suspended file channel을 throughput
최적화로 보기 어렵다는 쪽입니다. 자세한 조건은
[`docs/file-io-throughput-2026-05-29.md`](docs/file-io-throughput-2026-05-29.md),
raw `kotlinx-benchmark` JSON은
[`docs/raw/benchmark-file-io-throughput-2026-05-29-macos-java25.json`](docs/raw/benchmark-file-io-throughput-2026-05-29-macos-java25.json)을
참고하세요.

### Memory Profile (kotlinx-benchmark + GC addendum)

![Image workload memory profile chart](../docs/images/readme-charts/images-benchmark-memory-profile-chart-01.png)

| 워크로드 | AverageTime | Allocation |
|----------|-------------|------------|
| `scrimage_encodeJpeg` | 146.09 ms/op | 96.34 MB/op |
| `scrimage_scaleTo` 1920x1080 | 115.34 ms/op | 24.04 MB/op |
| `vips_encodeJpeg` | 44.16 ms/op | 0.26 MB/op |
| `vips_resize` 1920x1080 | 0.246 ms/op | 4.14 KB/op |
| `vips_crop` 1920x1080 | 0.085 ms/op | 4.63 KB/op |
| `vips_thumbnail` 1920x1080 | 0.266 ms/op | 3.95 KB/op |

자세한 조건은 [`docs/memory-profile-2026-05-29.md`](docs/memory-profile-2026-05-29.md),
raw `kotlinx-benchmark` JSON은
[`docs/raw/benchmark-memory-profile-2026-05-29-macos-java25.json`](docs/raw/benchmark-memory-profile-2026-05-29-macos-java25.json)을
참고하세요.

---

## 벤치마크 실행

```bash
# Java 25 - scrimage + vips-ffm (Panama FFM, macOS/Linux)
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmark -Pvips.impl=java25

# Java 21 - scrimage + JVips JNI (Linux 전용)
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmark -Pvips.impl=java21

# 2026-05-29 리포트에 사용한 focused evidence
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkPipelineAllocationBenchmark --console=plain
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkIoBoundaryBenchmark --console=plain
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkIoThroughputBenchmark --console=plain
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkMemoryProfileBenchmark --console=plain
```

**macOS 사전 요구사항**: `brew install vips`

`VipsBenchmarkState`가 macOS를 자동 감지하고 Homebrew 라이브러리 경로를 등록합니다
(`vipsffm.libpath.*.override`). macOS SIP가 `DYLD_LIBRARY_PATH`를 제거하므로 필수.

### 리포트와 차트 재생성

Gradle `kotlinx-benchmark` task를 기본 실행 경로로 사용하세요. benchmark target 이름은
`benchmark`이므로 Gradle은 다음 task를 제공합니다.

| Task | 용도 |
|------|------|
| `benchmarkBenchmark` | 전체 benchmark target 실행 및 JMH report 생성 |
| `benchmarkBenchmarkJar` | focused/debug 실행용 JMH jar 생성 |
| `benchmarkBenchmarkGenerate` | JMH source 생성 |
| `benchmarkBenchmarkCompile` | 생성된 JMH source 컴파일 |

새 리포트 작성 절차:

1. vips 행 측정을 위한 native prerequisite을 설치합니다.
   - macOS: `brew install vips`
   - Linux: `libvips-tools`, `libvips-dev` 설치
2. 백엔드는 한 번에 하나씩 실행합니다. 같은 호스트에서 Java 21과 Java 25 benchmark
   프로세스를 병렬 실행하지 마세요.
3. 생성된 JMH JSON을
   `images-benchmark/build/reports/benchmarks/<target>/<timestamp>/benchmark.json`에서
   `images-benchmark/docs/raw/`로 복사하고,
   `benchmark-results-YYYY-MM-DD-macos-java25.json`처럼 환경이 드러나는 이름을 사용합니다.
4. `images-benchmark/docs/`의 해당 Markdown report에 실행 명령, host/JVM/libvips 조건,
   raw JSON 링크, 결과 표를 기록합니다. 모든 latency 표는 `AverageTime ms/op`이며
   낮을수록 좋습니다.
5. `docs/images/readme-charts/` 아래 benchmark chart SVG source를 갱신한 뒤 matching PNG를
   렌더링합니다. README에는 PNG만 embed하고, SVG source는 검토와 재생성을 위해 같은 위치에
   보관합니다.

현재 이 모듈에서 참조하는 chart asset:

| Chart | SVG source | README PNG |
|-------|------------|------------|
| Resize latency | `../docs/images/readme-charts/images-benchmark-resize-latency-chart-01.svg` | `../docs/images/readme-charts/images-benchmark-resize-latency-chart-01.png` |
| Encode latency | `../docs/images/readme-charts/images-benchmark-encode-latency-chart-01.svg` | `../docs/images/readme-charts/images-benchmark-encode-latency-chart-01.png` |
| Filter latency | `../docs/images/readme-charts/images-benchmark-filter-latency-chart-01.svg` | `../docs/images/readme-charts/images-benchmark-filter-latency-chart-01.png` |
| Vips backend comparison | `../docs/images/readme-charts/images-benchmark-vips-backend-comparison-chart-01.svg` | `../docs/images/readme-charts/images-benchmark-vips-backend-comparison-chart-01.png` |

차트 갱신 후 렌더링과 검증:

```bash
# 변경한 chart SVG를 PNG로 렌더링
rsvg-convert docs/images/readme-charts/images-benchmark-resize-latency-chart-01.svg \
  -o docs/images/readme-charts/images-benchmark-resize-latency-chart-01.png

# SVG 문법과 PNG 가독성 확인
xmllint --noout docs/images/readme-charts/*.svg
identify docs/images/readme-charts/*.png

# 전체 benchmark 실행 없이 문서화된 task 경로 검증
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmark \
  -Pvips.impl=java25 --dry-run --console=plain
```

특정 환경 행만 다시 측정했다면 갱신된 행을 명확히 표시하고, 이전 CI 행은 historical data로
유지하세요. Linux CI나 Java 21 JNI 행을 호환 호스트에서 다시 실행하지 않았다면 최신값처럼
표현하지 마세요.

---

## 벤치마크 클래스

### `ImageResizeBenchmark`

자연 사진 fixture `landscape.jpg`(4032×3024)를 여러 해상도로 리사이즈합니다.

| 파라미터     | 값 |
|-------------|-----|
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

자연 사진 fixture `landscape.jpg`를 JPEG/PNG로 인코딩합니다.

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

1240×1754 document 이미지에 scrimage 필터를 적용합니다.

| 벤치마크             | 필터              |
|----------------------|-----------------|
| `scrimage_blur`      | `BlurFilter`    |
| `scrimage_grayscale` | `GrayscaleFilter` |
| `scrimage_sepia`     | `SepiaFilter`   |

### `ImagePipelineBenchmark`

`kotlinx-benchmark`로 high-level scrimage operation chain을 측정합니다.
리포트의 allocation 행은 `kotlinx-benchmark` Gradle DSL이 profiler 인자를
노출하지 않기 때문에 별도 JVM GC-profiler addendum으로 기록합니다.

| 벤치마크 | 작업 |
|----------|------|
| `scrimage_photoPreviewJpeg` | 4K photo resize -> grayscale -> JPEG encode |
| `scrimage_documentPreviewPng` | document resize -> blur -> sepia -> PNG encode |

### `ImageIoBoundaryBenchmark`

기본 로드/쓰기 진입점과 Okio, `bluetape4k-okio` suspended file-channel 경계를
비교합니다.

| 벤치마크 그룹 | 경계 |
|----------------|------|
| `load_homer_*` | `ByteArray`, `InputStream`, `Path`, Okio `Source`, `SuspendedSource` |
| `load_landscape_*` | `Path`, `SuspendedSource` |
| `write_homer_*` | `ByteArray`, `OutputStream`, `Path`, Okio `Sink`, `SuspendedSink` |

### `ImageFileIoThroughputBenchmark`

벤치마크 invocation당 64개 concurrent task로 compressed image file IO throughput을
측정합니다. 파일 경계만 분리하기 위해 Scrimage decode/encode는 제외합니다.

| 벤치마크 그룹 | 경계 |
|----------------|------|
| `read_*_concurrent` | `Path`, Okio `Source`, `SuspendedSource` |
| `write_*_concurrent` | `Path`, Okio `Sink`, `SuspendedSink` |

### `VipsBenchmarkState`

JMH `@State(Scope.Thread)` — 리플렉션으로 vips 런타임을 Trial당 1회 초기화합니다
(`FfmVipsRuntime` Java 25 또는 `JVipsRuntime` Java 21 자동 탐색).

```kotlin
@Setup(Level.Trial)
fun setup() {
    photo4kJpegBytes = BenchmarkImageSets.photo4k.bytes(JpegWriter(80, false))
    applyMacOsVipsLibraryPaths()   // macOS에서 vipsffm.libpath.*.override 설정
    vipsAvailable = tryInitVipsRuntime()
}
```
