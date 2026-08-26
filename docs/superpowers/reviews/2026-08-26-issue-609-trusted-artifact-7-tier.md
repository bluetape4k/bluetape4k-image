# Issue #609 trusted artifact producer 7-Tier review

## 검토 메타데이터

| 항목 | 값 |
|---|---|
| 대상 | Issue #609 연구·계획·lesson·artifact-ledger contract |
| 기준 head | `fb15509b46a6c7103e8db58a67f2ea0708af7a88`에서 시작한 `docs/issue-609-trusted-artifact-gate` |
| 변경 범위 | `docs/superpowers/` 연구·계획·lesson·review 4개와 artifact-ledger JSON 1개. Kotlin/API/dependency/model/service/runtime 변경 없음 |
| workflow | Type-E `bluetape-flow` run `20260826T094849Z-f412674b` |
| 독립 lane | `issue-609-independent-review` 공식 source reviewer + 보조 `issue607_independent_review` read-only review |
| 현재 상태 | `BLOCKED / REQUEST CHANGES / TRUSTED ARTIFACT AND EXECUTION PENDING` |

## 판정 요약

독립 reviewer는 실제 artifact를 확보했다고 가장하지 않은 점과 공식 source 추적성은
확인했지만, 첫 검토와 재검토에서 ledger evidence binding·platform mapping·receipt
readback·문서 gate의 과장을 발견했다. main lane은 해당 계약·문서 결함을 보강했으며,
strict validator와 실제 artifact는 의도적으로 후속 #609-C/D/E gate로 남겼다.

초기 독립 판정은 `REQUEST CHANGES / BLOCKED`다. 초기 finding을 고친 뒤 독립
re-review를 요청했으며, re-review가 완료되기 전에는 이 문서를 최종 PASS로 쓰지
않는다.

| 심각도 | 초기 finding | 현재 disposition |
|---|---:|---|
| P0 | 0 | 0 |
| P1 | 4 | 초기 4건은 contract 보강으로 닫혔고 재검토 3건은 이번 문서 보강으로 닫았지만 실제 artifact gate는 여전히 막힘 |
| P2 | 6 | 초기 6건과 재검토 4건 및 후속 2건은 문서·readback 보강으로 닫았고, 실행 validator/runtime 증명은 후속 gate |
| P3 | 0 | 0 |

## 7-Tier matrix

| Tier | 검토 범위 | 독립 초기 판정 | main disposition |
|---|---|---|---|
| 1. 계약·범위 | #609/#545/#544/#546/#547/#169/#513, Type-E/non-goal | PASS | PASS — #546의 실제 provider-neutral OCR API·PaddleHTTP adapter 경계를 정정하고 후속 순서를 고정 |
| 2. 보안·공급망 | producer allowlist, digest, model tree, SBOM/provenance/signature, NOTICE | CONDITIONAL | path/SHA/identity/verified 및 license/NOTICE/package-lock invariant를 문서 계약에 연결했지만 실제 artifact는 `PENDING`, unknown producer는 `BLOCKED` |
| 3. 정확성·추적성 | JSON path, executed image subject, OCI index→platform→config/base | CONDITIONAL | role별 tree verifier 출력, subject evidence, trust policy와 platform mapping을 명시했지만 실행 validator·실제 subject는 `PENDING` |
| 4. 운영·플랫폼 | arm64/amd64, emulation, offline/no-egress, policy vs observed | CONDITIONAL | `execution.policy`와 `execution.observed`를 분리하고 descriptor↔target platform equality를 기록했지만 실제 receipt는 `PENDING` |
| 5. 성능·benchmark | #544 동일 corpus와 native runtime | N/A/PENDING | artifact가 없어 숫자를 만들지 않고 #544-B 후속 단계로 보류 |
| 6. API·호환성 | Kotlin, dependency, public service/API | N/A | Kotlin/API/dependency 변경 0개. `$bluetape-kotlin-patterns`는 `N/A`, Kotlin 품질 PASS 아님 |
| 7. 문서·CI·release | Korean writer SPW-01~05, links, PR/live CI, release boundary | BLOCKED | 문서별 SPW 표와 명령·경로를 정렬했지만 executable ledger validator·PR live gate·실제 artifact는 보류 |

## 독립 reviewer finding과 disposition

### P1 — ledger identity와 execution subject

