# Issue 86 벤치마크 갱신

## 배경

#86은 이미 완료된 차트 생성이 아니라 현재 `images-benchmark` 재실행에 초점을
맞추도록 범위를 좁혀야 했다.

## 결정

이슈 제목과 본문을 벤치마크 재실행 중심으로 갱신한다. 이 작업에서는 Linux CI
벤치마크를 다시 실행하지 않았으므로 macOS Java 25 행만 갱신한다.

## 결과

`images-benchmark`가 GraalVM Java 25.0.3에서 `-Pvips.impl=java25`로
성공적으로 완료되었다. README 표와 차트 자산은 갱신한 macOS 값을 반영하면서
기존 CI Linux 행은 보존한다.

## 검증

- `./gradlew :bluetape4k-images-benchmark:benchmarkBenchmark -Pvips.impl=java25 --console=plain`
- 원시 JSON을 `benchmark/images-benchmark/docs/raw/benchmark-results-2026-05-25-macos-java25.json`에 복사했다.

## 향후 방지책

벤치마크 차트를 갱신할 때 실제로 다시 실행한 환경 행을 명시한다. CI Linux 행을
다시 실행하지 않았다면 최신 값이라고 표현하지 않는다.
