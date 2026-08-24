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

> 이 값은 한 macOS Java 25 FFM host에서 측정한 자연사진 실행 결과입니다. host 간 또는 Java 21 JNI 순위 비교가 아닙니다.

### Vips 백엔드 비교

`VipsBackendBenchmark`와 `VipsBackendEncodeBenchmark`는 레거시 이름의 JVips JNI
백엔드와 FFM 백엔드를 JDK 25에서 같은 벤치마크 이름으로 반복 실행해 나란히
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
로컬 실행 결과입니다. provider 간 또는 host 간 순위로 해석할 수 없습니다.
자세한 조건은 [`상세 리포트`](docs/barcode-extraction-2026-07-14.md), 원본 근거는
[`불변 raw evidence`](docs/raw/issue-272-20260714-macos-arm64-01/)를 참고하세요.

### Tesseract OCR 추출

이 Java 25/macOS 측정 기준 데이터는 clean, noisy, rotated, multilingual hash-pinned PNG
문서에서 Tess4J 기반 공개 API `ImmutableImage.extractText`를 측정합니다. 매 호출의
native engine 설정을 포함하며 fixture 로드, 디코드, 예상 token 검증은 trial setup에서
수행합니다. latency는 `AverageTime ms/op`으로 낮을수록 좋고 throughput은 별도로
관측한 `ops/s`로 높을수록 좋습니다.

| 시나리오 | 직접 추출 latency | 전처리 + 추출 | 직접 추출 throughput | 전처리 + 추출 |
|----------|-------------------|---------------|----------------------|---------------|
| clean text | 217.921 ms/op | 194.128 ms/op | 4.607 ops/s | 5.111 ops/s |
| noisy scan | 367.810 ms/op | 282.790 ms/op | 2.727 ops/s | 3.418 ops/s |
| rotated document | 168.593 ms/op | 186.895 ms/op | 5.875 ops/s | 5.189 ops/s |
| multilingual | 370.003 ms/op | 394.922 ms/op | 2.704 ops/s | 2.518 ops/s |

![Tesseract OCR extraction benchmark chart](../../docs/images/readme-charts/images-benchmark-ocr-extraction-chart-01.png)

#### 현재 OCR corpus v2 벤치마크

벤치마크 task는 이제 검증된 `bench/ocr-v2/manifest.json`을 `fixtureId`로
읽으며 v1 fixture fallback을 사용하지 않습니다. 매니페스트에는 8개 양성
시나리오 클래스별 3개씩 총 24개의 benchmarkable positive fixture와, 별도로
분리한 malformed-input negative receipt 3개가 있습니다. 위 표와 차트는 과거
v1 기준 데이터로 유지합니다.

| Fixture | 직접 추출 latency | 전처리 + 추출 | 직접 추출 throughput | 전처리 + 추출 |
|---------|-------------------|---------------|----------------------|---------------|
| `clean-text-v2-001` | 223.134 ± 6.445 ms/op | 207.461 ± 28.548 ms/op | 4.512 ± 0.272 ops/s | 4.985 ± 0.572 ops/s |

이 결과는 macOS arm64 Java 25 host 한 대에서 얻은 Tesseract baseline-only
receipt이며, host 간 순위나 도입 결정을 의미하지 않습니다. v2 immutable report와
실행 manifest는 [`Issue #565 corpus receipt`](docs/raw/issue-565-20260824-macos-arm64-java25-v2-corpus/)
에 있습니다. corpus는 후속 metric 및 run-receipt train을 위한 규모로 확장되었고,
CER/WER와 cold/warm/RSS 근거는 별도 benchmark output으로 관리합니다.

Train-3 host-native 실행은 다음 명령으로 재현합니다. 각 fixture의 cold/warm
latency, 관측 throughput, process RSS, 출력 hash와 host/JVM/Tesseract envelope를
기록하며 throughput을 latency 역수로 계산하지 않습니다. warm은 engine wrapper
재사용이며 public `TesseractOcrEngine`의 fresh Tess4J client 계약을 바꾸지 않습니다.

```bash
./gradlew :bluetape4k-images-benchmark:runOcrCorpusProtocol \
  -Pocr.protocol.runId=issue-565-protocol-20260824 \
  -Pocr.protocol.output=/absolute/path/issue565-protocol.json \
  --console=plain
./gradlew :bluetape4k-images-benchmark:validateOcrProtocolReceipt --console=plain
```