| ID | 관찰 | 수정/잔여 경계 |
|---|---|---|
| R-01 | `requiredFields`가 `producer.*`와 `model.models[]` 실제 구조를 가리키지 않았고 detector/recognizer pair가 강제되지 않았다. | **CLOSED** — JSON path semantics, `role`, detector/recognizer 두 항목, `pairBindingSha256`, canonical sorted file path, file bytes/SHA/tree/NOTICE 필드를 추가했다. |
| R-02 | invariant의 `executedImageDigest`가 ledger에 없고 SBOM/provenance/signature subject와 실행 receipt 연결이 불명확했다. | **CLOSED (contract)** — `execution.observed.executedImageDigest`, receipt SHA와 subject equality 규칙을 추가했다. 실제 digest는 후속 실행 전까지 `PENDING`이다. |
| R-03 | G5 보안 요구와 invariant가 network/model source만 확인해 non-root, cap drop, limits, redacted log, cleanup을 누락했다. | **CLOSED (contract)** — policy/observed 분리, cap/request/resource/log/cleanup/no-egress 필드와 verification flags를 추가했다. 실제 관측값은 `PENDING/false`다. |
| R-04 | signer/repository/workflow/builder/OIDC allowlist가 없어 서명만으로 trusted producer를 판정할 위험이 있었다. | **CLOSED (contract)** — `trustPolicy` allowlist, workflow ref/OIDC issuer, policy hash와 full-commit/subject verification 규칙을 추가했다. 실제 허용 값은 producer 선택 단계에서 채운다. |

### P2 — state, schema, 문서·운영 경계

| ID | 관찰 | 수정/잔여 경계 |
|---|---|---|
| R-05 | `PENDING/BLOCKED/REJECTED/DEFER` 의미가 표마다 달라질 수 있었다. | **CLOSED** — ledger·plan·research·lesson에 canonical state machine과 retry/reject/decision 경계를 기록했다. |
| R-06 | 계획은 별도 7-Tier review artifact를 요구했지만 review 파일이 없었다. | **CLOSED** — 이 문서가 독립 lane provenance, finding, disposition, SPW와 최종 gate를 보존한다. |
| R-07 | policy 선언을 실제 관측으로 읽을 수 있었다. | **CLOSED** — `execution.policy`와 `execution.observed`를 분리하고 observed 값은 `PENDING/false`로 초기화했다. |
| R-08 | Cosign 예제가 exact identity와 regexp flag를 혼용했다. | **CLOSED** — `--certificate-identity-regexp`와 allowlist 규칙으로 정정했다. |
| R-09 | model file list, path/symlink/size, license/NOTICE binding이 빈 배열 예시에 머물렀다. | **CLOSED (contract)** — JSON path semantics와 role별 canonical file entry를 명시했다. 실제 files/bytes는 후속 #609-C에서 채운다. |
| R-10 | OCI index→platform→config/base 연결 검사가 invariant에 없었다. | **CLOSED (contract)** — verification의 index selection/config/base flags와 invariant를 추가했다. 실제 manifest 검증은 후속 #609-C/D다. |
| R-11 | 계획의 #546 설명이 receipt 보강으로 잘못 적혔다. | **CLOSED** — live title인 provider-neutral OCR API·PaddleHTTP adapter 경계로 정정했다. |
| R-12 | PaddleX 3.5 installation URL과 pinned 3.7 source의 버전 맥락이 섞였다. | **CLOSED** — 3.5 URL은 historical installation example로 표시하고 현재 producer 후보와 분리했다. |
| R-13 | Tier 3/4/7의 PASS가 실제 artifact/runtime PASS로 읽힐 수 있었다. | **CLOSED** — `설계 계약 PASS / 실제 subject·실행 PENDING`, SPW·PR live gate 보류로 표현을 좁혔다. |
| R-14 | workflow run ID를 target worktree에서 추적하지 않으면 승인 근거로 오해할 수 있었다. | **CLOSED (readback pending)** — canonical state root에 run을 생성했고 이 PR 검증에서 receipt head/completion을 다시 읽는다. |
| R-15 | SPW-01~05의 결과를 문서에서 직접 확인할 수 없었다. | **CLOSED** — research/plan/lesson에 대상·실패·한국어·source readback·불확실성 표를 추가했다. |

## 독립 re-review 잔여 finding

보강본을 다시 읽은 독립 reviewer는 다음을 남겼다. 이 항목은 실제 runtime을
실행하지 않는 이번 Type-E 범위와 후속 validator/receipt gate를 구분하기 위해
문서에 보존한다.

