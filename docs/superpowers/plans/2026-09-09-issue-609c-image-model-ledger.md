# Issue #609-C image·model ledger 실행 계획

## 계획 메타데이터

| 항목 | 값 |
|---|---|
| 대상 | [#609 trusted artifact producer·SBOM·attestation 재개 gate](https://github.com/bluetape4k/bluetape4k-image/issues/609)의 `#609-C` |
| 선행 | [#611 producer 선택](https://github.com/bluetape4k/bluetape4k-image/issues/611), [#638 trusted producer](https://github.com/bluetape4k/bluetape4k-image/issues/638) |
| 실행 유형 | Type-E 유지보수·문서·검증 |
| milestone | `1.1.0` |
| 기준 head | `develop` @ `367275a58681e7a7f04fc5f078d70e694a13951a` |
| 실행 branch | `feat/issue-609c-image-model-ledger` |
| 중간 산출물 | `IMAGE_MODEL_LEDGER`, strict JSON Schema, stdlib-only Python validator, Korean plan/research/lesson |
| 저장 경계 | 모델 원본 bytes와 외부 evidence 디렉터리는 저장소에 추가하지 않고 digest·파일 manifest만 기록 |

## 목표와 비목표

목표는 #638이 확정한 동일한 PaddleOCR image/model bytes를 다시 읽어 image
identity, package lock, model tree/file hash, license/NOTICE, producer trust claim으로
구성한 `IMAGE_MODEL_LEDGER`를 만드는 것이다. ledger 자체는 JCS checksum으로 묶고,
검증기는 선언된 값과 staged bytes가 다르면 실패한다.

이번 단계에서는 다음을 실행하지 않는다.

- SPDX SBOM·provenance·signature의 same-subject 판정(`#609-D`)
- 실제 `network=none` 실행, egress 차단, resource limit, 로그 redaction, cleanup(`#609-E`)
- #544 corpus 성능·정확도 비교와 benchmark
- #547 adoption 결정, #169 production API 구현
- model bytes, registry push, workflow dispatch, mutable tag pull

## 작업 순서와 종료 조건

| 순서 | 작업 | 산출물 | 종료 조건 |
|---:|---|---|---|
| 1 | #638 최종 evidence와 #611 producer claim을 live/read-only로 재확인 | 입력 root·run identity·policy SHA 기록 | image/platform/config/base와 input lock의 출처가 하나의 attempt로 결합됨 |
| 2 | fixture를 먼저 만들고 tamper·symlink·unknown-key negative test를 RED/GREEN으로 고정 | `test_paddle_ocr_ledger.py` | 정상 build/validate 1건과 fail-closed negative 6건 이상 |
| 3 | strict parser, safe path, regular-file read, model tree/pair hash, legal/trust mapping 구현 | `paddle_ocr_ledger.py` | 선언값을 실제 bytes에서 재계산하고 unknown/malformed input을 거부 |
| 4 | stage-specific schema와 실제 #638 evidence ledger 생성 | `artifact-ledger.schema.json`, ledger JSON | schema shape, checksum, 모든 #609-C verification flag가 PASS |
| 5 | Korean 문서와 SPW writer pass 수행 | research/plan/lesson 3개 | 사실·범위·후속 gate·재현 명령이 서로 일치 |
| 6 | targeted Python tests, schema/compile, existing producer regression, `git diff --check` 실행 | test receipts | 실패 0건, 외부 egress/performance는 unchecked로 기록 |
| 7 | branch push, PR과 #609 comment 연결 | PR, issue readback | PR은 `Refs #609`, milestone/assignee/labels가 live metadata와 일치; parent issue는 닫지 않음 |

## ledger 계약

최상위 key는 `schemaVersion`, `kind`, `status`, `scope`, `producer`, `image`, `model`,
`licenseNotice`, `trustPolicy`, `evidence`, `verification`, `checksum`으로 고정한다.
`scope.deferred`에는 `609-D`, `609-E`, `544-B`, `547`을 그대로 남긴다.

모델 tree는 role별 canonical sorted manifest의
`path\tbytes\tsha256\n` UTF-8 bytes를 SHA-256으로 해시한다. pair binding은 다음
문자열을 같은 방식으로 해시하며 role 순서를 바꾸지 않는다.

```text
detector
<detector tree SHA-256>
recognizer
<recognizer tree SHA-256>
```

ledger checksum은 `checksum`을 제거한 object를 JCS로 직렬화한 SHA-256이다. 입력
evidence JSON은 기존 producer가 만든 pretty JSON을 strict parser로 읽되, ledger와
trust policy 출력은 canonical JCS 형식을 유지한다.

## 검증 명령

```bash
python3.13 scripts/research/test_paddle_ocr_ledger.py
python3.13 -m py_compile scripts/research/paddle_ocr_ledger.py scripts/research/test_paddle_ocr_ledger.py
python3.13 -m json.tool docker/paddleocr/artifact-ledger.schema.json >/dev/null
python3.13 scripts/research/paddle_ocr_ledger.py validate \
  --evidence-root <issue-638-evidence> \
  --model-root <issue-638-accepted-image> \
  --legal-root docker/paddleocr \
  --policy docker/paddleocr/trust-policy.json \
  --ledger docs/superpowers/research/2026-09-09-issue-609c-image-model-ledger.json
git diff --check
```

## 실패 처리와 후속 연결

- evidence descriptor, platform manifest, package lock, model file/tree, legal file,
  trust membership 중 하나라도 불일치하면 ledger를 `PASS`로 만들지 않는다.
- 모델 파일은 저장소에 복사하지 않는다. 후속 검증자는 #638에서 승인한 staged model
  root를 별도로 준비하고 ledger의 six file descriptors와 tree hash를 재검증해야 한다.
- #609-D는 같은 image platform digest를 SBOM·provenance·signature subject로 사용하고,
  #609-E는 이 ledger의 image/model identity를 실행 receipt에 결합한다.
- no-egress와 성능은 아직 측정하지 않았으므로 해당 결과를 이 단계의 PASS로 승격하지
  않는다.

## SPW-01~05 DoD

| 항목 | 결과 | 근거 |
|---|---|---|
| SPW-01 대상·승인·경계 | PASS | 사용자 요청, #611 선행, #609-C와 후속 gate의 분리를 계획 메타데이터에 고정 |
| SPW-02 plan 계약 | PASS | 순서, 산출물, 종료 조건, 실패 처리, 재현 명령을 표와 본문으로 정의 |
| SPW-03 한국어 technical register | PASS | 상태·명령·digest·API key는 원문 token을 보존하고 설명은 자연스러운 한국어로 작성 |
| SPW-04 source·readback | PASS | #638 final evidence, #611 producer claim, local validator output을 research 문서에 연결 |
| SPW-05 최종 readback | PASS | 실제 checksum·tree·policy 값과 deferred 범위를 검증 후 기록 |
