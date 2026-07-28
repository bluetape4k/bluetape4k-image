# Issue #197 대용량 스트리밍 벤치마크 동등성 설계

## 목표

대용량 이미지 benchmark를 Scrimage와 libvips가 구현하는 일반적인 color-preserving pipeline의 공정한
비교로 만든다.

`decode -> resize -> JPEG encode`

benchmark는 한 backend에서만 grayscale transform을 사용하면 안 된다. grayscale conversion은 비교 대상인
일반 thumbnail/resize workload에 속하지 않으므로 의도적으로 scope 밖에 둔다.

## 현재 문제

`ImageLargeStreamingBenchmark`는 Scrimage path에서 resize 후 `GRAYSCALE_FILTER`를 적용하지만,
libvips path는 resize와 encode를 바로 수행한다. benchmark report와 README는 양쪽 모두 grayscale을
포함한다고 설명한다. 따라서 published comparison은 서로 다른 workload를 섞고 있으며, code가 수정된 뒤에는
기존 measurement, table, chart를 authoritative evidence로 유지할 수 없다.

## 선택한 설계

Scrimage-only grayscale filter를 제거한다. 기존 deterministic fixture, dimension, boundary, JPEG option,
libvips behavior는 변경하지 않는다. 비교되는 모든 row는 color-preserving `decode -> resize -> JPEG encode`
contract를 수행한다.

이는 benchmark 목적을 바꾸거나 어느 backend에도 color-conversion feature를 추가하지 않고 workload parity를
복원하는 가장 작은 변경이다.

## 고려한 대안

1. libvips에 grayscale을 추가한다.
   - product requirement 없이 일반 color-preserving workload를 grayscale workload로 바꾸므로 거부한다.
2. asymmetric implementation을 유지하고 result label만 바꾼다.
   - 보고된 backend comparison이 여전히 equivalent work를 측정하지 않으므로 거부한다.
3. dedicated grayscale benchmark를 만든다.
   - 보류한다. 실제 grayscale/OCR preprocessing 요구가 자체 performance evidence를 필요로 할 때만 가치가 있다.

## 범위

- `benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/ImageLargeStreamingBenchmark.kt`의
  shared Scrimage transform을 업데이트한다.
- `benchmark/images-benchmark/docs/` 아래에 새 date-stamped detailed report를 만들고
  `large-streaming-2026-06-05.md`에 visible supersession notice를 추가한다.
- `benchmark/images-benchmark/README.md`와 `benchmark/images-benchmark/README.ko.md`의
  large-streaming section을 함께 업데이트한다.
- root `README.md`와 `README.ko.md`의 large-image benchmark link와 recommendation을 함께 업데이트한다.
- `docs/scripts/generate-readme-visual-assets.py`의 `images-benchmark-large-streaming-chart-01` input을
  업데이트하고 `docs/images/readme-charts/` 아래 SVG/PNG artifact를 재생성한다.
- fresh supported `kotlinx.benchmark` run에서 benchmark result evidence와 date-stamped raw JSON copy를
  `benchmark/images-benchmark/docs/raw/` 아래에 재생성한다.
- 같은 parity workload에 대한 date-stamped GC-profiler addendum을 재생성하거나, old asymmetric addendum에서 파생된
  모든 managed-allocation recommendation과 link를 제거한다.
- recommendation language는 refreshed local comparable snapshot으로 제한하고 measurement caveat를 유지한다.

## 비목표

- libvips image-processing behavior를 변경하지 않는다.
- grayscale API 또는 OCR preprocessing benchmark를 추가하지 않는다.
- 새 root-cause evidence가 요구하지 않는 한 fixture generation, target dimension, output format, encode option을
  변경하지 않는다.
- refreshed local result를 production-wide ranking으로 취급하지 않는다.

## 측정과 증거 정책