커밋한 full-corpus receipt는 24개 row(`TEXT` 21개, `EMPTY` 3개)와 embedded
CER/WER summary를 포함합니다. macOS arm64 Java 25 host 한 대의 관찰값이므로
host 간 순위나 production SLO로 해석하지 않습니다. 상세 파일은
[`v2 protocol receipt`](docs/raw/issue-565-20260824-macos-arm64-java25-v2-protocol/)에
있습니다.

synthetic 추가 fixture는 고정한 ImageMagick/font receipt를 사용해
`ruby benchmark/images-benchmark/tools/generate_ocr_v2_fixtures.rb`로 재생성할 수
있습니다. 기존 `clean-text-v2-001` baseline은 byte 단위로 보존하므로 legacy
fixture를 동일하게 재생할 수 있을 때까지 generator receipt는 `PENDING`입니다.

trial setup은 `extractText`와 `preprocessAndExtract`를 각각 한 번 실행해
fixture가 선언한 `TEXT`/`EMPTY` 결과를 모두 검증합니다. 선언과 다른 결과가 나오면
즉시 실패합니다. 실행에 사용한 `eng.traineddata`의 경로, 실제 해석 경로, byte 수,
SHA-256은 [`model-provenance.json`](docs/raw/issue-563-20260824-macos-arm64-java25-v2-baseline/model-provenance.json)에
기록합니다. 기준 데이터를 사용하기 전에
`./gradlew :bluetape4k-images-benchmark:validateOcrBenchmarkReceipt`를 실행해
manifest, raw report EOF 정규화, report hash, model provenance hash를 함께
검증합니다.

GC profiler는 direct clean text 추출에서 managed allocation `1,417,421 B/op`을
기록했으며 Tesseract native/model memory는 포함하지 않습니다. `tesseract`, tessdata,
fixture language prerequisite을 명시적으로 확인하므로 OCR task는 기본 CI lane에서
실행하지 않습니다. 자세한 조건은
[`상세 리포트`](docs/ocr-extraction-benchmark.md), 원본 근거는
[`불변 raw evidence`](docs/raw/issue-203-20260726-macos-java25/)를 참고하세요.

### 0.4.0 Benchmark 추가

`0.4.0` benchmark lane은 다음 configuration으로 독립 실행할 수 있습니다.

| 이슈 | Configuration | 범위 |
|------:|---------------|-------|
| #203 | `ocrLatency`, `ocrThroughput` | Tesseract 추출, 전처리, multilingual traineddata, GC addendum |
| #204 | `storageLocal`, `storageS3` | `ImageStorage` upload/download/list와 크기 제한 |
| #205 | `ktorRoute`, `ktorRouteConcurrency` | 단일/동시 multipart thumbnail route, mixed traffic, 크기 초과 거부 |
| #206 | `batchPipeline` | thumbnail fan-out, 순차 처리와 제한된 coroutine 병렬 처리 |
| #207 | `algorithmicHotPaths` | crop, tiling, dominant colors, SVG rasterization, similarity |

로컬 OCR, storage, Ktor route, batch, algorithmic lane 실행:

```bash
./gradlew :bluetape4k-images-benchmark:benchmarkOcrLatencyBenchmark
./gradlew :bluetape4k-images-benchmark:benchmarkOcrThroughputBenchmark
./gradlew :bluetape4k-images-benchmark:benchmarkStorageLocalBenchmark
./gradlew :bluetape4k-images-benchmark:benchmarkKtorRouteBenchmark
./gradlew :bluetape4k-images-benchmark:benchmarkKtorRouteConcurrencyBenchmark
./gradlew :bluetape4k-images-benchmark:benchmarkBatchPipelineBenchmark
./gradlew :bluetape4k-images-benchmark:benchmarkAlgorithmicHotPathsBenchmark
```

S3 lane은 credential 없이 동작하는 in-memory adapter benchmark이며
`-Pstorage.s3.enabled=true`를 명시해야 합니다. 실제 네트워크 성능을 의미하지
않습니다. fixture, object 수, cleanup, 해석 범위는
[`storage backend`](docs/storage-backend-benchmark.md),
[`OCR extraction`](docs/ocr-extraction-benchmark.md),
[`Ktor thumbnail route`](docs/ktor-thumbnail-route-benchmark.md),
[`batch and thumbnail`](docs/batch-thumbnail-benchmark.md),
[`algorithmic hot paths`](docs/algorithmic-hot-paths-2026-07.md)를 참조하세요.

![Storage backend benchmark chart](../../docs/images/readme-charts/images-benchmark-storage-backend-chart-01.png)

