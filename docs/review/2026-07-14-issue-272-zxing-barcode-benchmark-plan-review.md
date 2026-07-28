# Issue #272 ZXing Barcode Benchmark Plan 검토

## 범위

- Artifact: `docs/superpowers/plans/2026-07-14-issue-272-zxing-barcode-benchmark-plan.md`
- Artifact 종류: plan
- Basis: approved design spec, issue #272, current barcode/provider tests, existing kotlinx-benchmark task와 evidence pattern
- 관점: performance, stability, security, operator/Ops, developer/API, user/caller, 이후 integration 및 Step 3-R ordering review

active native-agent interface에는 `agent_type` field가 없다. 여섯 필수 lens는 documented model-routing fallback 아래 별도 read-only main-session pass로 실행했다.

## 초기 발견 사항

| Priority | 관점/영역 | 근거 | 필요한 plan 수정 | 해결 |
|---|---|---|---|---|
| P1 | Developer/API | Task 1이 signature를 정의하지 않은 채 `BarcodeBenchmarkFixture`와 `loadForTest`를 참조했다. | 이후 test가 사용하기 전에 runtime wrapper와 정확한 injected test-loader API를 정의한다. | Task 1 Steps 3-4에서 수정. |
| P1 | Security | entry path validation이 scenario 하나를 선택할 때만 발생했다. | 모든 decoded manifest entry에 path 및 semantic validation을 둔다. | Task 1 Step 3에서 수정. |
| P1 | Stability/Ops | temporary generator가 기존 source fixture directory를 overwrite할 수 있었다. | 기존 output path를 거부하고 전체 PNG+manifest set을 하나의 reviewed unit으로 다시 생성한다. | Task 1 Steps 5-6에서 수정. |
| P2 | Build/API | Provider configuration inspection에 exact command나 expected contrast가 없었다. | `runtimeClasspath`와 `benchmarkRuntimeClasspath`를 명시적으로 비교한다. | Task 2 Step 6에서 수정. |

## Lens 재실행

| 관점 | 판정 | 근거 |
|---|---|---|
| Performance | PASS | Tasks 2와 4는 extraction call을 isolate하고 두 real JMH mode를 고정하며 reciprocal metric derivation을 금지한다. |
| Stability | PASS | Tasks 1, 3, 4는 timing 전 fixture/report를 validate하고 fresh staging과 append-only promotion을 사용한다. |
| Security | PASS | Task 1은 strict JSON, exact scenario set, normalized fixed-prefix path, size bound, hash, malformed input을 cover한다. |
| Operator/Ops | PASS | Tasks 3-4는 run ownership, collision behavior, environment capture, failure cleanup, sequential rerun point를 정의한다. |
| Developer/API | PASS | Tasks 1-2는 consumer 전에 모든 used type을 정의하고 provider를 benchmark/test configuration에 confine한다. |
| User/caller | PASS | Task 5는 exact report/README content, metric direction, locale parity, link, caveat를 할당한다. |

## Step 3-R 통합

- 모든 spec acceptance criterion은 ordered task와 fresh proof에 map된다.
- 어떤 task도 producer task 완료 전에 fixture byte, task name, raw JSON, documentation을 소비하지 않는다.
- success, missing/malformed input, traversal, oversize, hash/dimension drift, expectation mismatch, stale report, wrong row/mode/unit, duplicate accepted target, documentation drift가 할당됐다.
- concurrency, coroutine cancellation, HTTP, Spring, Exposed, Testcontainers, OCR, native/JNI, new-module registration, migration, closeable-resource lifecycle은 approved synchronous pure-JVM existing-module scope에서 N/A다. Task 6은 final diff를 기준으로 이 근거를 다시 확인하도록 요구한다.
- README English/Korean parity, English benchmark KDoc, issue-linked PR metadata, benchmark hazard, rollback/rerun point, lesson capture가 할당됐다.
- plan에는 unresolved manual hash substitution이 없다. reviewed generator가 모든 fixture byte와 hash-pinned manifest를 함께 생성한다.

Latest convergence: **P0=0, P1=0**. 하나의 P2 finding은 수정됐다.

Required checks: 21/21; N/A: 8; Blocked: 0.