생성된 benchmark task는 `:bluetape4k-images-benchmark:benchmarkLargeStreamingBenchmark`다.
measurement run은 Java 25와 `-Pvips.impl=java25`를 사용해야 하며, 현재 benchmark configuration인
one warmup과 three one-second average-time iteration을 따른다. 정확한 command, JVM,
host architecture, libvips binding, measurement date를 report에 기록해야 한다.

생성된 Gradle task가 authoritative measurement surface다. direct JMH diagnostic run이 다른 workload를
조용히 선택하지 못하도록 source annotation도 같은 one-warmup contract에 맞춰야 한다. refreshed raw JSON은
one fork, one one-second warmup, three one-second measurement iteration, average-time mode,
output unit milliseconds를 증명해야 한다.

이전 2026-06-05 raw JSON은 audit artifact로 immutable하게 남긴다. JSON file 자체가 아니라 old detailed
report가 raw result가 asymmetric workload 때문에 superseded되었음을 visible하게 밝혀야 한다. old raw JSON과
old report는 current README table, recommendation, chart의 근거가 되면 안 된다. refreshed raw JSON은
date-stamped filename을 사용하고 report value, README value,
`images-benchmark-large-streaming-chart-01` input의 유일한 source여야 한다.

refreshed detailed report는 새 date-stamped filename을 사용한다. old report는 visible supersession link를
유지해야 하고, 두 README locale은 refreshed report에만 link해야 한다. old report는 invalid table 옆에 shared
current chart를 embed하면 안 된다. 이는 active benchmark page가 아니라 superseded evidence의 archived explanation이다.

refreshed GC-profiler addendum은 같은 color-preserving workload와 date-stamped raw filename을 사용해야 한다.
repository의 Gradle `kotlinx.benchmark` task가 JMH profiler configuration을 노출하지 않으므로, direct JMH jar
invocation은 이 addendum에만 허용된다. main benchmark result는 계속 Gradle task다.

## Cross-Backend 준비 contract

이 issue는 cross-backend comparison을 publish하므로 libvips unavailable 상태를 optional skipped row로
취급할 수 없다. benchmark result를 수용하기 전에 Java 25 FFM vips implementation이 성공적으로 초기화되어야
하며, 모든 expected vips row가 unavailable sentinel value를 소비하는 대신 image pipeline을 실행해야 한다.
이 precondition이 충족되지 않으면 benchmark는 actionable error로 fail fast해야 한다. 성공한 Gradle exit code나
vips-named raw row만으로는 충분한 증거가 아니다.

## Regression guard

이 benchmark-only change에는 synthetic timing unit test를 도입하지 않는다. module에는 현재 benchmark test source가
없으며, timing test는 workload parity를 증명하지 못한다. 대신 validation은 다음을 포함해야 한다.

1. large-streaming Scrimage transform이 resize와 JPEG encode만 포함하고 `GRAYSCALE_FILTER` reference가
   없음을 보여주는 source-level guard.
2. cross-backend run이 unavailable libvips row를 publish하지 않고 실패함을 보여주는 source-level readiness guard.
3. configured measurement setting을 증명하고 `large-photo`, `ocr-document` 두 scenario 모두에 대해 실행된
   Scrimage와 vips row를 포함하는 raw output을 가진 successful focused `kotlinx.benchmark` execution.

이 guard는 의도적으로 좁다. grayscale은 다른 benchmark class에서 유효한 operation으로 남아야 하며 다른 곳에서
제거하면 안 된다.

## 파생 artifact contract

report, 각 benchmark README locale의 두 large-streaming 위치, root README locale pair는 같은
color-preserving workload description을 보여야 하고, 표시되는 모든 값을 refreshed raw data로 trace해야 한다.
chart generator는 backend/boundary category와 두 scenario series `large-photo`, `ocr-document`를 사용해야 하며,
이 series를 JPEG와 PNG로 label하면 안 된다. scale label, tick spacing, displayed value는 하나의 consistent
scale을 표현해야 한다. 생성물은 다음과 같다.

- `docs/images/readme-charts/images-benchmark-large-streaming-chart-01.svg`
- `docs/images/readme-charts/images-benchmark-large-streaming-chart-01.png`

