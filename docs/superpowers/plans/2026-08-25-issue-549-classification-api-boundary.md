# Issue #549 ImageClassifier API·ONNX 모듈 경계 실행 계획

## 계획 상태

| 항목 | 값 |
|---|---|
| 대상 | [#549](https://github.com/bluetape4k/bluetape4k-image/issues/549) |
| 상위 train | [#513](https://github.com/bluetape4k/bluetape4k-image/issues/513) → [#3](https://github.com/bluetape4k/bluetape4k-image/issues/3) |
| 선행 입력 | [#543 policy](../research/2026-08-19-issue-543-ai-ml-supply-chain-policy.md), [#548 manifest](../research/2026-08-25-issue-548-model-manifest-provenance.md) |
| 후속 gate | [#550 native/platform·BOM·CI](https://github.com/bluetape4k/bluetape4k-image/issues/550), [#551 adoption](https://github.com/bluetape4k/bluetape4k-image/issues/551) |
| 작업 유형 | Type-E research/design |
| 승인된 write scope | research/spec/plan/lesson/review 문서만 |
| 구현 상태 | production API/module/dependency/model/native runtime 변경 없음 |
| 기준 base | develop @ 979b45a7865b172c250e199d338e9ad8b1c03732 |

## 의존성 순서와 승인 gate

물리적 순서를 바꾸지 않는다.

1. #543 공통 공급망 정책
2. #548 model/head/labels/preprocess/postprocess manifest
3. #549 provider-neutral API와 module 경계 설계
4. #550 ORT native/platform/BOM/CI 검증
5. #551 ADOPT/DEFER/REJECT 결정
6. #551이 ADOPT인 경우에만 Type-A implementation epic과 stacked PR 생성

#551 전에는 다음 mutation을 실행하지 않는다.

- bluetape4k-images-classification-api 또는 bluetape4k-images-classification-onnxruntime project 등록
- ORT/JNI/GPU dependency와 version catalog alias 추가
- public ImageClassifier, ImmutableImage.classify, suspendClassify source 추가
- model/label bytes 체크인, JAR bundling, remote resolver/auto-download 추가
- production benchmark가 model을 내려받거나 native runtime을 초기화하도록 변경

## 설계 산출물

이번 문서 train은 다음 다섯 파일로 고정한다.

| 파일 | 독자 | 완료 증거 |
|---|---|---|
| docs/superpowers/research/2026-08-25-issue-549-classification-api-boundary.md | maintainer, #550/#551 reviewer | official ORT source ledger, repo pattern, alternatives, risks, PENDING gate |
| docs/superpowers/specs/2026-08-25-issue-549-classification-api-boundary.md | API/provider implementer | contract shape, invariants, failures, compatibility, acceptance |
| docs/superpowers/plans/2026-08-25-issue-549-classification-api-boundary.md | implementation planner | exact PR order, files, tests, rollback, approvals |
| docs/superpowers/lessons/2026-08-25-issue-549-classification-api-boundary.md | 후속 train maintainer | reusable guardrails, miss/surprise, future evidence |
| docs/superpowers/reviews/2026-08-25-issue-549-7-tier.md | reviewer/release maintainer | exact-head 7-Tier matrix, independent disposition, DoD |

## 후속 Type-A stacked PR train (ADOPT 이후에만 실행)

### PR-A — provider-neutral API와 fake fixture

Base: develop after #551 ADOPT

Files/modules:

- settings.gradle.kts: bluetape4k-images-classification-api project 등록
- images-classification-api/build.gradle.kts: core images와 coroutine 경계, no ORT dependency
- API source: ImageClassifier/AutoCloseable, capabilities·identity/options/result/error, ImmutableImage sync/suspend extensions
- API tests: fake provider, limits, deterministic ordering, `java.time.Duration` Java consumer fixture, close-after-use, cancellation-before-start

Expected evidence:

- API signature와 generated POM에 ORT/JNI/NDArray/mapper가 없음
- class index/label/confidence/rank invariant와 top-k/tie-break fixture PASS
- `ClassifierCapabilities`의 timeout/batch/top-k/confidence semantics와 `java.time.Duration` Java compile fixture PASS
- CancellationException 재전파와 IO dispatcher contract PASS
- source/binary compatibility/API diff PASS

Rollback/rerun: API module과 settings entry를 revert하고 기존 images artifact만 유지한다. fake fixture가 실패하면 public API를 넓히지 않고 contract를 먼저 수정한다.

### PR-B — ORT CPU provider와 manifest boundary

Base: PR-A exact head

Files/modules:

- images-classification-onnxruntime/build.gradle.kts
- ORT environment와 `SessionOptions → OrtSession → RunOptions/result/pinned-output` lifecycle adapter
- #548 manifest/schema validator와 verified local model resolver
- preprocessing/postprocessing, labels, stable error mapping
- provider tests: malformed model, manifest mismatch, output invalid, close/cancellation race, cleanup order

Expected evidence:

- single-file ONNX, external-data/custom-op/remote URL 거부
- `SessionOptions`가 session 종료 전 닫히지 않고 session/result/tensor/pinned-output close와 exception/cancellation path PASS
- output logits→confidence, label hash/order, deterministic top-k golden PASS
- CPU artifact/native access가 provider module에만 존재

Rollback/rerun: provider artifact를 publish graph에서 제거하고 API fake provider를 유지한다. native smoke 실패 시 API PR을 revert하지 않고 provider PR만 차단한다.

### PR-C — catalog/BOM/consumer smoke

Base: PR-B exact head

Files/modules:

- central version catalog ORT/Jackson 3 alias
- bom/build.gradle.kts constraints와 publication metadata
- versionless API/provider consumer smoke
- public APIElements/runtimeClasspath leakage check

Expected evidence:

- BOM이 지정된 API/provider version을 resolve하고 transitive ORT scope가 API consumer에 새지 않음
- Jackson 3가 implementation-only이고 public POM/API에 노출되지 않음
- Java 25 consumer compile/runtime smoke PASS

Rollback/rerun: catalog/BOM 변경을 함께 revert하고 이미 배포된 API contract와 provider version을 혼합하지 않는다. metadata mismatch는 publish 전 차단한다.

### PR-D — CPU CI·native lifecycle·scheduled matrix

Base: PR-C exact head

Files/modules:

- PR required API/fake tests
- Ubuntu x64·macOS ARM64 CPU native smoke
- Windows periodic CPU smoke, GPU/CUDA manual/nightly lane
- native access flags, path filters, final fail-closed aggregation

Expected evidence:

- PR lane은 network/model auto-download 없이 deterministic fixture만 실행
- CPU native load/run/close와 RSS/thread/session limit receipt 기록
- skipped GPU/Windows는 PASS로 합치지 않고 별도 scope로 표시
- path-filtered workflow가 API/provider/BOM 변경을 놓치지 않음

Rollback/rerun: CI-only 변경은 provider code와 분리해 revert한다. native job failure는 required coverage를 줄여 숨기지 않고 scheduled/manual 대체 lane과 issue를 남긴다.

### PR-E — example/benchmark/quality adoption follow-up

Base: PR-D exact head and separate #551 adoption decision

Files/modules:

- Spring/Ktor 또는 examples에 explicit classifier/provider injection
- same-corpus quality/latency/RSS benchmark와 baseline receipt
- README/manual은 별도 Type-E 문서 PR로 분리

Expected evidence:

- public caller가 provider를 명시하고 silent fallback이 없음
- model manifest/license/SBOM/NOTICE/attestation과 quality corpus가 동일 identity를 가리킴
- benchmark는 cold/warm p50/p95/p99, RSS, thread/session count와 caveat를 기록

Rollback/rerun: adoption gate가 DEFER면 example/provider integration을 merge하지 않고 docs-only 연구 상태를 유지한다.

## 테스트·fixture 계약

| 계층 | 필수 검증 | 네트워크/native 여부 |
|---|---|---|
| API unit | options/limits, result ordering, immutable list, stable error, capabilities, fake provider close | 없음 |
| coroutine | cancellation-before-start, IO dispatcher, exception cleanup | 없음 |
| JSON | Jackson 3 canonical round-trip, unknown/duplicate/trailing/depth/size rejection, SHA-256 | 없음 |
| compatibility | Java caller compile/run (`java.time.Duration` 포함), API diff, generated POM leakage, Serializable marker/serialVersionUID | 없음 |
| provider CPU | model/manifest validation, session/result/tensor close, output mapping | CPU native, #550 gate |
| consumer/BOM | versionless resolution, APIElements/runtimeClasspath, Java 25 | native 없음 또는 provider smoke 별도 |
| benchmark | quality, cold/warm latency, RSS/thread/session, deterministic receipt | model/native, #551 gate |

Fixture는 model auto-download나 moving URL을 사용하지 않는다. #548 manifest가
RESEARCH_ONLY인 동안에는 tiny synthetic ONNX 또는 fake provider만 사용한다.
실제 ResNet inference는 #550/#551 receipt가 없으면 성공 증거가 아니다.

## migration·compatibility·rollback

- 기존 bluetape4k-images, barcode, OCR public API와 artifact coordinate는 변경하지 않는다.
- 새 API artifact를 기존 classifier caller가 자동으로 받도록 core dependency를 바꾸지 않는다.
- public DTO는 provider-specific type과 Java object stream을 노출하지 않는다.
- 기존 module이 새 provider를 사용하려면 명시적인 dependency와 constructor injection을 추가한다.
- migration 중 provider가 없거나 capability mismatch이면 명확한 error를 반환하고 다른 provider로 조용히 전환하지 않는다.
- Type-A train 어느 단계에서도 failure가 나면 가장 최근 exact head를 유지하고, 이전 PR을 cherry-pick해 partial native/API state를 만들지 않는다.
- catalog/BOM, provider, example/benchmark를 각각 revert 가능한 PR로 유지한다.

## 검증 순서와 helper evidence

1. 문서 source ledger와 official URL read-back
2. spec/plan cross-reference 및 code-token preservation
3. Korean terminology audit와 naturalness checklist
4. git diff --check, Markdown fence/EOF, relative link, JSON parse
5. changed-path scope audit: 다섯 문서와 helper receipt 외 production path 없음
6. independent reviewer exact-head read-only 결과
7. main inline follow-up 및 review artifact read-back
8. helper component checks, completion-check, final report
9. commit Lore trailer와 PR body metadata/DoD read-back

이 issue는 docs-only이므로 Gradle/Kotlin/native/OCR/Testcontainers는 production
변경 검증으로 실행하지 않는다. baseline으로 실행한 :bluetape4k-images:test의
704 tests/18 skipped 결과는 변경 전 상태이며, 문서 변경의 correctness를 대신하지
않는다.

## 승인·중단 조건

- WF-03: 사용자가 표시된 첫 concrete plan을 승인해야 문서 mutation을 시작한다.
- WF-04/04A: helper run이 정확히 한 running receipt와 write scope를 보유해야 한다.
- 독립 reviewer timeout: liveness contract에 따라 bounded probe/interrupt를 기록하고 inline review로 대체하되 독립 결과를 만들어내지 않는다.
- #549 종료: 다섯 문서, writer SPW-01~05, independent disposition, exact verification, PR live metadata가 모두 기록된 뒤에만 가능하다.
- #551 종료 전: 구현·dependency·model·module mutation은 BLOCKED다.

## Writer DoD

- SPW-01: PASS — plan 독자, 승인 상태, exact files, upstream/downstream sources를 고정했다.
- SPW-02: PASS — PR order, actions, expected evidence, tests, rollback, rerun, approval gates를 모두 포함했다.
- SPW-03: PASS — 한국어 engineer-to-engineer register와 API/command/URL token preservation을 적용했다.
- SPW-04: PASS — spec→implementation PR mapping과 #548/#550/#551 dependency를 대조했다.
- SPW-05: PASS — implementation은 ADOPT 이후라는 stop condition과 docs-only DoD를 read-back했다.

최종 상태: PLAN READY / APPROVED DOC TRAIN / TYPE-A IMPLEMENTATION BLOCKED
