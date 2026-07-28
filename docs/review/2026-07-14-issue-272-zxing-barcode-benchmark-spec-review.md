# Issue #272 ZXing Barcode Benchmark Spec 검토

## 범위

- Artifact: `docs/superpowers/specs/2026-07-14-issue-272-zxing-barcode-benchmark-design.md`
- Artifact 종류: spec
- Research basis: issue #272, barcode API/provider source와 tests, issue #247 fixture decision, 기존 `images-benchmark` configuration과 report
- 관점: performance, stability, security, operator/Ops, developer/API, user/caller, 이후 main-session integration

active native-agent interface는 필수 `agent_type` field를 노출하지 않는다. `model-routing.md`에 따라 각 필수 lens는 agent role을 지어내지 않고 별도 read-only main-session pass로 실행했다.

## 초기 발견 사항

| Priority | 관점 | 근거 | 필요한 수정 | 해결 |
|---|---|---|---|---|
| P1 | Developer/API | Sections 7-8이 새 provider dependency configuration을 제한하지 않았다. | ZXing을 `benchmarkImplementation`과 `testImplementation`에만 두고 main/published dependency surface를 보존한다. | Sections 7.1, 8, 12에서 수정. |
| P1 | Operator/Ops | Sections 9와 11이 raw JSON을 요구했지만 collision-safe accepted-run ownership을 정의하지 않았다. | validated run id, fresh build staging, append-only accepted directory, 하나의 run manifest를 사용한다. | Sections 9와 11에서 수정. |
| P2 | Security | manifest-controlled resource path와 encoded input size에 explicit bound가 없었다. | classpath prefix를 제한하고 traversal/absolute path를 거부하며 fixture마다 1 MiB cap을 둔다. | Sections 6, 7.1, 9, 10에서 수정. |

## 재실행 판정

| 관점 | 판정 | 근거 |
|---|---|---|
| Performance | PASS | Sections 7.2-7.3은 `readBarcodes`를 isolate하고 두 mode에 같은 scenario를 사용하며 thread/fork/warmup/measurement condition을 고정한다. |
| Stability | PASS | Sections 6, 9, 11은 fixture/expectation error에서 measurement 전에 실패하고 accepted-evidence overwrite를 방지한다. |
| Security | PASS | Sections 6과 9는 manifest resource path와 byte를 제한한다. external input, secret, network call은 없다. |
| Operator/Ops | PASS | Sections 9와 11은 run identity, staging, immutable promotion, environment capture, rerun behavior를 정의한다. |
| Developer/API | PASS | Section 8은 ZXing import를 provider에 두고 provider dependency를 benchmark module의 main/published surface 밖에 둔다. |
| User/caller | PASS | Sections 11-13은 runnable command, metric direction, bilingual README parity, conservative interpretation을 요구한다. |

## 통합 판정

- alternative, boundary, compatibility, failure mode, testability, acceptance criteria가 명시적이다.
- Chart N/A는 evidence-backed이다. provider 하나, workload shape 세 개, unit과 direction이 맞지 않는 metric 두 개가 있기 때문이다.
- CHANGELOG/WIP는 #270/#271로 defer된 상태가 맞다.
- Latest convergence: **P0=0, P1=0**. P2 manifest-bound finding은 수정됐다.

Required checks: 7/7; N/A: 0; Blocked: 0.
