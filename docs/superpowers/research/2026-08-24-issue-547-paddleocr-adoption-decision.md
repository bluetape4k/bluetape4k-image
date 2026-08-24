# Issue #547 PaddleOCR 채택 게이트 결정

## 결정

**PaddleOCR provider는 `DEFER`한다.** 0.5.0 기본 OCR provider는
Tesseract/Tess4J baseline으로 유지한다. self-hosted HTTP service는 향후
재평가할 수 있지만, 현재 production dependency·모델·public API·HTTP adapter를
추가하지 않는다.

이번 결정은 PaddleOCR의 영구적인 `REJECT`가 아니다. 같은 corpus의 품질·성능
비교와 실제 service 공급망 증적이 아직 없으므로, 채택을 정당화할 수 있는 증거가
완성되지 않았다는 뜻이다. `ADOPT`로 바꾸려면 아래 재평가 조건을 모두 새 receipt로
충족하고, 별도 Type-A 구현 issue와 계획을 승인해야 한다.

여기서 `0.5.0 기본 OCR provider`는 #513 stacked research train에서 고정한 기본
provider 범위를 뜻한다. 현재 GitHub live milestone은 #513·#169·#544–#547 모두
`1.0.0`이므로, 이 문서는 milestone이나 release version을 변경하지 않는다.

## 추적성