| ID | 등급 | 관찰 | disposition |
|---|---|---|---|
| RR-01 | P1 | invariant에 observed non-root/read-only/cap/resource/request limit와 모든 receipt/verification flag가 빠질 수 있었다. | **CLOSED (contract)** — plan invariant에 privilege·limit·receipt·image/SBOM/provenance/signature/model/offline/cleanup checks를 추가했고, 실제 관측값은 여전히 `PENDING/false`다. |
| RR-02 | P1 | trust policy required path에 workflow ref/OIDC issuer와 non-empty allowlist/policy hash가 없었다. | **CLOSED (contract)** — `producer.workflowRef`, required evidence path, `policySha256`, `allowlistNonEmpty`, producer-ref membership과 PASS semantics를 추가했다. |
| RR-03 | P1 | `bluetape-flow` run ID의 canonical state-root receipt readback이 review evidence에 없었다. | **CLOSED (receipt readback)** — explicit `--state-root /Users/debop/work/bluetape4k/bluetape4k-image/.bluetape`의 fresh `verify`는 main lane completion 시점에 `event_count=20`, `sequence=20`, head `4f9b64681f3e9ce70c18eccef4230261e9c824ab05c595d4494d2da4fe3c6f60`를 반환했다. 이후 required checks/component evidence가 통과했고 `complete`가 sequence 31, head `48da785e0ff273d66a85111e83695fed73c34013447bb77d2b100663fd027da5`에서 run을 `completed`로 전환했다. |
| RR-04 | P2 | live Issue #609 `Blocked: 7`과 plan gate `PENDING`의 의미가 분리되지 않았다. | **CLOSED** — plan에 live `0/7; Blocked: 7`과 stage-local `PENDING`의 차이를 기록했다. |
| RR-05 | P2 | detector/recognizer tree 및 pair hash canonical serialization이 모호했다. | **CLOSED** — role별 sorted `path\\tbytes\\tsha256\\n`, `recomputedModelTreeSha256[role]` 출력 매핑과 고정 detector→recognizer pair hash를 plan/ledger에 기록했다. |
| RR-06 | P2 | SPW-04가 executable ledger validator를 현재 evidence처럼 표현했다. | **CLOSED** — 모든 문서에 URL HTTP 200, receipt/smoke test 명령, ledger structural audit를 현재 evidence로 기록하고 executable validator는 #609-C 후속으로 명시했다. |
| RR-07 | P2 | model card URL이 moving `main`으로 남아 pinned source 주장과 충돌했다. | **CLOSED** — detector/recognizer full revision URL을 고정하고 bytes/tree/NOTICE는 여전히 PENDING으로 남겼다. |
| RR-08 | P2 | re-review 전 P1/P2 0건으로 DoD를 선기록했다. | **CLOSED** — aggregate를 `BLOCKED / REQUEST CHANGES`로 고정하고, 문서 계약 finding을 닫아도 실제 artifact/runtime gate는 별도로 미완료로 남겼다. |
| RR-09 | P1 | `producer.workflowRef`만 allowlist membership으로 연결되어 repository·workflow path·builder·signer·OIDC binding이 opaque `trustPolicyMatched`에 남았다. | **CLOSED (contract)** — ledger semantics와 plan invariant에 다섯 identity/path membership, workflow ref, non-empty allowlist, policy hash의 conjunction을 명시했다. 실제 값과 verifier는 #609-B/C 후속이다. |
| RR-10 | P2 | plan/review SPW-04가 ledger structural audit의 정확한 명령·경로를 생략했다. | **CLOSED (traceability)** — research/plan/lesson/review의 SPW-04 표에 동일한 ledger JSON path, `json.tool`, required-path/role `python3 -c` command, receipt/smoke 명령을 기록했다. |

## 상태·실행 보류선

현재 `PENDING`은 실제 artifact를 아직 시도하지 않았음을 뜻한다. registry 권한·
artifact 부재·선행 receipt가 원인이면 `BLOCKED`, digest/subject/서명/allowlist
불일치면 `REJECTED`, 증거와 별도로 채택 결정을 미루면 `DEFER`다. 이 상태들을
서로 바꿔서 실행이나 adoption을 열지 않는다.

다음은 이 review의 PASS 범위가 아니다.

- trusted producer의 실제 image index/platform/config/base digest
- detector/recognizer model bytes, tree SHA, license/NOTICE inventory
- SPDX SBOM, in-toto/SLSA provenance, Cosign/GitHub attestation과 signer 검증
- clean `network=none` offline smoke, no-egress, resource/cleanup receipt
- #544 동일 corpus benchmark, #547 ADOPT, #169 Type-A implementation

## Writer SPW-01~05

