# Issue #166 대용량 스트리밍 벤치마크 설계

## 배경

- 이슈: [#166](https://github.com/bluetape4k/bluetape4k-image/issues/166)
- 마일스톤: `0.3.0`
- branch baseline: `develop`
- 작업 유형: Type B Fast Track benchmark evidence expansion.

## `develop` branch 증거

- `develop`은 clean이며 `origin/develop`을 추적한다.
- `develop` 대상 CodeGraph 조회가 현재 Kotlin symbol과 맞지 않았으므로, CodeGraph gap을 기록한 뒤
  repository file을 직접 사용해 source verification을 수행했다.
- `images/src/main/kotlin/io/bluetape4k/images/ImmutableImageSupport.kt`는 Scrimage-backed load/write에 대해
  이미 `ByteArray`, `InputStream`, `Path`, Okio `Source`, buffered `Source`, suspended source,
  `Path`, Okio `Sink`, buffered `Sink`, suspended sink 경계를 노출한다.
- 기존 benchmark evidence:
  - `benchmark/images-benchmark/docs/io-boundary-baseline-2026-05-29.md`
  - `benchmark/images-benchmark/docs/file-io-throughput-2026-05-29.md`
  - `benchmark/images-benchmark/docs/memory-profile-2026-05-29.md`

## 결정

public API를 바꾸기 전에 증거를 먼저 추가한다. Issue #166은 대용량 이미지의 전체
load-transform-write pipeline을 측정하고, Okio/suspended 경계가 지연 시간, 처리량, 또는
memory/lifecycle feature 중 무엇에 해당하는지 분류해야 한다. 이 PR에서는 새 production API를
추가하지 않는다.

## 범위

- 집중된 `kotlinx-benchmark` configuration과 task를 하나 추가한다.
  `benchmarkLargeStreamingBenchmark`.
- `benchmark/images-benchmark/src/benchmark/kotlin` 아래에 benchmark class를 하나 추가한다.
- 거대한 binary asset을 commit하지 않고 JMH setup에서 deterministic large fixture를 생성한다.
- 두 scenario를 측정한다.
  - `large-photo`: generated 4032x3024 photo-like JPEG.
  - `ocr-document`: generated 2480x3508 document/OCR-like JPEG.
- Scrimage boundary row를 측정한다.
  - `ByteArray`
  - `Path`
  - `InputStream` / `OutputStream`
  - Okio `Source` / `Sink`
  - suspended file source/sink
- local native runtime을 사용할 수 있을 때 libvips Java 25 FFM row를 측정한다.
  - `ByteArray`
  - `Path`
  - `InputStream` / `OutputStream`
- `kotlinx-benchmark`가 Gradle DSL을 통해 profiler flag를 노출하지 않으므로, JMH GC-profiler
  managed heap allocation과 GC counter를 부록으로 기록한다.
- benchmark README locale set, raw JSON, report, chart asset을 업데이트한다.

## 비목표

- 이 PR에서는 새 `images` public API를 추가하지 않는다.
- 대용량 binary fixture를 commit하지 않는다.
- 호환되는 Linux/Java 21 host를 명시적으로 실행하지 않는 한 Java 21 JNI row는 포함하지 않는다.
- local comparable snapshot을 넘어서는 단정적인 성능 주장은 하지 않는다.

## 인수 기준

- `:bluetape4k-images-benchmark:benchmarkBenchmarkCompile`이 통과한다.
- `:bluetape4k-images-benchmark:benchmarkLargeStreamingBenchmark -Pvips.impl=java25`
  이 local에서 통과하고 `benchmark/images-benchmark/docs/raw/` 아래에 raw JSON을 쓴다.
- JMH GC-profiler 부록이 `benchmark/images-benchmark/docs/raw/` 아래에 managed-allocation JSON을 쓴다.
- README table과 chart는 `AverageTime ms/op`를 표시하며 값이 낮을수록 좋다고 명시한다.
- report interpretation은 Okio/suspended 경계가 지연 시간, 처리량, 또는 memory/lifecycle feature 중
  무엇인지 명시적으로 설명한다.
- chart SVG/PNG pair가 검증되고 README는 PNG만 embed한다.
- PR 생성 전 P0/P1 review finding이 0개다.

## 후속 지침

- 이 benchmark를 #165 API positioning의 증거로 사용한다.
- OCR-like document row를 #1 preprocessing 증거로 사용한다.
- 향후 작업이 public API를 바꾼다면 이 증거에서 출발하고, 구현 전에 별도의 API design spec을 작성한다.