| 입력 | 정확한 기준 | 현재 의미 |
|---|---|---|
| 공통 정책 [#543](https://github.com/bluetape4k/bluetape4k-image/issues/543) | [`2026-08-19-issue-543-ai-ml-supply-chain-policy.md`](2026-08-19-issue-543-ai-ml-supply-chain-policy.md), commit `83f8a3b888425e4706ab8e0a7d92e4700a6d4868` | provenance, offline cache, license/SBOM, 보안, CI tier를 채택 전제조건으로 고정했다. 정책 문서 자체는 실제 backend 실행 결과가 아니다. |
| 품질·성능 [#544](https://github.com/bluetape4k/bluetape4k-image/issues/544) | benchmark 계약 [`2026-08-19-issue-544-ocr-benchmark-corpus.md`](2026-08-19-issue-544-ocr-benchmark-corpus.md), commit `83f8a3b888425e4706ab8e0a7d92e4700a6d4868`; 실행 receipt [`2026-08-24-issue-544-tesseract-baseline-receipt.md`](2026-08-24-issue-544-tesseract-baseline-receipt.md), merge commit `8c3f152cc5b44d3a4007197fa112ffb392340751`, run `issue-544-20260824-macos-arm64-java25-baseline` | corpus provenance와 Tesseract latency/throughput 기준선은 `PASS`다. 상태는 `BASELINE_ONLY`이며 PaddleOCR 결과·품질 지표·자원 비교는 `PENDING`이다. receipt 내부의 benchmark 기준 commit `83f8a3b888425e4706ab8e0a7d92e4700a6d4868`과 문서 merge commit을 혼동하지 않는다. |
| service·공급망 [#545](https://github.com/bluetape4k/bluetape4k-image/issues/545) | service 연구 [`2026-08-19-issue-545-paddleocr-service-security-ci.md`](2026-08-19-issue-545-paddleocr-service-security-ci.md), commit `83f8a3b888425e4706ab8e0a7d92e4700a6d4868`; receipt contract [`2026-08-24-issue-545-receipt-contract.md`](2026-08-24-issue-545-receipt-contract.md), commit `690dcc92344e579a3c36b953d1229e03e8f8f4f7`; smoke harness [`2026-08-24-issue-545-smoke-harness.md`](2026-08-24-issue-545-smoke-harness.md), commit `e53f9b59a0bb240403489409c604dd40f8ef9438` | 연구·receipt 계약과 입력 `PREFLIGHT`는 고정했다. 실제 digest-pinned service, offline startup, OCR smoke, SBOM/attestation, 로그·cleanup 증적은 `PENDING`이다. |
| API 경계 [#546](https://github.com/bluetape4k/bluetape4k-image/issues/546) | 누적 계획 [`2026-08-23-issue-546-ocr-api-boundary.md`](../plans/2026-08-23-issue-546-ocr-api-boundary.md)와 독립 review [`2026-08-23-issue-546-ocr-api-boundary-review.md`](../reviews/2026-08-23-issue-546-ocr-api-boundary-review.md), 초기 plan merge commit `13ddbb6b7e8a71f3b10f692634fcd48ea4cc29d6`, 현재 develop 누적 통합 commit `83f8a3b888425e4706ab8e0a7d92e4700a6d4868` | provider-neutral API와 조건부 PaddleHTTP 경계를 문서로 고정했다. production API/module 구현은 이 결정 이후의 별도 Type-A 범위다. 새 wire codec의 기본 구현은 Jackson 3 implementation-only로 유지하고 public API에 mapper를 노출하지 않는다. |

위 네 입력의 `commit`은 현재 `develop`에서 read-back했다. 짧은 SHA나 이전
worktree 상태를 채택 증거로 사용하지 않는다. #543 정책 commit에는 누적 research
문서도 포함되어 있으므로, 각 child issue의 개별 receipt 상태를 별도로 확인했다.

### Live 상태와 범위

2026-08-24에 GitHub를 다시 조회한 결과 #543은 `CLOSED`, #544·#545·#546·#547은
`OPEN`이며, parent #169와 main epic #513도 `OPEN`이다. 따라서 이 결정이 완료되어
#547을 닫더라도 실제 benchmark/service 실행을 추적하는 #544·#545와 child epic
#169, main epic #513을 완료로 표시하지 않는다.

## 게이트 평가

| 게이트 | 합격 기준 | 현재 상태 | 결정 영향 |
|---|---|---|---|
| 모델 provenance·offline·license·CI 정책 | 모델·runtime·container identity, hash, license/SBOM, no-network CI 계약 | `PASS` — #543 정책 문서 | 필수 정책은 있으나 실행 결과를 대신하지 않는다. |
| 동일 corpus 재현성 | 한국어·영어·일본어 및 저해상도·회전·잡음·표·다단 fixture의 provenance/hash/정답 | `PASS` — #544 manifest와 fixture hash | 비교 입력은 고정되었다. |
| Tesseract 기준선 | 최소 반복·warm-up과 raw artifact hash를 포함한 기준 측정 | `PASS` — #544 `BASELINE_ONLY` receipt | 0.5.0 기본 provider를 유지할 기준선이다. |
| PaddleOCR 동일 입력 비교 | 같은 fixture에서 provider 결과와 raw JSON/manifest를 보존 | `PENDING` | 품질·성능 우위의 방향을 판단할 수 없다. |
| 품질 지표 | CER/WER, geometry 정확도, empty/error taxonomy와 실패 사례 | `PENDING` | vendor 수치만으로 채택할 수 없다. |
| 운영 자원 지표 | cold/warm startup, latency/throughput, RSS/peak memory, concurrency/timeout | `PENDING` | 운영 비용과 JVM 호출자 영향이 미정이다. |
| service 공급망 | digest-pinned image, pre-baked model tree, model hash, SPDX SBOM, provenance/SBOM attestation, NOTICE | `PENDING` — #545 `CONTRACT_ONLY/PREFLIGHT` | 검증된 배포 단위가 없다. |
| 격리·보안 실행 | offline egress 차단, auth/TLS, non-root/read-only, limits, no-log, cleanup | `PENDING` — 실제 Docker service 미실행 | `PREFLIGHT_PASS`를 운영 보안 승인으로 승격할 수 없다. |
| provider-neutral API 설계 | legacy migration, geometry/error/cancellation/limits, module/BOM/CI 분해 | `PASS` — #546 설계 범위 | 구현 시작 권한을 주지 않으며, 채택 시 Type-A 입력으로 사용한다. |

`PENDING`인 비교·실행 게이트가 하나라도 남아 있으므로 `ADOPT` 조건을 충족하지
않는다. 반대로 현재 자료는 PaddleOCR를 영구적으로 배제할 품질·성능·운영 실패를
입증하지 않으므로 `REJECT`로 확정할 근거도 없다. 따라서 `DEFER`가 현재 증거에
맞는 결정이다.

## 선택지와 trade-off

| 선택지 | 이점 | 비용·위험 | 이번 결정 |
|---|---|---|---|
| `ADOPT` — self-hosted HTTP를 Type-A로 구현 | Python/native runtime과 JVM을 분리하고 다국어·문서 구조화 기능을 확장할 수 있다. | 동일 corpus 우위, 모델 공급망, offline 운영, 서비스 비용과 실패 분류가 아직 입증되지 않았다. 지금 진행하면 정책·게이트를 우회한다. | 보류 |
| `DEFER` — Tesseract baseline 유지 | 검증된 현재 경로를 차단하지 않고, unverified model/download와 CI 비용을 피한다. 재평가 기준을 명확히 남길 수 있다. | PaddleOCR의 잠재적인 품질·다국어 이점을 당장 사용할 수 없고, 후속 benchmark/service 실행 비용이 남는다. | **채택** |
| `REJECT` — PaddleOCR 평가 종료 | runtime·model·service 복잡도를 영구적으로 제거한다. | 실제 비교 결과 없이 품질·비용 개선 가능성까지 닫으며, 향후 요구가 생기면 근거를 다시 수집해야 한다. | 현재는 이르다 |

호출 경계는 #546의 결정을 따른다. JVM 내부 Python/JNI 결합과 호출별 CLI는 ABI,
allocator, lifecycle, cold-start 위험 때문에 후보에서 제외하고, self-hosted HTTP만
조건부로 남긴다. HTTP를 선택하더라도 endpoint URL, credential, provider-specific
option을 common OCR API에 노출하지 않는다. 후속 구현에서 wire serialization이
필요하면 Jackson 3 private codec을 사용하되, Kotlinx serialization이나 mapper
타입을 public contract의 기본값으로 추가하지 않는다.

## 0.5.0에 남기는 범위

- `bluetape4k-images-ocr`의 Tesseract/Tess4J baseline을 기본 provider로 유지한다.
- #544의 corpus manifest와 Tesseract raw baseline은 비교의 기준 자료로 보존한다.
- #545의 receipt contract와 `PREFLIGHT` harness는 실제 실행 전 입력 검증으로만
  사용한다. `CONTRACT_ONLY` 또는 `PREFLIGHT_PASS`를 OCR 품질·운영 보안 승인으로
  해석하지 않는다.
- #546의 provider-neutral API 계획은 구현 전에 참조할 설계 원본으로 유지한다.
- PaddleOCR dependency, model binary/자동 다운로드, production service, public
  OCR API/module, BOM/catalog 변경, hosted CI 추가를 만들지 않는다.

## 재평가 조건

다음 재평가는 별도 실행 receipt와 새 decision comment를 사용해야 한다. 이전
`BASELINE_ONLY`, `CONTRACT_ONLY`, `PREFLIGHT` 결과를 새 결과로 재사용하지 않는다.

### 1. #544 비교 증적

#544 benchmark 계약의 `provisional 채택 gate`를 수치 기준으로 그대로 적용한다.
이 기준은 production SLO가 아니라 추가 조사 가치와 재현성 여부를 판단하는
gate이며, 기준 문서는 [`2026-08-19-issue-544-ocr-benchmark-corpus.md`](2026-08-19-issue-544-ocr-benchmark-corpus.md)의
`반복·순서·통계`·`provisional 채택 gate` 절이다.

- full acceptance corpus는 9개 scenario class마다 최소 3개 fixture(총 27개),
  `eng`·`kor`·`jpn` 각각 최소 9개를 포함한다. 각 언어에는 single-language와
  mixed-language fixture를 각각 최소 3개씩 넣고, table·multi-column layout도
  각각 최소 3개를 포함한다.
- 동일 workload·corpus·host·resource envelope에서 최소 3개 독립 run을 수행한다.
  warm 측정은 p50/p95/p99를 산출하고, HTTP service cold는 매 run 새 process/container를
  시작해 독립 run마다 정확히 10회 launch(총 30회)을 raw ledger에 기록한다.
- 품질 gate는 macro CER 상대 개선률 `>= 10%`, 필수 language/scenario별
  `candidate <= baseline + 0.05`, geometry 지원 시 macro F1 `+0.05` 또는 승인된
  구조적 사유, `EMPTY`/`ERROR`/`PARTIAL` 분류 100% 일치를 요구한다. malformed와
  limit 초과 입력을 성공으로 위장하지 않는다.
- 공통 limit profile은 encoded image `5 MiB`(transport `6 MiB`), `maxPages=1`,
  `maxPixels=16_777_216`, `maxSide=8_192`, OCR deadline `3 s`, bounded concurrency
  `{1, 2, 4}`로 고정한다. profile을 적용하지 못한 row는 성공 수치가 아니라
  `LIMIT_PROFILE_MISMATCH`다.
- 성능·자원 gate는 quality gate를 통과한 동일 scenario의 warm p95 `<= 1.5x`,
  peak RSS/native memory `<= 2x` 목표, warm p95·warm RSS relative MAD `<= 10%`,
  cold-start relative MAD `<= 20%`를 요구한다. timeout, cancellation, bounded
  concurrency, retry budget도 결정적으로 동작해야 한다.

- 동일 manifest·fixture를 Tesseract와 PaddleOCR에 적용하고 fixture/provenance/license/
  SHA-256을 raw artifact에 다시 기록한다.
- 동일 resource envelope에서 사전에 정한 warm-up과 최소 3회 독립 반복을 지키고, latency·throughput의 측정
  명령·환경·unit·mode·오차를 함께 보존한다.
- CER/WER, page/block/line/word geometry, confidence, empty/error/partial-result
  분류와 대표 실패 사례를 같은 schema로 비교한다. PaddleOCR 결과가 없는 fixture를
  성공으로 간주하지 않는다.
- cold/warm startup, model load, p50/p95/p99 latency, throughput, RSS/peak memory,
  concurrency 및 timeout을 host architecture별로 기록한다. 단일 Mac 실행을
  production ranking으로 일반화하지 않는다.
- 결과 manifest와 raw report의 byte-level SHA-256, 실행 commit, JDK/runtime,
  model/container identity를 보존한다.

### 2. #545 service·공급망 증적

- trusted build에서 만든 digest-pinned CPU image와 pre-baked model directory를
  선택하고, model file/tree digest·revision·license·NOTICE를 manifest로 고정한다.
- clean/offline 환경에서 first-use network/download와 external egress가 차단된 상태로
  readiness, OCR smoke, request/response limit, timeout, cleanup을 실행한다.
- non-root/read-only/capability drop, loopback 또는 private bind, auth/TLS, no-log와
  sanitized error를 실제 container 설정·log receipt로 검증한다.
- SPDX SBOM, provenance attestation, SBOM attestation, license/NOTICE artifact를
  하나의 trusted build digest에 연결하고, signature/issuer/subject를 acceptance
  verifier가 별도로 검증한다. local rebuild digest를 서명된 subject로 대체하지 않는다.
- receipt의 immutable tuple과 실제 artifact byte hash를 재계산하고, 실패한 native/
  container tier가 기존 Tesseract PR baseline을 차단하지 않는지 CI 결과를 남긴다.

### 3. #546 API 및 구현 경계

- provider-neutral request/result/geometry/error/cancellation/limits 계약과 legacy
  caller migration/rollback/observability fixture를 구현 계획의 입력으로 재검토한다.
- provider-specific path, URL, credential, Tess4J/Paddle type, raw `Throwable`가
  common API나 public serialization schema로 새지 않는지 확인한다.
- HTTP wire는 schema version, unknown/duplicate field, size/depth/trailing token,
  sensitive log를 fail-closed로 검증한다. 기본 codec은 Jackson 3
  implementation-only이며 public signature와 generated POM에 mapper를 노출하지
  않는다.
- 이 조건을 만족해도 #547의 새 `ADOPT` 승인 전에는 production API/module PR을
  생성하지 않는다.

### 채택 판정 기준

`ADOPT`는 위 세 묶음의 증적이 모두 있고, 다음을 명시적으로 만족할 때만 가능하다.

1. mandatory corpus에서 Tesseract 대비 품질 회귀가 없고, 도입을 정당화할 명확한
   품질 또는 운영상 개선이 사전에 합의한 기준으로 재현된다.
2. CPU 환경의 cold/warm, p95, throughput, RSS, startup, concurrency 결과가
   운영 예산 안에 있고, 실패·timeout·cancellation이 stable reason으로 분류된다.
3. model/runtime/container provenance, license/NOTICE, SBOM/attestation, rollback
   receipt가 모두 검증되며 P0/P1 공급망·격리 결함이 없다.
4. provider-neutral API와 Jackson 3 private codec 경계가 유지되고, 기존 Tesseract
   호출자의 migration/rollback 경로가 별도 fixture로 통과한다.
5. 위 결과를 인용하는 별도 Type-A implementation issue/plan에 module graph,
   dependency/BOM, model manifest, CI tier, 운영·롤백 순서를 고정한다.

하나라도 만족하지 못하거나 결과를 재현할 수 없으면 결정은 `DEFER`로 유지한다.
trusted artifact를 확보하지 못했거나 acceptance verifier를 통과하지 못한 경우도
`DEFER`다. 모든 재평가 증적을 확보한 뒤에도 품질·운영 가치가 없다고 판정한
경우에만 별도 근거와 함께 `REJECT`로 전환할 수 있다.

### 재평가 trigger

다음 중 하나가 발생하면 새 receipt를 수집한 뒤 이 결정의 유효성을 다시 확인한다.

- trusted digest-pinned image와 pre-baked model receipt를 확보했다.
- #544의 동일 corpus 전체 비교와 #545 acceptance verifier가 각각 완료되었다.
- PaddleOCR/PaddleX 또는 model major version, serving protocol, license가 바뀌었다.
- 새로운 Tesseract baseline, 지원 language, target architecture 또는 운영 예산이
  바뀌어 현재 비교 기준을 더 이상 적용할 수 없다.

## 잔여 위험과 후속 작업

| 위험 | 현재 영향 | 후속 증적 |
|---|---|---|
| #544 multilingual direct latency의 오차가 큼 | 단일 host 결과로 안정된 성능 우위를 주장할 수 없다. | 반복 확대와 host matrix, 품질 metric을 포함한 새 raw run |
| #545 실제 image/model이 없음 | preflight 이후의 startup·OCR·SBOM/attestation이 비어 있다. | trusted build artifact와 offline service receipt |
| receipt artifact root의 ancestor TOCTOU 가능성 | 계약 validator가 운영 보안 승인을 대신하지 못한다. | directory-FD `O_NOFOLLOW` 순회 또는 immutable trusted root을 사용하는 acceptance verifier |
| self-hosted HTTP 운영 비용 | auth/TLS, version skew, payload/backpressure, process cleanup을 새로 운영해야 한다. | 실제 CI tier별 비용·실패·rollback report |
| Tesseract baseline 유지 | 기존 엔진의 언어별 품질·native dependency 한계를 계속 부담한다. | 동일 corpus의 지속 baseline과 재평가 기준 갱신 |

이번 문서는 위 위험을 해결했다고 주장하지 않는다. 다음 실행 slice는 먼저
trusted model/image provisioning과 #544/#545의 실제 receipt를 준비해야 한다.

## 결정 DoD

- [x] #543 정책, #544 baseline, #545 contract/preflight, #546 API plan의 exact
  issue·artifact·commit을 연결했다.
- [x] `ADOPT`·`DEFER`·`REJECT`를 비교하고 `DEFER` 근거와 trade-off를 기록했다.
- [x] 0.5.0 Tesseract baseline 유지 범위와 Paddle dependency/model/API 금지선을
  명시했다.
- [x] #544 품질·성능, #545 service·공급망, #546 API 구현 경계를 재평가 조건으로
  분해했다.
- [x] 실제 실행·SBOM·attestation을 수행했다고 과장하지 않았고, 잔여 위험과
  follow-up evidence를 명시했다.
- [x] 문서 본문과 링크를 read-back하고 한국어 기술문체·용어를 점검했다.

최종 상태: **`DEFER / Tesseract baseline 유지 / PaddleOCR Type-A 구현 미승인`**

### Superpowers writer gate

- [x] `SPW-01` 범위·독자·문서 유형을 결정 문서로 고정했다.
- [x] `SPW-02` #543–#546의 source-to-claim ledger와 게이트 표를 작성했다.
- [x] `SPW-03` 한국어 기술문체, 용어 일관성, 금지선 표현을 검토했다.
- [x] `SPW-04` exact commit·artifact·issue·수치·현재 상태를 원문과 대조했다.
- [x] `SPW-05` 최종 Markdown을 read-back하고 결정 DoD와 잔여 위험을 기록했다.