storage chart는 sub-millisecond adapter 비용부터 filesystem 비용까지 범위가
넓어 log scale을 사용합니다. in-memory S3 adapter가 byte/list 작업에서 더 빠른
것은 네트워크와 durable filesystem 비용을 제거했기 때문이며, 실제 운영 S3
throughput 순위로 해석하면 안 됩니다. 크기 제한 초과 행은 payload 저장 전에
거절되므로 두 backend 모두 거의 0에 가깝습니다.

![Ktor multipart thumbnail route benchmark chart](../../docs/images/readme-charts/images-benchmark-ktor-thumbnail-route-chart-01.png)

이 host에서 Ktor test host를 통과한 전체 route는 직접 decode, resize, PNG
encode보다 약 `2.3-3.9 ms/op` 더 걸렸습니다. 입력 크기가 커질 때 전체 route
latency가 `16.9-102.6 ms/op`로 증가하는 주된 원인은 image 처리입니다.
Multipart parsing만 측정하면 `0.4 ms/op` 미만이고, 제한보다 1 byte 큰 upload는
decode 전에 약 `0.35 ms/op`로 거부됩니다. socket, TLS, proxy, network IO는
포함하지 않은 in-process route 비용입니다.

![Ktor accepted-route concurrency chart](../../docs/images/readme-charts/images-benchmark-ktor-concurrency-chart-01.png)

closed-loop 동시 요청 측정에서 두 accepted fixture 모두 concurrency 10이
정점이었습니다. `medium`은 약 `157.4 derived req/s`, `photo4k`는
`58.8 derived req/s`였고, concurrency 30에서는 각각 `128.7`, `52.2 derived
req/s`로 하락하면서 p95 batch 완료 시간은 `290.8`, `687.9 ms`로 증가했습니다.
따라서 30은 포화 상태를 확인하는 stress point이지 기본 capacity 목표가 아닙니다.
expected rejection과 90/10 mixed batch도 10에서 30으로 갈 때 같은 하락을
보였습니다. 이 값은 in-process closed-loop 파생치이며 운영 open-loop
throughput이 아닙니다.

![Batch and thumbnail scaling benchmark chart](../../docs/images/readme-charts/images-benchmark-batch-pipeline-chart-01.png)

batch chart의 핵심은 scaling 차이입니다. Scrimage 순차 처리는 입력 1개에서
8개로 늘 때 약 `78`에서 `616 ms/op`로 증가하지만, bounded concurrency는
8개에서도 약 `92 ms/op`입니다. libvips thumbnail-only 행은 약 `33`에서
`261-269 ms/op`로 거의 선형 증가합니다. Scrimage의 resize+JPEG 행과 다른
pipeline boundary이므로 backend 직접 순위로 비교하지 않습니다.

![Algorithmic hot paths benchmark chart](../../docs/images/readme-charts/images-benchmark-algorithmic-hot-paths-chart-01.png)

algorithmic chart는 document와 photo fixture를 함께 보이기 위해 log scale을
사용합니다. photo의 `dominantColors`와 `histogramSimilarity`가 각각 약
`140`, `158 ms/op`로 가장 크고, document fixture에서는 두 작업 모두
`10 ms/op` 아래입니다. 이는 photo 분석 작업의 우선순위를 판단하기 위한
fixture 기반 근거이며 host 간 보편적 보장은 아닙니다.

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
경계로 설명하는 쪽을 지지합니다. vips 입력 경계는 이 짧은 실행 결과를 보편적 순위로
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

# JDK 25 - 선택한 레거시 JVips JNI backend benchmark (Linux 전용, FFM 전용 large streaming 제외)
./gradlew :bluetape4k-images-benchmark:benchmarkPipelineAllocationBenchmark :bluetape4k-images-benchmark:benchmarkMemoryProfileBenchmark :bluetape4k-images-benchmark:benchmarkIoBoundaryBenchmark :bluetape4k-images-benchmark:benchmarkIoThroughputBenchmark -Pvips.impl=java21

# 2026-05-29 리포트에 사용한 focused evidence
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkPipelineAllocationBenchmark --console=plain
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkIoBoundaryBenchmark --console=plain
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkIoThroughputBenchmark --console=plain
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkMemoryProfileBenchmark --console=plain
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew :bluetape4k-images-benchmark:benchmarkLargeStreamingBenchmark -Pvips.impl=java25 --console=plain

# 대용량 streaming 행 managed heap allocation addendum
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkJar --console=plain

