# Issue #544 Train 3 Jackson3 provider 비교 receipt 7-Tier 검토

## 검토 상태

| 항목 | 값 |
|---|---|
| 대상 | Issue [#544](https://github.com/bluetape4k/bluetape4k-image/issues/544) benchmark contract train |
| 기준 base | `develop` @ `441900d82a166074e9635723678221a0480f79e4` |
| 정확한 head | `47659ba3f4b2f5cf4f0e84353933da4f6f9f6452` |
| PR train | PR [#605](https://github.com/bluetape4k/bluetape4k-image/pull/605) / Train 3-A |
| 변경 유형 | Type-B bounded benchmark tooling; production OCR API·Paddle runtime·model bytes 없음 |
| 독립 reviewer lane | **TIMEOUT/NO RESULT** — native interrupt 후 parent-linked inline fallback을 별도 기록 |

이번 검토는 provider-neutral receipt 계약, Jackson3 경계, corpus 정합성, fail-closed
검증과 PR readiness를 대상으로 한다. 현재 receipt는 실제 PaddleOCR 비교 결과가 아니며,
Tesseract 기준선은 `BASELINE_ONLY`로만 소비한다.

## 7-Tier 체크리스트

| Tier | 검토 범위 | 결과·근거 |
|---|---|---|
| 1. 범위·의존성 | #544 선후 관계, Type-B 경계, production mutation | **PASS** — `OcrProviderComparisonReceipt.kt`는 benchmark 내부 계약이며 production API·Paddle dependency·model/service를 추가하지 않는다. benchmark 모듈은 저장소 중앙 Jackson3 BOM/version을 재사용한다. |
| 2. corpus·provenance | v2 manifest, provider/runtime/model/image identity, output hash | **PASS** — bounded canonical v2 manifest SHA-256을 계산해 고정하고 provider별 SHA를 top-level과 대조한다. receipt의 `scenario`는 manifest wire 값(`low-resolution`)으로 exact-match하며, `COMPARABLE`은 model/image를 `sha256:<64 hex>`로 요구한다. |
| 3. 결과 계약 | TEXT/EMPTY/ERROR, text·geometry·empty/error payload | **PASS** — fixture 순서·scenario·expected outcome을 manifest와 exact-match하고 actual outcome별 text/geometry/error 일관성을 검증한다. geometry box ID/order, 좌표·confidence·이미지 경계를 확인한다. |
| 4. 품질·성능 | CER/WER summary, cold/warm/RSS/throughput | **PASS (계약)** / **PENDING (실행)** — row별 cold/warm latency·최소 warm 3회·throughput·RSS를 양수 및 bounded 값으로 검증하고, `COMPARABLE` summary의 CER/WER를 0..1로 제한한다. 실제 Paddle 동일 corpus 수치는 아직 없다. |
| 5. 보안·공급망 | JSON 입력, digest pinning, #545 artifact gate | **PASS (계약)** / **PENDING (runtime)** — Jackson3 strict unknown-field, 512 KiB document, 64 KiB string, nesting/token/name bounds와 trailing-data 거부를 적용했다. #545의 trusted image/model/SBOM/attestation이 없으면 COMPARABLE 실행을 승인하지 않는다. |
| 6. API·호환성 | public API/ABI, serialization 경계, Kotlin pattern | **PASS** — 모든 타입은 benchmark 내부이며 production OCR API를 변경하지 않는다. 새 JSON 경로는 `tools.jackson` Jackson3를 사용하고 기존 `kotlinx.serialization` corpus/protocol은 그대로 둔다. |
| 7. writer·CI·release | 한국어 문서, metadata, CI, merge boundary | **CONDITIONAL** — review/lesson은 한국어 SPW 구조로 기록하고 issue assignee·milestone·labels를 PR에 미러링했다. 독립 lane은 timeout/NO RESULT이며 inline exact-head review는 P0-P3=0으로 별도 기록했다. 최종 hosted CI·metadata read-back 전에는 merge-ready로 승격하지 않는다. |

## 독립 reviewer lifecycle

독립 reviewer는 정확한 초기 code head `8a92f238ffd3b829fac276d93e83a1c932185f8c`를
read-only로 검토하도록 할당했다. 검토 범위는 다음과 같다.

- Jackson3 strict mapper와 bounded input이 실제로 fail-closed인지 확인한다.
- corpus manifest/fixture/geometry/metric contract가 provider 비교를 과장하지 않는지 확인한다.
- `$bluetape-kotlin-patterns`의 불변성·명시적 validation·Kotlin test 관례와 Type-B 경계를 확인한다.
- P0/P1/P2/P3 finding을 파일·심볼·근거와 함께 반환한다.

06:36:20에 `stall_suspected`, 06:36:25에 bounded probe를 receipt에 기록했지만 fresh
reviewer 결과가 없었다. probe deadline 이후 06:44:15에 native `interrupt_agent`로 lane을
회수하고, 06:44:30에 `independent-review`의 checkpoint를 parent로 하는 `inline-review`
replacement를 생성했다. 06:44:45 시작, 06:44:50 startup ACK 후 최종 code head
`47659ba3f4b2f5cf4f0e84353933da4f6f9f6452`를 main session에서 read-only로 검토했다.
이 inline 결과는 독립 검토의 대체 증거이며 독립 PASS로 표현하지 않는다.

### Inline reviewer result

| Severity | 결과 | 근거 |
|---|---:|---|
| P0 | 0 | production API·Paddle runtime·credential 경계 침범 없음 |
| P1 | 0 | Jackson3 strict/bounded parser, canonical manifest/provenance, payload invariant가 exact head에서 fail-closed |
| P2 | 0 | scenario wire 값·최소 warm 3회·manifest bound 보강 후 residual finding 없음 |
| P3 | 0 | Kotlin 불변 data class, 명시적 `require`, JUnit 5 regression evidence 확인 |

초기 preflight에서 발견한 enum 기본 직렬화(`LOW_RESOLUTION`)와 `warmIterations > 0`
완화 부족은 correction commit `47659ba3`에서 수정하고, 최종 inline pass에서 재검증했다.

## 잔여 위험과 disposition

- 실제 PaddleOCR service/container, model/image digest, SBOM/attestation, legal receipt는
  #545에서 먼저 완료해야 한다.
- 같은 v2 corpus에 대한 Paddle 품질·geometry·empty/error·cold/warm/RSS/throughput
  결과가 없으므로 Issue #544와 #169는 닫지 않는다.
- `BASELINE_ONLY`는 Tesseract 기준선만 의미하며 adoption·provider ranking·production
  SLO를 의미하지 않는다.
- 기준선 전체 benchmark test는 180초 제한에서 완료되지 않았으므로 targeted contract
  test와 compile 결과만 현재 evidence로 인정한다.

## 검증 증거

- `./gradlew :bluetape4k-images-benchmark:test --tests io.bluetape4k.images.benchmark.OcrProviderComparisonReceiptTest --no-daemon --no-configuration-cache` — exact head `47659ba3`에서 4 passing
- `./gradlew detekt --no-daemon --no-configuration-cache` — `NO-SOURCE`, 성공
- `git diff --check` — 통과
- live issue #544 — OPEN, assignee `debop`, milestone `1.0.0`, labels `documentation`, `test`
- live PR #605 — base `develop`, head `47659ba3f4b2f5cf4f0e84353933da4f6f9f6452`, correction push 후 hosted CI fresh run read-back pending

## Writer DoD

- `SPW-01`: PASS — 대상·독자·Type-B 범위·독립 reviewer stop rule을 고정했다.
- `SPW-02`: PASS — 7-Tier evidence, PENDING gate, risk와 disposition을 분리했다.
- `SPW-03`: PASS — 한국어 기술 문체를 사용하고 API·command·URL·SHA token을 보존했다.
- `SPW-04`: PASS — #544/#545/#547 선후 관계와 source/commit/PR ledger를 연결했다.
- `SPW-05`: CONDITIONAL — 독립 reviewer는 TIMEOUT/NO RESULT로 보존했고 inline 대체 검토는 완료했으며, hosted CI fresh 결과와 최종 read-back이 남아 있다.

## Final Status

`CONDITIONAL / CONTRACT GREEN / PADDLE COMPARISON PENDING` — 이 문서는 receipt
계약의 merge readiness만 추적하며, 실제 provider adoption이나 merge 승인을 의미하지 않는다.
