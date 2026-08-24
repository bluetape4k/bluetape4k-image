# Issue #544 OCR corpus v2 baseline 정합화

| 항목 | 내용 |
|---|---|
| Issue | [#544](https://github.com/bluetape4k/bluetape4k-image/issues/544) |
| Parent | [#169](https://github.com/bluetape4k/bluetape4k-image/issues/169) |
| Main epic | [#513](https://github.com/bluetape4k/bluetape4k-image/issues/513) |
| Train | `RESEARCH-1 / Train 1` |
| 문서 유형 | Type-E 연구 정합화 |
| 기준 ref | `develop` @ `c737ed38ac184b1922590ab256c484030f38a9cd` |
| 조사일 | 2026-08-25 |
| 판정 | `BASELINE_ONLY` / PaddleOCR 비교 `PENDING` / 채택 `DEFER` |

## 목적

기존 Issue #544 receipt와 현재 canonical OCR corpus v2 evidence의 관계를 고정한다.
이전 receipt가 사용한 v1 manifest를 현재 비교 입력으로 잘못 재사용하지 않도록
manifest 버전·SHA-256·실행 artifact를 하나의 추적성 표로 묶는다.

이 문서는 PaddleOCR 실행 결과나 채택 근거가 아니다. Paddle dependency, model
binary, 자동 다운로드, service/container, production adapter와 public API는 이 train의
범위에 없다.

## 정합화 결과

| 구분 | 이전 #544 receipt | 현재 canonical evidence | 해석 |
|---|---|---|---|
| 입력 manifest | `bench/ocr/manifest.json` v1, receipt SHA `eeae6d9dc34fa8281befad9b288196a4fac955ca0b25bda77102b5b1b6079bb0` | `bench/ocr-v2/manifest.json` v2, SHA `99502a59751f68aff19634c33239d0f0e50931a17746621e7384ef169faaebb6` | v1 raw 수치는 v2 비교 입력으로 승격하지 않는다. |
| Tesseract baseline | 4개 v1 scenario의 token smoke 및 JMH latency/throughput | v2 baseline subset receipt와 full-corpus protocol receipt | v2 결과만 현재 #544 기준선으로 참조한다. |
| 품질 metric | CER/WER·geometry 비교 없음 | 21개 `TEXT` fixture의 Tesseract CER/WER, 3개 `EMPTY`, 3개 malformed negative | Tesseract 내부 baseline은 측정됐지만 PaddleOCR와의 차이는 아직 없다. |
| 자원 metric | native RSS·cold start 미측정 | Tesseract cold/warm latency·throughput·RSS receipt | provider 간 비교가 아니므로 adoption 근거가 아니다. |
| 최종 판단 | `PENDING` | `PaddleOCR comparison = PENDING`, `adoption = DEFER` | #545 service/security receipt와 같은 입력의 PaddleOCR 결과가 필요하다. |

## 현재 기준 artifact ledger

모든 경로는 `benchmark/images-benchmark` 기준으로 읽는다. manifest를 참조하는
artifact의 SHA-256은 서로 일치해야 하며, receipt가 가리키는 raw 파일을 수동으로
재작성하지 않는다.

| Artifact | 역할 | SHA-256 또는 판정 |
|---|---|---|
| [`bench/ocr-v2/manifest.json`](../../../benchmark/images-benchmark/src/main/resources/bench/ocr-v2/manifest.json) | v2 fixture·ground truth·geometry·negative 입력 계약 | `99502a59751f68aff19634c33239d0f0e50931a17746621e7384ef169faaebb6` |
| [`issue-565...v2-corpus/run-manifest.json`](../../../benchmark/images-benchmark/docs/raw/issue-565-20260824-macos-arm64-java25-v2-corpus/run-manifest.json) | 1개 baseline subset의 host/model/JMH receipt | manifest SHA와 `BASELINE_ONLY`를 함께 기록 |
| [`issue-565...v2-protocol/ocr-v2-protocol.json`](../../../benchmark/images-benchmark/docs/raw/issue-565-20260824-macos-arm64-java25-v2-protocol/ocr-v2-protocol.json) | 24개 fixture의 Tesseract cold/warm/RSS와 CER/WER | `592806fcf4488dfd54683980c0a7959440dac37a672392de8116393a93b7043b` |
| [`issue-565...v2-protocol/run-manifest.json`](../../../benchmark/images-benchmark/docs/raw/issue-565-20260824-macos-arm64-java25-v2-protocol/run-manifest.json) | protocol 실행 환경·row/negative 수·artifact hash | `ab1f1f27aac81656266be11044d7775f15ae335999f87b545031ff8d63c865fe` |
| [`issue-565...v2-protocol/model-provenance.json`](../../../benchmark/images-benchmark/docs/raw/issue-565-20260824-macos-arm64-java25-v2-protocol/model-provenance.json) | Tesseract `eng`/`kor`/`jpn` model bytes·SHA-256 | `53c4afb40b8d47c61b136e33eedfa78b44e3a9b224722610c13e5d8b33f9158f` |

Protocol receipt의 측정 범위는 24개 fixture(`TEXT` 21개, `EMPTY` 3개)와
malformed negative 3개다. Tesseract aggregate는 `CER=0.04604051565377532`,
`WER=0.09897610921501707`이며, 이는 Mac arm64 한 대의 baseline 관찰값이다.
다른 provider나 host의 순위, production SLO, PaddleOCR 채택을 의미하지 않는다.

## Issue #544 완료 조건 재판정

| 완료 조건 | 상태 | 근거 또는 다음 gate |
|---|---|---|
| corpus provenance·license·hash·정답 고정 | `PASS` | v2 manifest와 fixture/resource/ground-truth SHA 검증 |
| 최소 3회 반복·warm-up·artifact 형식 | `PASS` | protocol receipt의 cold 1회, warm-up 2회, warm 3회, 24 row |
| Tesseract baseline 재현 | `PASS` | `validateOcrBenchmarkReceipt`와 `validateOcrProtocolReceipt` |
| PaddleOCR 동일 corpus 비교 | `PENDING` | #545의 pinned service/offline/security receipt 후 실행 |
| CER/WER·geometry·empty/error provider 비교 | `PENDING` | provider-neutral result artifact와 동일 manifest 필요 |
| RSS·cold/warm·concurrency provider 비교 | `PENDING` | 같은 host envelope와 bounded resource profile 필요 |
| 최종 채택 판단 | `DEFER` | #547 결정 유지; ADOPT 전 Type-A 구현 금지 |

`PASS`는 Tesseract 기준선이 충족됐다는 뜻이지, #544 전체가 닫혔다는 뜻이 아니다.
비교·운영·공급망 gate 중 하나라도 남아 있으면 Issue #544와 Parent #169는 열린
상태로 유지한다.

## 재현 명령

다음 두 validator를 같은 checkout에서 순서대로 실행한다. 첫 번째는 v2 corpus와
baseline raw receipt의 manifest/hash chain을 검증하고, 두 번째는 full-corpus
protocol의 row 순서·negative 분리·CER/WER·RSS/cold/warm envelope를 검증한다.

```bash
./gradlew :bluetape4k-images-benchmark:validateOcrBenchmarkReceipt --console=plain
./gradlew :bluetape4k-images-benchmark:validateOcrProtocolReceipt --console=plain
```

validator가 확인하는 현재 manifest SHA는 다음과 같다.

```text
99502a59751f68aff19634c33239d0f0e50931a17746621e7384ef169faaebb6
```

`generate_ocr_v2_fixtures.rb`의 `replayStatus=PENDING`은 역사적
`clean-text-v2-001`을 보존하기 때문에 generator 전체를 byte 단위로 재생성하지
못한다는 뜻이다. 따라서 generator를 재실행해 현재 fixture를 덮어쓰거나, replay를
`PASS`로 바꾸지 않는다. 이 상태는 corpus provenance/hash receipt와 별도의 후속
보강 과제로 남긴다.

## 다음 gate와 금지선

- #545에서 offline/no-egress model, service/container digest, SBOM/attestation,
  security negative, CI tier를 고정한다.
- 그 결과가 있어도 PaddleOCR는 동일 v2 manifest로 CER/WER, geometry, empty/error,
  cold/warm latency, RSS, bounded concurrency를 별도 실행해야 한다.
- 비교 결과가 없거나 재현되지 않으면 `DEFER`를 유지한다.
- Paddle dependency, model auto-download, Python embedding, subprocess adapter,
  HTTP production adapter와 provider-neutral public API 변경은 Type-A 승인 전까지
  금지한다.

## Writer DoD

- `SPW-01`: `PASS` — Type-E 정합화 문서의 독자·목적·source ledger·미확인 claim을
  고정했다.
- `SPW-02`: `PASS` — 이전/current artifact, 판정, 완료 조건, 재현 명령, 다음 gate와
  금지선을 포함했다.
- `SPW-03`: `PASS` — 한국어 기술 문체를 적용하고 SHA-256, 경로, 명령, issue/PR
  식별자를 보존했다.
- `SPW-04`: `PASS` — `develop` source, v2 manifest, #565 receipts, #544 완료 조건을
  대조해 v1 수치의 승격을 차단했다.
- `SPW-05`: `PASS` — 최종 Markdown을 다시 읽어 표·링크·코드 블록·불확실한
  `PENDING` 경계를 확인했다.

## Final Status

`BASELINE_ONLY` — #544의 canonical v2 입력과 Tesseract baseline receipt 정합화는
완료했지만, PaddleOCR 동일 corpus 비교·service/security receipt·최종 adoption gate는
아직 완료하지 않았다.
