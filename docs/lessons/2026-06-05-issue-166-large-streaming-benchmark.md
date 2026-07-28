# Issue #166 대용량 streaming benchmark

## 배경

Issue #166에서는 대용량 파일 Okio API와 OCR 전처리 작업의 방향을 정하기 전에
마일스톤 `0.3.0`의 benchmark 근거가 필요했다.

## 결정

먼저 `develop`을 분석하고 spec을 작성한 뒤 worktree 구현을 기준으로 삼는다.
CodeGraph가 현재 symbol을 해석하지 못했으므로 직접 소스를 검사한 결과와 spec을 지속
가능한 근거로 사용했다.

benchmark의 기본 실행 경로는 `kotlinx-benchmark`를 사용한다. Gradle DSL이 profiler
flag를 노출하지 않으므로 managed heap allocation은 별도의 JMH GC profiler 보충
실행으로 측정한다.

## 결과

`ImageLargeStreamingBenchmark`, 대상 `benchmarkLargeStreamingBenchmark` task,
원시 latency JSON, 원시 GC profiler JSON, README/README.ko 요약, 상세 보고서,
렌더링한 차트를 추가했다.

Scrimage `Path`, Okio, stream 경계의 managed heap 사용량은 비슷하다. 대형 사진은
약 216~218 MiB/op, OCR 형태 문서는 164~166 MiB/op였다. vips `Path`는 managed heap
사용량이 1 MiB/op 미만이며 Java 25 FFM 실행에서 가장 빠른 대용량 파일 항목이기도
했다.

## 검증

- `./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkCompile --console=plain`
- `./gradlew :bluetape4k-images-benchmark:benchmarkLargeStreamingBenchmark -Pvips.impl=java25 --console=plain`
- `-prof gc`를 사용한 JMH jar GC profiler 보충 실행
- `xmllint --noout docs/images/readme-charts/images-benchmark-large-streaming-chart-01.svg`
- `identify docs/images/readme-charts/images-benchmark-large-streaming-chart-01.png`
- 원시 JSON 파일 두 개에 `jq empty` 실행
- `git diff --check`

## 이후 지침

benchmark를 근거로 API를 작업할 때는 worktree 구현보다 `develop` branch 분석과 spec
승인을 먼저 수행한다. 이후 workload에서 latency, throughput, managed allocation의
측정 가능한 개선을 입증하기 전까지 Scrimage Okio/suspended 경계는 성능 최적화가 아닌
lifecycle/integration 경계로 취급한다.