# 0.4.0 focused lanes
./gradlew :bluetape4k-images-benchmark:benchmarkStorageLocalBenchmark --console=plain
./gradlew :bluetape4k-images-benchmark:benchmarkBatchPipelineBenchmark --console=plain
./gradlew :bluetape4k-images-benchmark:benchmarkAlgorithmicHotPathsBenchmark --console=plain
# S3 adapter-only lane (credential 불필요, 명시적 opt-in)
./gradlew :bluetape4k-images-benchmark:benchmarkStorageS3Benchmark \
  -Pstorage.s3.enabled=true --console=plain

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
2. 백엔드는 한 번에 하나씩 실행합니다. 같은 호스트에서 JVips JNI와 FFM backend benchmark
   프로세스를 병렬 실행하지 마세요.
3. 생성된 JMH JSON을
   `benchmark/images-benchmark/build/reports/benchmarks/<target>/<timestamp>/benchmark.json`에서
   `benchmark/images-benchmark/docs/raw/`로 복사하고,
   `benchmark-results-YYYY-MM-DD-macos-java25.json`처럼 환경이 드러나는 이름을 사용합니다.
4. `benchmark/images-benchmark/docs/`의 해당 Markdown report에 실행 명령, host/JVM/libvips 조건,
   raw JSON 링크, 결과 표를 기록합니다. 모든 latency 표에는 JMH mode와 단위를
   명시합니다.
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
| Storage backend | `../../docs/images/readme-charts/images-benchmark-storage-backend-chart-01.svg` | `../../docs/images/readme-charts/images-benchmark-storage-backend-chart-01.png` |
| Ktor multipart thumbnail route | `../../docs/images/readme-charts/images-benchmark-ktor-thumbnail-route-chart-01.svg` | `../../docs/images/readme-charts/images-benchmark-ktor-thumbnail-route-chart-01.png` |
| Ktor accepted-route concurrency | `../../docs/images/readme-charts/images-benchmark-ktor-concurrency-chart-01.svg` | `../../docs/images/readme-charts/images-benchmark-ktor-concurrency-chart-01.png` |
| Tesseract OCR extraction | `../../docs/images/readme-charts/images-benchmark-ocr-extraction-chart-01.svg` | `../../docs/images/readme-charts/images-benchmark-ocr-extraction-chart-01.png` |
| Batch and thumbnail scaling | `../../docs/images/readme-charts/images-benchmark-batch-pipeline-chart-01.svg` | `../../docs/images/readme-charts/images-benchmark-batch-pipeline-chart-01.png` |
| Algorithmic hot paths | `../../docs/images/readme-charts/images-benchmark-algorithmic-hot-paths-chart-01.svg` | `../../docs/images/readme-charts/images-benchmark-algorithmic-hot-paths-chart-01.png` |

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

### `VipsTransformBenchmark` (Issue #582)

scrimage, Java 21 JVips backend, Java 25 FFM backend에서 파생 이미지의
ownership 비용을 chain/fan-out 변환으로 측정합니다. 모든 `VipsImage` 파생
결과는 같은 scope에서 소비하고 즉시 닫으며, operation 사이에 native handle을
보존하거나 ownership 계약을 바꾸지 않습니다.

이미지 크기(`1280x720`, `640x480`), chain 길이(`3`), fan-out(`4`)와 JMH
cold/warm protocol을 고정합니다. backend별 실행 명령은 다음과 같습니다.

```bash
./gradlew :bluetape4k-images-benchmark:benchmarkVipsTransformBenchmark -Pvips.impl=java25
./gradlew :bluetape4k-images-benchmark:benchmarkVipsTransformBenchmark -Pvips.impl=java21
```

커밋된 receipt는 host-native RSS/allocation을 측정하기 전까지 `N/A` 행을
포함하는 계약 receipt입니다. 모듈 check 전에 다음 validator를 실행해야 합니다.

```bash
./gradlew :bluetape4k-images-benchmark:validateVipsTransformReceipt
```

`N/A`는 성능 결과가 아닙니다. backend 비교 전 후속 macOS/Linux native run에서
native resource, output hash, latency/throughput 근거를 기록해야 합니다. 원본
receipt는 [`transform-receipt.json`](docs/raw/issue-582-20260825-macos-arm64-java25-transform/transform-receipt.json)입니다.

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
(`FfmVipsRuntime` 또는 레거시 이름의 `JVipsRuntime`을 JDK 25에서 자동 탐색).

```kotlin
@Setup(Level.Trial)
fun setup() {
    photo4kJpegBytes = BenchmarkImageSets.photo4k.bytes(JpegWriter(80, false))
    applyMacOsVipsLibraryPaths()   // macOS에서 vipsffm.libpath.*.override 설정
    vipsAvailable = tryInitVipsRuntime()
}
```