regenerated SVG는 XML-valid여야 하며 displayed series/value는 refreshed README table과 일치해야 한다.
chart source reference는 superseded asymmetric result가 아니라 refreshed report를 가리켜야 한다.

generator는 현재 complete visual set을 다시 쓴다. execution은 large-streaming SVG와 PNG만 포함하는
allowlist를 기록하고, generated diff를 즉시 inspect하며, 계속하기 전에 관련 없는 모든 generated asset을
restore해야 한다. 그런 다음 target SVG를 CairoSVG로 target PNG에 render하고, 해당 PNG를 full size로 inspect해
legend, scale, label, clipping correctness를 확인해야 한다. rendering 후 PNG signature와 dimension을 검증한다.

## 검증 contract

1. filter 또는 measurement를 실행하기 전에 Gradle로 generated benchmark task name을 inspect한다.
2. measurement 전에 Java 25 FFM/libvips readiness를 검증하고 unavailable이면 cross-backend run을 실패시킨다.
3. 기록된 Java 25/libvips command를 사용해 `ImageLargeStreamingBenchmark`용 focused Gradle
   `kotlinx.benchmark` task를 실행한다.
4. raw output이 effective fork, warmup, iteration, duration, mode, unit, 모든 executed
   backend/scenario row를 증명하는지 확인하고 documented date-stamped audit path로 copy한다.
5. 같은 workload로 equivalent GC-profiler addendum을 실행하거나 active report와 README file에서 모든 allocation
   claim을 제거한다.
6. source-level `GRAYSCALE_FILTER`와 libvips-readiness guard를 large-streaming benchmark file에만 실행한다.
7. raw evidence에서 report lifecycle, 두 benchmark README locale의 두 위치, root README locale, GC evidence,
   chart input, SVG, PNG를 업데이트한다.
8. SVG XML을 검증하고 target PNG를 render/inspect하며, PNG signature와 dimension을 확인하고,
   generated-file allowlist를 검증하고, focused benchmark module validation과 `git diff --check`를 실행한다.
9. evidence commit 전 raw JSON과 documented command에서 absolute home path, host/user identifier,
   token-like JVM property를 제거한다.
10. PR을 열기 전에 implementation/documentation diff에서 workload parity와 evidence integrity를 review한다.

## 위험과 완화

| 위험 | 완화 |
| --- | --- |
| fresh number가 previous report와 크게 다름 | invalid asymmetric snapshot과 비교하지 말고 교체한다. environment와 command metadata는 유지한다. |
| native libvips를 local에서 사용할 수 없음 | environment blocker를 보고하고 refreshed cross-backend claim을 publish하지 않는다. |
| README/chart가 raw JSON에서 drift됨 | raw benchmark output을 source로 취급하고 해당 run에서 모든 derived value를 재생성한다. |
| benchmark setup 실패 후 temporary file이 남음 | failed setup 또는 execution 뒤 `bt4k-image-large-streaming-*` residue를 확인하고 run-owned directory만 제거한다. |

## 인수 기준

- compared benchmark path에 Scrimage-only grayscale operation이 남지 않는다.
- Java 25 FFM/libvips가 준비되지 않으면 cross-backend run이 실패한다. 따라서 published vips row는 실행된 image work를 증명한다.
- benchmark text가 color를 보존하는 `resize -> JPEG encode`를 설명한다.
- primary/GC raw JSON, report, 두 benchmark README locale의 두 위치, root README locale, chart가 표시되는 모든 result에서 일치한다.
- superseded asymmetric result가 current evidence로 오인될 수 없다.
- chart legend가 `large-photo`와 `ocr-document`를 사용하고, scale/tick이 full-size rendered PNG에서 일관되고 읽기 쉽다.
- `git diff --check`, SVG XML validation, PNG signature/dimension inspection, generated-file allowlist verification,
  focused Gradle validation, targeted source-level parity/readiness guard가 통과한다.
