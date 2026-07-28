# Issue #272 ZXing barcode benchmark 교훈

## 배경

Issue #272는 immutable QR, Code 128, no-result PNG fixture에서 ZXing barcode extraction의
reproducible latency/throughput evidence를 추가한다. 측정 boundary는 이미 decode된
`ImmutableImage`에서 시작하며 fixture generation, resource loading, PNG decoding은 제외한다.

## 결정 또는 확인 사항

- exact fixture byte와 strict hash/dimension/expectation manifest를 commit한다.
  runtime-generated barcode는 provider test에는 유용하지만 generator 또는 encoder 변경은
  longitudinal benchmark row를 비교 불가능하게 만든다.
- average latency와 throughput은 독립적으로 측정한다. 둘은 서로 다른 JMH mode를 사용하므로
  harness behavior, rounding, statistical error가 달라 reciprocal conversion으로 제시하지
  않는다.
- provider-neutral fixture validation은 main에, ZXing dependency는 benchmark/test configuration에
  둔다. boundary는 dependency declaration text만이 아니라 resolved runtime classpath로 확인한다.
- raw evidence는 owned run으로 다룬다. fresh task start를 기록하고 mode마다 정확히 하나의 새
  report만 accept하며, 모든 row와 metric을 검증한 뒤 append-only directory 하나를 atomically
  promote한다. rerun은 accepted evidence를 덮어쓰지 않고 새 run ID를 받는다.
- 전체 build file에서 common Gradle literal을 세는 contract test는 unrelated benchmark
  configuration에 결합된다. assertion은 보호하려는 protocol의 named configuration으로 좁힌다.
- failure test는 단순 exception type이 아니라 의도한 error를 assert해야 한다. 첫 path test는
  나중 hash mismatch가 같은 exception을 던져 `./`에 대해 통과했다. normalization message 확인이
  missing guard를 드러냈다.
- Gradle command-line `-D` property는 자동으로 test-worker system property가 되지 않는다. 임시
  fixture generator는 명시적으로 전파하기 전까지 property를 보지 못했다. 향후 generator는
  launcher inheritance에 의존하지 말고 dedicated JavaExec task, `systemProperty`로 연결한 Gradle
  property, 또는 explicit test-task mapping을 사용한다.

## 결과

accepted run은 같은 fixture에 대한 average-time row 3개와 throughput row 3개를 담는다. 각
fixture는 immutable하고 hash-pinned이다. 각 raw report는 benchmark name, scenario set, mode,
unit, thread, fork, warmup, measurement, positive finite score, finite non-negative
score error로 검증된다. main runtime classpath에는 ZXing provider가 없고, benchmark runtime은
ZXing `3.5.4`를 사용한다.

## 검증

- Benchmark module: clean test 84개 통과와 benchmark source compilation.
- Barcode API/provider regression: fresh passing test 14개와 8개.
- 증거: accepted file 4개, mode별 row 3개, canonical fixture manifest parity,
  recomputed SHA-256 value, duplicate-promotion rejection.
- Documentation: rounded score/error pair 6개가 report와 두 README locale에서 모두 일치하며
  raw link와 metric direction이 존재한다.
- Static/scope check: module build, task listing, projects, detekt, dependency boundary,
  unsafe-Kotlin scan, `git diff --check` 통과.
- Six-lens implementation review는 `P0=0`, `P1=0`으로 수렴했다.

## 향후 지침

metric unit 또는 direction이 호환되지 않는 one-provider result에는 table을 사용한다. 이후
issue가 provider 정확히 2개를 비교한다면 complementary pastel pair와 matching legend swatch를
사용하고, series가 3개 이상이면 categorical palette를 사용한다. 향후 run이 performance claim
전에 comparability를 세울 수 있도록 fixture identity, provider version, JVM, host, protocol,
raw JSON, accepted-run hash를 함께 보관한다.
