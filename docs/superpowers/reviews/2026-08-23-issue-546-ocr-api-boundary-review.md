# Issue #546 provider-neutral OCR API 경계 계획 독립 검토

## 검토 상태

| 항목 | 값 |
|---|---|
| 대상 | `docs/superpowers/plans/2026-08-23-issue-546-ocr-api-boundary.md` |
| 범위 | #546 Type-E 설계 문서와 후속 Type-A stacked train 경계 |
| 기준 base | `docs/issue-544-benchmark` (`32c8f09abf08d36885e07436ba5b8acb04c71616`) |
| 독립 reviewer 결과 | 초기 `REQUEST CHANGES` — P0 0건, P1 8건, P2 4건 |
| 수정 후 상태 | 모든 P1/P2 disposition 반영, 문서 재검증 PASS |
| production 변경 | 없음. Kotlin source, dependency, model, container를 변경하지 않음 |

## 초기 reviewer 지적과 disposition

| 우선순위 | 지적 | 반영 내용 | 상태 |
|---|---|---|---|
| P1 | #547 adoption gate보다 public API 구현이 앞섬 | train을 `#547 decision → Type-A API → Tesseract → benchmark` 순서로 재배치하고, 결정 전 public implementation PR 금지를 명시 | 해결 |
| P1 | `TiffMultiPageOcr` migration/fixture 누락 | limits, failure reason/exception, blocking/suspend, page preflight/order, partial result 금지, cleanup과 broad-catch mapping을 matrix·fixture·ledger에 추가 | 해결 |
| P1 | `ImmutableImage`에서 encoded `OcrImage`로의 변환 계약 부재 | legacy direct decoded seam, HTTP deterministic lossless PNG policy, media type/alpha/orientation/metadata/multipage/decode owner와 round-trip fixture를 추가 | 해결 |
| P1 | limit 책임과 적용 순서 불명확 | encoded bytes → decode metadata → pixels/pages/total pixels → result size → concurrency/deadline의 순서와 edge/decoder/coordinator/provider 책임을 고정 | 해결 |
| P1 | WebFlux 계약 누락 | 현재 WebFlux surface가 없음을 `rg` 근거로 N/A 처리하고, future `Mono`/suspend·bounded scheduler·backpressure·cancellation·timeout·response mapping을 별도 Type-A 범위로 고정 | 해결 |
| P1 | benchmark caller 누락 | `TesseractOcrExtractionBenchmark`와 fixture manifest를 migration matrix, train, scheduled/no-network evidence에 연결 | 해결 |
| P1 | Tesseract broad catch가 cancellation을 감쌀 수 있음 | 현재 `catch (RuntimeException)` 사실을 기록하고, 후속 adapter에서 `CancellationException`을 broad catch보다 먼저 재전파하는 fixture를 명시 | 해결 |
| P1 | Jackson 3 기본 선택 누락 | Jackson 3 implementation-only private codec, 중앙 version alias, bounded/fail-closed JSON, canonical fixture와 no-default-typing을 고정 | 해결 |
| P2 | DTO 불변성/직렬화 세부 부족 | `ByteArray` 방어 복사, immutable 기준 데이터, `copy()`/Java fixture, legacy `Serializable`과 JSON-only DTO 분리를 명시 | 해결 |
| P2 | source ledger 경로 생략 | 모든 local source claim을 repository-relative exact path로 교체하고 존재 검사를 수행 | 해결 |
| P2 | region 의미론 불명확 | 독립 extraction window, request/page/region reading order, overlap/empty/geometry-null과 legacy Tesseract 차이를 fixture로 고정 | 해결 |
| P2 | HTTP schema/observability 추상적 | private HTTP v1 envelope, field/encoding/error 정책, metric 이름·저 cardinality label·request ID·duration/queue/retry/cancel을 고정 | 해결 |

## 검증 증거

- `git diff --check` — PASS
- `audit-korean-terms.mjs` — PASS, `findings=0`
- Markdown fenced block count — 8, balanced
- source-to-claim path existence check — missing 0
- 독립 reviewer 초기 판정 — P0 0건 및 위 P1/P2 목록 확인
- 후속 구현·호스트 Tesseract·Paddle service·benchmark 실행 — 이 문서 범위 밖이며 미실행

## 잔여 gate

1. #544 실제 비교 실행과 #545 model/container/offline/security receipt가 아직 없다.
2. #547이 `ADOPT`/`DEFER`/`REJECT`를 결정하기 전에는 public API/provider/benchmark
   implementation PR을 만들지 않는다.
3. 이 문서는 Type-E 설계 artifact이므로 현재 PR은 production behavior나 dependency
   graph를 바꾸지 않는다.

## 판정

`PASS — 설계 문서 gate 충족 / 구현·Paddle 채택 gate 미완료`

이번 검토는 계획 문서가 후속 구현을 시작할 수 있는 경계·증거 요구사항을 충분히
명시했는지를 판단한다. 실제 provider 품질 우위, service 운영 readiness, 또는 #547
채택 결정을 대신하지 않는다.

## 2026-08-23 누적 head 보강 검토

대상은 PR #553의 누적 head `13ddbb6b7e8a71f3b10f692634fcd48ea4cc29d6`이다. 이 head는
#554와 #558 산출물을 포함한 4개 문서 파일을 누적하며, 다음 보강을 반영한다.

| 우선순위 | 관찰 | 보강 내용 | 상태 |
|---|---|---|---|
| P1 | 기존 계획 예시는 `data class OcrImage(ByteArray)`였지만 생성된 `copy()`와 getter가 배열 alias를 허용할 수 있음 | `OcrImage`를 non-data/private backing value object로 바꾸고 `of`·`bytes()` 방어 복사, content equality/hash, `ocr-image-aliasing-v1` 회귀 fixture를 명시 | 해결 |
| P2 | WebFlux 검색 주장이 계획·review 문서까지 포함한 repository-wide 결과와 충돌함 | production source/config 경로만 검색하는 명령으로 ledger 범위를 명확히 하고 docs/review artifact 언급을 제외 | 해결 |
| P1 | live PR body가 #544 단일 파일만 설명하고 누적 #554/#558 topology와 DoD를 반영하지 못함 | PR body를 4개 누적 문서, exact head, 후속 gate, 독립 재검토 결과 기준으로 갱신 | 진행 |

보강 후에도 이 문서는 production Kotlin source, dependency, model, container를 변경하지
않는다. 따라서 `#544` 실제 benchmark, `#545` service/model receipt, `#547` adoption
decision은 여전히 후속 gate이며, 현재 PR의 hosted CI가 없다는 사실도 성공으로 해석하지
않는다.

## 누적 보강 후 판정

`PASS — 설계 문서 보강 완료 / PR body 누적 범위와 exact head 재검토 대기`

계획 문서의 aliasing·source ledger P1/P2는 해결되었다. PR #553 본문은 누적 4개 문서와
새 exact head를 반영한 뒤 독립 reviewer가 다시 확인해야 하며, 그 전에는 merge하지 않는다.
