# Issue #563 OCR task를 corpus v2에 연결한 교훈

## 맥락

OCR benchmark task가 v1의 `scenario`와 `OcrBenchmarkFixtures`를 계속 사용해
검증된 corpus v2 manifest를 실제로 소비하지 않는 문제가 있었다. 이 상태에서는
v2 receipt와 benchmark 결과가 같은 fixture 집합을 사용한다고 증명할 수 없다.

## 결정

`TesseractOcrExtractionBenchmark`의 파라미터를 `fixtureId`로 바꾸고,
`OcrBenchmarkCorpusV2.loadFixture`가 image·text·geometry·license receipt를
검증한 뒤 Tesseract에 전달하도록 고정했다. trial setup에서는
`expectedOutcome`을 다시 확인해 `TEXT`/`EMPTY` 계약을 어기면 즉시 실패시키며,
`ERROR` fixture는 benchmark 입력으로 허용하지 않는다. Gradle report validator도
v1 scenario 목록을 복제하지 않고 v2 manifest의 benchmarkable fixture ID를 읽는다.

## 결과

현재 manifest의 유일한 positive fixture `clean-text-v2-001`에 대해 latency와
throughput task를 각각 실행하고 immutable JSON과 run manifest를 남겼다. latency는
직접 추출 `225.860 ± 55.267 ms/op`, 전처리 후 추출 `199.305 ± 3.082 ms/op`이며,
throughput은 각각 `4.521 ± 0.149 ops/s`, `4.889 ± 0.474 ops/s`였다. 이 수치는
macOS arm64 Java 25 한 호스트의 baseline-only receipt다.

## 검증

- RED: v2 output contract test가 `verifyOutput` 부재로 compile 실패함을 확인했다.
- GREEN: `OcrBenchmarkCorpusV2Test`와 `OcrBenchmarkContractTest` 10/10 PASS.
- `./gradlew :bluetape4k-images-benchmark:cleanTest --no-build-cache :bluetape4k-images-benchmark:test --console=plain`: 모듈 전체 PASS.
- `./gradlew :bluetape4k-images-benchmark:benchmarkOcrLatencyBenchmark --console=plain`: 2개 row와 v2 validator PASS.
- `./gradlew :bluetape4k-images-benchmark:benchmarkOcrThroughputBenchmark --console=plain`: 2개 row와 v2 validator PASS.
- `./gradlew detekt --console=plain`: `detekt` NO-SOURCE, BUILD SUCCESSFUL.
- `git diff --check`: PASS.

## 다음 guard

새 positive fixture를 추가할 때 manifest의 `fixtureId`, JMH `@Param`, report row
validator와 raw receipt를 함께 갱신한다. malformed/limit 입력은 negative manifest에
두고 benchmark 입력으로 섞지 않는다. 현재 9개 시나리오·최소 27개 fixture 확장과
Paddle 비교는 별도 이슈에서 수행하며, 이 baseline만으로 provider 도입 결정을 내리지 않는다.
