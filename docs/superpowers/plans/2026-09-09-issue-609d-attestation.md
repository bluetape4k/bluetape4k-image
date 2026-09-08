# #609-D attestation same-subject 검증 계획

## 계획 메타데이터

| 항목 | 값 |
|---|---|
| 대상 | [#609 trusted artifact producer·SBOM·attestation 재개 gate](https://github.com/bluetape4k/bluetape4k-image/issues/609)의 `#609-D` |
| 선행 | [#609-C PR #669](https://github.com/bluetape4k/bluetape4k-image/pull/669), [#638 trusted producer](https://github.com/bluetape4k/bluetape4k-image/issues/638) |
| 실행 유형 | Type-E 유지보수·문서·검증 |
| milestone | `1.1.0` |
| 실행 branch | `feat/issue-609d-attestation` |
| 입력 | #638 final evidence, #609-C `IMAGE_MODEL_LEDGER`, `gh attestation verify` |
| 산출물 | strict attestation validator, JSON Schema, same-subject receipt, Korean plan/research/lesson |

## 목표와 비목표

목표는 하나의 실행 platform digest를 가리키는 provenance와 SPDX SBOM을 독립적으로
`gh attestation verify`로 검증하고, 검증 결과의 인증서 identity·투명성 로그 timestamp·
DSSE payload를 로컬 bundle과 다시 대조하는 것이다. 검증기는 `gh`가 수행한 암호 검증을
대체하지 않고, `gh`의 JSON read-back이 다른 subject나 signer를 성공으로 보고하지 않도록
fail-closed 경계를 추가한다.

이번 단계에서는 다음을 실행하지 않는다.

- `network=none` PaddleOCR service 실행과 no-egress/runtime receipt (`#609-E`)
- 동일 corpus 품질·성능 비교 (`#544-B`)
- `ADOPT`/`DEFER` 재결정 (`#547`) 또는 Kotlin production API (`#169`)
- registry push, workflow dispatch, credential 저장, model bytes 추가

## 작업 순서와 종료 조건

| 순서 | 작업 | 산출물 | 종료 조건 |
|---:|---|---|---|
| 1 | #638 evidence와 #609-C ledger의 subject/run/policy를 재확인 | 입력 tuple | platform digest·run attempt·workflow SHA가 한 tuple로 고정됨 |
| 2 | synthetic `gh` JSON과 DSSE bundle fixture를 먼저 작성 | `test_paddle_ocr_attestation.py` | 정상 1건과 multiple/subject/signer/timestamp/payload/unknown negative가 RED로 고정됨 |
| 3 | bounded parser와 certificate/statement/bundle binding 구현 | `paddle_ocr_attestation.py` | unknown key, malformed JSON, symlink/size 초과, subject·identity drift를 거부 |
| 4 | receipt schema와 실제 evidence read-back 실행 | `attestation-receipt.schema.json`, receipt JSON | provenance·SBOM 2개가 같은 subject/producer identity와 검증 timestamp를 가짐 |
| 5 | 문서와 GitHub metadata 연결 | research/lesson, PR, #609 comment | 실제 digest·bundle SHA·ledger SHA·deferred 범위를 read-back함 |

## 검증 계약

`gh attestation verify --format json` 결과는 다음 값을 모두 기대값과 일치시킨다.

- 결과 배열은 정확히 1개이고, predicate는 각각 SLSA provenance와 SPDX 2.3이다.
- statement subject name/digest는 `ghcr.io/bluetape4k/paddleocr-service@sha256:…`의
  동일 platform digest이며 DSSE payload를 local bundle과 비교한다.
- certificate의 repository, workflow, workflow ref, source/build digest, source ref,
  signer URI, OIDC issuer, `github-hosted` runner, workflow dispatch invocation이
  #638 run `34258892748.1`과 일치한다.
- `verifiedTimestamps`에는 하나 이상의 `Tlog` HTTPS timestamp가 있어야 한다.
- local bundle은 하나의 strict JSONL envelope, 하나의 DSSE signature, 유효한 in-toto
  statement를 가져야 하며 bundle·payload·signature SHA-256을 receipt에 남긴다.
- #609-C ledger checksum, evidence manifest descriptor, platform manifest bytes를 다시
  확인한 뒤에만 receipt를 `PASS`로 만든다.

`ATTESTATION_RECEIPT`의 checksum은 `checksum`을 제외한 JCS object의 SHA-256이다.
Receipt의 `scope.deferred`는 `609-E`, `544-B`, `547`을 유지하며 부모 #609 전체를
완료로 승격하지 않는다.

## 실패 처리와 잔여 위험

`gh`가 실패하거나 JSON이 바뀌거나 subject·certificate·timestamp·DSSE payload가
불일치하면 receipt를 쓰지 않고 `REJECTED`로 끝낸다. `gh attestation verify`의 암호학적
검증은 외부 도구의 권위로 남기며, Python validator는 서명을 직접 재검증하지 않는다.
실제 receipt가 있어도 no-egress 실행과 성능 비교가 없으므로 PaddleOCR 채택 근거가 아니다.

## DoD Status

- [x] #638 final evidence와 #609-C ledger의 동일 platform subject/run tuple을 고정했다.
- [x] `gh` JSON 결과와 local DSSE bundle을 묶는 strict validator 및 negative test를 추가했다.
- [x] provenance·SBOM same-subject receipt와 JSON Schema를 생성했다.
- [x] 실제 `gh attestation verify` 2회와 receipt checksum을 재현했다.
- [ ] #609-E no-egress smoke
- [ ] #544-B 비교와 #547 decision

최종 상태: `609-D PASS / 609-E·544-B·547 DEFERRED`
