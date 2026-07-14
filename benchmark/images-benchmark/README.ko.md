한국어 | [English](./README.md)

# Module bluetape4k-images-benchmark

[scrimage](https://sksamuel.github.io/scrimage/)와 [libvips](https://www.libvips.org/) 이미지 처리 성능을 JVM JMH backend 위의 `kotlinx-benchmark`로 비교하는 벤치마크 모듈.

## 아키텍처

![images benchmark Architecture diagram](../../docs/images/readme-diagrams/images-benchmark-architecture-01.png)

## 벤치마크 결과

> AverageTime ms/op이며 낮을수록 좋습니다. 현재 비교 가능한 macOS Java 25 FFM 근거는 커밋된 자연사진 fixture를 사용한 [`docs/benchmark-results-2026-05-28-natural-photos.md`](docs/benchmark-results-2026-05-28-natural-photos.md) 및 [raw JSON](docs/raw/benchmark-results-2026-05-28-macos-java25-natural-photos.json)입니다. 동일 fixture를 사용하지 않은 CI Linux 행은 의도적으로 제외합니다.

### 리사이즈 (자연 4K 사진 → 1920×1080)

![Resize latency benchmark chart](../../docs/images/readme-charts/images-benchmark-resize-latency-chart-01.png)

| 자연사진 | scrimage (ms/op) | vips Java 25 FFM (ms/op) | 속도 향상 |
|----------|-----------------|---------------------------|----------|
| `cafe` | 114.885 ± 3.207 | 0.257 ± 0.083 | **446배** |
| `landscape` | 115.641 ± 2.242 | 0.244 ± 0.028 | **473배** |

### 인코딩 (자연 4K 사진)

![Encode latency benchmark chart](../../docs/images/readme-charts/images-benchmark-encode-latency-chart-01.png)

| 포맷 | 자연사진 | scrimage (ms/op) | vips Java 25 FFM (ms/op) | 속도 향상 |
|------|----------|-----------------|---------------------------|----------|
| JPEG | `cafe` | 137.947 ± 2.417 | 58.351 ± 23.828 | **2.4배** |
| JPEG | `landscape` | 144.961 ± 5.511 | 46.749 ± 6.066 | **3.1배** |
| PNG | `cafe` | 884.105 ± 156.993 | 585.288 ± 186.247 | **1.5배** |
| PNG | `landscape` | 989.370 ± 346.605 | 546.388 ± 25.444 | **1.8배** |

> 이 값은 한 macOS Java 25 FFM host에서 측정한 자연사진 snapshot입니다. host 간 또는 Java 21 JNI 순위 비교가 아닙니다.

### Vips 백엔드 비교

`VipsBackendBenchmark`와 `VipsBackendEncodeBenchmark`는 Java 21 JVips JNI
백엔드와 Java 25 FFM 백엔드를 같은 벤치마크 이름으로 반복 실행해 나란히
비교할 수 있게 합니다.

![Vips backend comparison benchmark chart](../../docs/images/readme-charts/images-benchmark-vips-backend-comparison-chart-01.png)

| 벤치마크 | 작업 |
|----------|------|
| `vips_resize` | 4K JPEG를 `1920x1080`, `1280x720`로 리사이즈 |
| `vips_thumbnail` | 4K JPEG 썸네일 생성 |
| `vips_crop` | 4K JPEG 좌상단 영역 크롭 |
| `vips_encodeJpeg` | 4K JPEG 디코드 후 JPEG 인코딩 |

양쪽 백엔드 실행 명령, raw JSON 리포팅 형식, 로컬 검증 노트는
[`docs/vips-backend-comparison.md`](docs/vips-backend-comparison.md)를 참고하세요.

### Codec Runtime Matrix (PNG, WebP, AVIF, HEIC)

2026-07-13 codec 실행은 `cafe.jpg`를 `1920x1080` web-photo fixture로,
`homer.jpg`를 `512x512` profile fixture로 사용합니다. Java 25 FFM/libvips
8.18.4는 16개 방향 셀을 모두 측정했습니다. Java 21 JNI는 이 macOS arm64
host에서 JNI binary architecture를 확인할 수 없어 `N/A`이며 Java 25와
순위를 비교하지 않습니다.

![Codec runtime matrix latency chart](../../docs/images/readme-charts/images-benchmark-codec-runtime-latency-chart-01.png)

| Scenario | PNG encode / decode | WebP encode / decode | AVIF encode / decode | HEIC encode / decode |
|----------|---------------------|-----------------------|-----------------------|-----------------------|
| profile | 5.945 / 2.156 ms | 10.415 / 2.605 ms | 51.134 / 4.339 ms | 60.350 / 7.681 ms |
| web-photo | 80.132 / 18.825 ms | 106.405 / 20.020 ms | 511.268 / 38.751 ms | 339.555 / 73.038 ms |

![Codec encode output size chart](../../docs/images/readme-charts/images-benchmark-codec-output-size-chart-01.png)

상태 범례: `MEASURED`는 latency와 allocation 근거가 승인되었다는 뜻이고,
`N/A`는 이 host에서 runtime을 평가할 수 없다는 뜻입니다. `UNSUPPORTED`는
사용 가능한 runtime이 해당 codec/direction을 지원하지 않는 경우이며,
`SKIPPED`는 eligible 셀을 의도적으로 실행하지 않은 경우입니다. Encode는
JPEG에서 대상 codec으로, decode는 대상 codec에서 JPEG로 변환합니다.
Managed-heap allocation에는 native libvips memory가 포함되지 않으며 output
size는 시각 품질 순위가 아닙니다. 자세한 조건은
[`codec runtime matrix report`](docs/codec-runtime-matrix-2026-07-13.md),
원본 근거는 [immutable raw evidence](docs/raw/issue-208-20260713-macos-arm64-09/)를
참고하세요.

### ZXing 바코드 추출

이 Java 25 결과는 JMH trial setup에서 미리 로드하고 디코딩한 불변 이미지를
대상으로 ZXing 추출 성능을 측정합니다. Latency는 `AverageTime ms/op`이며 낮을수록 좋고,
throughput은 별도로 관측한 `ops/s` 값이라 높을수록 좋습니다. 한쪽 값을 역수로
계산해 다른 쪽을 만든 결과가 아닙니다.

| 시나리오 | Latency (ms/op) | Throughput (ops/s) | 예상 결과 |
|----------|-----------------|--------------------|-----------|
| QR | 0.174126 ± 0.001086 | 5702.142 ± 37.446 | QR 결과 1개 |
| Code 128 | 0.112914 ± 0.000715 | 8839.015 ± 135.003 | Code 128 결과 1개 |
| 결과 없음 | 0.271397 ± 0.009099 | 3690.012 ± 32.832 | 빈 목록 |

이 값은 Apple M5 호스트 한 대에서 고정 PNG fixture 3개와 단일 provider를 측정한
로컬 snapshot입니다. provider 간 또는 host 간 순위로 해석할 수 없습니다.
자세한 조건은 [`상세 리포트`](docs/barcode-extraction-2026-07-14.md), 원본 근거는
[`불변 raw evidence`](docs/raw/issue-272-20260714-macos-arm64-01/)를 참고하세요.

### 필터 (scrimage 전용, 1240×1754)

![Filter latency benchmark chart](../../docs/images/readme-charts/images-benchmark-filter-latency-chart-01.png)

| 필터      | macOS (ms/op) | CI Linux java25 (ms/op) | CI Linux java21 (ms/op) |
|-----------|--------------|------------------------|------------------------|
| Sepia     | 14.51 ± 8.45 | 60.83 ± 0.42 | 60.70 ± 0.59 |
| Grayscale | 6.26 ± 0.12  | 99.72 ± 23.9 | 97.05 ± 12.6 |
| Blur      | 27.76 ± 0.15 | 73.64 ± 1.28 | 84.81 ± 6.31 |

### Pipeline Allocation (scrimage chained operations)

![Image pipeline allocation benchmark chart](../../docs/images/readme-charts/images-benchmark-pipeline-allocation-chart-01.png)

| 벤치마크 | 파이프라인 | AverageTime | Allocation |
|----------|------------|-------------|------------|
| `scrimage_photoPreviewJpeg` | `landscape.jpg`를 `1280x720`으로 resize, grayscale, JPEG encode | 113.82 ms/op | 50.75 MB/op |
| `scrimage_documentPreviewPng` | `homer.png`를 `640x905`로 resize, blur, sepia, PNG encode | 57.86 ms/op | 60.89 MB/op |

자세한 조건은 [`docs/pipeline-allocation-2026-05-29.md`](docs/pipeline-allocation-2026-05-29.md),
raw `kotlinx-benchmark` JSON은
[`docs/raw/benchmark-pipeline-allocation-2026-05-29-macos-java25.json`](docs/raw/benchmark-pipeline-allocation-2026-05-29-macos-java25.json)을
참고하세요.

### IO 경계 Baseline (Path, Okio, Suspended File Channel)

![Image IO boundary benchmark chart](../../docs/images/readme-charts/images-benchmark-io-boundary-chart-01.png)

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

![Concurrent image file IO throughput chart](../../docs/images/readme-charts/images-benchmark-file-io-throughput-chart-01.png)

| 작업 | Path | Okio | Suspended file channel |
|------|------|------|------------------------|
| `cafe.jpg` 6,400-path concurrent read | 16,904 files/s | 2,513 files/s | 74 files/s |
| `landscape.jpg` 6,400-path concurrent read | 15,981 files/s | 2,072 files/s | 70 files/s |
| `cafe.jpg` 256-file concurrent write | 1,507 files/s | 767 files/s | 147 files/s |
| `landscape.jpg` 256-file concurrent write | 1,280 files/s | 778 files/s | 154 files/s |

이 benchmark는 Scrimage decode/encode를 제외하고 compressed file IO 경계만
분리해 측정합니다. `cafe.jpg`와 `landscape.jpg`를 사용하고, 고정 buffer로
streaming하며, read 입력은 큰 setup 복사를 피하려고 6,400개 hard link 경로를
사용합니다. 이번 Java 25 로컬 결과는 suspended file channel을 throughput
최적화로 보기 어렵다는 쪽입니다. 자세한 조건은
[`docs/file-io-throughput-2026-05-29.md`](docs/file-io-throughput-2026-05-29.md),
raw `kotlinx-benchmark` JSON은
[`docs/raw/benchmark-file-io-throughput-2026-05-29-macos-java25.json`](docs/raw/benchmark-file-io-throughput-2026-05-29-macos-java25.json)을
참고하세요.

### 대용량 Streaming Pipeline

![Large streaming pipeline benchmark chart](../../docs/images/readme-charts/images-benchmark-large-streaming-chart-01.png)

| 경계 | `large-photo` | `ocr-document` | 권장 판단 |
|------|---------------|----------------|-----------|
| Scrimage `Path` | 187.44 ms/op | 114.77 ms/op | 색상을 보존하는 blocking 경계 |
| Scrimage Okio `Source`/`Sink` | 183.37 ms/op | 115.41 ms/op | lifecycle/integration 경계이며 latency 보장은 아님 |
| Scrimage suspended source/sink | 215.61 ms/op | 136.77 ms/op | coroutine 경계지만 bridge overhead가 있습니다 |
| vips `Path` | 27.34 ms/op | 16.76 ms/op | local-file API 경계이며 50 MiB guard 안에서 여전히 버퍼링 |
| vips `InputStream`/`OutputStream` | 25.76 ms/op | 16.61 ms/op | caller-owned stream 경계이며 50 MiB guard 안에서 버퍼링 |

`ImageLargeStreamingBenchmark`는 큰 binary fixture를 commit하지 않도록 JMH setup
단계에서 deterministic large fixture를 생성합니다. 이번 로컬 Java 25 결과는
Okio/suspended API를 Scrimage latency나 throughput 최적화가 아니라 memory/lifecycle
경계로 설명하는 쪽을 지지합니다. vips 입력 경계는 이 짧은 snapshot을 보편적 순위로
해석하지 말고 caller가 이미 소유한 resource와 lifecycle에 맞춰 선택하세요. 현재 모든
vips 입력 overload는 `Path`를 포함해 50 MiB guard 안에서 compressed input을 검증하고
버퍼링하므로, 어느 경계도 streaming-memory 또는 guard 우회 선택지가 아닙니다.
자세한 조건은 [`docs/large-streaming-2026-07-10.md`](docs/large-streaming-2026-07-10.md),
raw `kotlinx-benchmark` JSON은
[`docs/raw/benchmark-large-streaming-2026-07-10-macos-java25.json`](docs/raw/benchmark-large-streaming-2026-07-10-macos-java25.json)을
참고하세요.
JMH GC-profiler addendum
[`docs/raw/benchmark-large-streaming-jmh-gc-2026-07-10-macos-java25.json`](docs/raw/benchmark-large-streaming-jmh-gc-2026-07-10-macos-java25.json)을
에는 동일한 16개 행의 managed-heap allocation이 기록됩니다. 이 Java
allocation 값만으로 native libvips 메모리를 추론하지 않습니다.

### Memory Profile (kotlinx-benchmark + GC addendum)

![Image workload memory profile chart](../../docs/images/readme-charts/images-benchmark-memory-profile-chart-01.png)

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
# Java 25 - FFM 전용 large streaming을 포함한 전체 benchmark set (macOS/Linux)
RUN_ID="local-$(date +%Y%m%d-%H%M%S)"
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmark \
  -Pcodec.matrix.runId="$RUN_ID" -Pvips.impl=java25

# Java 21 - 선택한 JVips JNI 호환 benchmark (Linux 전용, FFM 전용 large streaming 제외)
./gradlew :bluetape4k-images-benchmark:benchmarkPipelineAllocationBenchmark :bluetape4k-images-benchmark:benchmarkMemoryProfileBenchmark :bluetape4k-images-benchmark:benchmarkIoBoundaryBenchmark :bluetape4k-images-benchmark:benchmarkIoThroughputBenchmark -Pvips.impl=java21

# 2026-05-29 리포트에 사용한 focused evidence
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkPipelineAllocationBenchmark --console=plain
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkIoBoundaryBenchmark --console=plain
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkIoThroughputBenchmark --console=plain
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkMemoryProfileBenchmark --console=plain
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkLargeStreamingBenchmark -Pvips.impl=java25 --console=plain

# 대용량 streaming 행 managed heap allocation addendum
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkJar --console=plain

JAVA25=$(/usr/libexec/java_home -v 25)
"$JAVA25/bin/java" --enable-native-access=ALL-UNNAMED \
  -jar benchmark/images-benchmark/build/benchmarks/benchmark/jars/bluetape4k-images-benchmark-benchmark-jmh-0.3.0-JMH.jar \
  '.*ImageLargeStreamingBenchmark.*' -wi 1 -i 3 -f 1 -bm avgt -tu ms \
  -prof gc -rf json \
  -rff benchmark/images-benchmark/docs/raw/benchmark-large-streaming-jmh-gc-2026-07-10-macos-java25.json
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
   `benchmark/images-benchmark/build/reports/benchmarks/<target>/<timestamp>/benchmark.json`에서
   `benchmark/images-benchmark/docs/raw/`로 복사하고,
   `benchmark-results-YYYY-MM-DD-macos-java25.json`처럼 환경이 드러나는 이름을 사용합니다.
4. `benchmark/images-benchmark/docs/`의 해당 Markdown report에 실행 명령, host/JVM/libvips 조건,
   raw JSON 링크, 결과 표를 기록합니다. 모든 latency 표는 `AverageTime ms/op`이며
   낮을수록 좋습니다.
5. `docs/images/readme-charts/` 아래 benchmark chart SVG source를 갱신한 뒤 matching PNG를
   렌더링합니다. README에는 PNG만 embed하고, SVG source는 검토와 재생성을 위해 같은 위치에
   보관합니다.

현재 이 모듈에서 참조하는 chart asset:

| Chart | SVG source | README PNG |
|-------|------------|------------|
| Resize latency | `../../docs/images/readme-charts/images-benchmark-resize-latency-chart-01.svg` | `../../docs/images/readme-charts/images-benchmark-resize-latency-chart-01.png` |
| Encode latency | `../../docs/images/readme-charts/images-benchmark-encode-latency-chart-01.svg` | `../../docs/images/readme-charts/images-benchmark-encode-latency-chart-01.png` |
| Filter latency | `../../docs/images/readme-charts/images-benchmark-filter-latency-chart-01.svg` | `../../docs/images/readme-charts/images-benchmark-filter-latency-chart-01.png` |
| Vips backend comparison | `../../docs/images/readme-charts/images-benchmark-vips-backend-comparison-chart-01.svg` | `../../docs/images/readme-charts/images-benchmark-vips-backend-comparison-chart-01.png` |
| Large streaming pipeline | `../../docs/images/readme-charts/images-benchmark-large-streaming-chart-01.svg` | `../../docs/images/readme-charts/images-benchmark-large-streaming-chart-01.png` |

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
  -Pcodec.matrix.runId=local-dry-run-01 \
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

`cafe.jpg`와 `landscape.jpg`로 compressed image file IO throughput을 측정합니다.
Read는 scenario당 6,400개 hard-linked path를 사용하고, write는 scenario당 256개
실제 출력 파일을 씁니다. 파일 경계만 분리하기 위해 Scrimage decode/encode는
제외합니다.

| 벤치마크 그룹 | 경계 |
|----------------|------|
| `read_*_concurrent` | `Path`, Okio `Source`, `SuspendedSource` |
| `write_*_concurrent` | `Path`, Okio `Sink`, `SuspendedSink` |

### `ImageLargeStreamingBenchmark`

대용량 이미지 전체 load-transform-write pipeline을 측정합니다. 큰 binary file을
repository에 추가하지 않도록 fixture는 JMH setup에서 생성합니다.

| Scenario | 생성 크기 | Transform |
|----------|-----------|-----------|
| `large-photo` | 4032x3024 | 1920x1440으로 resize, JPEG encode |
| `ocr-document` | 2480x3508 | 1240x1754로 resize, JPEG encode |

| 벤치마크 그룹 | 경계 |
|----------------|------|
| `scrimage_*_pipeline` | `ByteArray`, `Path`, `InputStream`/`OutputStream`, Okio `Source`/`Sink`, suspended file source/sink |
| `vips_*_pipeline` | Java 25 FFM backend를 필수로 사용하는 `ByteArray`, `Path`, `InputStream`/`OutputStream` |

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