| 항목 | 증거 | 판정 |
|---|---|---|
| SPW-01 대상·독자·범위 | 메타데이터, plan dependency, non-goal | PASS |
| SPW-02 실행·실패·재개 | stacked train, 7 gates, state machine, stop conditions | PASS |
| SPW-03 한국어·machine token | Korean prose와 API/command/URL/digest/status 보존 | PASS |
| SPW-04 source·readback | pinned official URL HTTP 200 readback, `python3 scripts/research/test_paddle_ocr_receipt.py`, `python3 scripts/research/test_paddle_ocr_smoke.py`, `python3 -m json.tool docs/superpowers/research/2026-08-26-issue-609-artifact-ledger-contract.json`, `python3 -c 'import json; d=json.load(open("docs/superpowers/research/2026-08-26-issue-609-artifact-ledger-contract.json")); assert {"producer.workflowRef","image.packageLockSha256","licenseNotice.complete","sbom.path","provenance.path","signature.path","execution.policy.targetPlatform","execution.observed.targetPlatform"} <= set(d["requiredFields"]); assert {m["role"] for m in d["model"]["models"]} == {"detector","recognizer"}; print("artifact-ledger structural audit: PASS")'`, live Issue #609/#546 readback | PASS(문서 범위) — executable ledger validator는 #609-C 후속 |
| SPW-05 사실·불확실성 | `PENDING/BLOCKED/REJECTED/DEFER`, 실제 artifact 부재 명시 | PASS |

## 독립 lane provenance

- `issue609_official_research`: 공식 PaddleOCR/PaddleX·Docker·SPDX·in-toto/SLSA·Cosign source 조사와 1차 read-only review를 반환했다. 초기 판정은 `REQUEST CHANGES / BLOCKED`다.
- `issue607_independent_review`: 별도 read-only lane이 동일 초안을 검토해 초기 `CONDITIONAL / REQUEST CHANGES`와 P0=0/P1=4/P2=6/P3=0을 반환했다.
- 두 독립 reviewer lane 모두 파일·GitHub·receipt를 수정하지 않았다. main lane은 초기 및 재검토 findings를 모두 문서 계약에 반영했지만, 재검토 aggregate는 `BLOCKED / REQUEST CHANGES`로 유지한다. canonical workflow state는 저장소 root `.bluetape`를 explicit `--state-root`로 사용한다. main lane completion 시점 fresh receipt readback은 `event_count=20`, `sequence=20`, head `4f9b64681f3e9ce70c18eccef4230261e9c824ab05c595d4494d2da4fe3c6f60`였고, required checks/component evidence 후 `complete`가 sequence 31/head `48da785e0ff273d66a85111e83695fed73c34013447bb77d2b100663fd027da5`에서 run을 `completed`로 전환했다.
- 독립 reviewer의 재검토는 P0=0/P1=3/P2=4/P3=0이었고, path/SHA/identity/verified binding·license/NOTICE/package lock·workflow ref·platform/tree mapping·SPW/readback 문제를 지적했다. 이 문서의 contract disposition은 그 finding을 닫았으나, 실제 artifact·validator·runtime 증명은 닫지 않았다.
- 후속 독립 readback은 P0=0/P1=1/P2=1/P3=0으로 field-level trust binding과 SPW command/path를 추가 지적했다. RR-09/RR-10으로 contract에 반영했지만, 그 lane의 최종 verdict도 실제 trusted artifact/runtime 부재로 `REQUEST CHANGES / BLOCKED`다.

## DoD Status

- [x] 독립 reviewer lane 2개의 source·ledger·7-Tier·writer 검토와 초기 finding을 기록했다.
- [x] 초기 P1/P2 contract·문서 경계 finding을 disposition하고 후속 #609-C/D/E의 실제 증거 gap을 남겼다.
- [x] 재검토 P1/P2를 문서 계약·readback·SPW evidence에 반영하고 실제 artifact gate와 분리했다.
- [x] 후속 field-level trust binding·SPW command/path finding(RR-09/RR-10)을 반영했다.
- [x] 실제 trusted artifact·offline execution·benchmark·adoption을 PASS로 승격하지 않았다.
- [x] `docs/superpowers/research/`, `plans/`, `lessons/`, `reviews/` 및 ledger만 변경했다.
- [x] 독립 re-review 결과와 최종 aggregate `BLOCKED / REQUEST CHANGES`를 기록했다.
- [ ] PR live metadata/checks/review threads와 exact-head readback
- [ ] trusted producer artifact·attestation·offline smoke·#544 benchmark

최종 상태: `BLOCKED / REQUEST CHANGES / RE-REVIEW AND TRUSTED ARTIFACT GATES PENDING`
