# #609 trusted artifact producer lesson

## 배경

#609는 PaddleOCR 실행을 막는 단순한 image availability 문제가 아니라, producer가
만든 정확한 bytes와 그 공급망 증거를 같은 실행 subject에 연결하는 gate다. 공식
serving 문서, Docker tag, model card, registry 응답을 차례로 읽었지만, 어느 하나도
그 자체로 trusted artifact를 만들지 않았다.

## 핵심 교훈

1. **공식 serving 경로와 trusted producer를 분리한다.** PaddleOCR는 PaddleX Basic
   Serving과 Triton 경로를 안내하지만, 문서는 builder·image digest·SBOM·provenance·
   signer를 제공하지 않는다. “공식 문서에 있다”는 “검증된 실행 bytes다”가 아니다.
2. **tag와 digest를 구분한다.** tag는 이동할 수 있다. index digest, 플랫폼 manifest
   digest, config digest를 모두 기록하고 실제 실행한 platform subject를 receipt에
   연결해야 한다.
3. **multi-platform은 identity가 아니라 선택지다.** index 하나를 기록해도 arm64와
   amd64 manifest의 bytes가 같다는 뜻은 아니다. 플랫폼별 manifest와 attestation을
   분리하고, emulation 결과를 native 지원·성능 증거로 쓰지 않는다.
4. **model metadata와 model bytes를 분리한다.** model card의 revision/license는
   후보 identity다. 전체 모델 파일 묶음의 file/tree SHA-256, license 원문, NOTICE와
   detector·recognizer pair가 없으면 offline 실행 입력으로 승격하지 않는다.
5. **SBOM/provenance는 subject에 귀속된다.** SPDX SBOM 또는 in-toto/SLSA 문서가
   존재하는 것만으로 충분하지 않다. SBOM·provenance·서명의 subject digest가 실제
   실행한 platform manifest digest와 모두 같아야 한다.
6. **권한 실패는 부재와 다르다.** registry `401`은 trusted manifest를 현재 권한으로
   읽지 못했다는 `BLOCKED` 증거다. artifact 없음, 공식성, 보안 PASS로 재분류하지
   않는다.
7. **offline 전달도 다시 검증한다.** `docker save`/`docker load`는 bytes 이동
   방법일 뿐 attestation 보존을 보장하지 않는다. load 후 subject·signature·SBOM을
   독립 verifier로 다시 읽고 no-egress smoke receipt를 만든다.
8. **정책과 관측을 분리한다.** `execution.policy`는 요구하는 network·model source·
   privilege·limit이고 `execution.observed`는 실제 실행 receipt에서 읽은 값이다.
   정책을 선언했다고 관측된 실행으로 승격하지 않는다.
9. **detector와 recognizer를 함께 고정한다.** `model.models[]`는 role별 file/tree
   manifest를 가지며, pair binding과 canonical sorted path를 확인하기 전에는 한
   모델만으로 OCR acceptance를 만들지 않는다.

## 선택지 판단

| 경로 | 재사용 가능한 장점 | 버려야 하는 이유 또는 다음 조건 |
|---|---|---|
| 공식 PaddleX HPS tag | upstream serving 경로와 빠른 탐색 | mutable tag와 x86-64 제약. 실제 digest·attestation을 확보하기 전에는 후보 |
| pinned source 자체 build | source/base/model/lock과 builder를 통제할 수 있음 | trusted hosted builder와 NOTICE inventory를 추가로 운영해야 함. 다음 train의 권장 경로 |
| 고정 모델 파일 묶음 + 최소 CPU image | 모델 범위를 작게 고정하여 #544 비교에 적합 | image provenance와 model provenance를 각각 ledger에 연결해야 함 |
| amd64 emulation on arm64 | 임시 구조 확인 | native 지원·성능·resource 수치가 아니므로 benchmark evidence에서 제외 |
| mutable tag·자동 download | 초기 실행이 빠름 | 재현성·offline·license·egress 계약을 모두 깨므로 거부 |

## 재사용할 방어선

- `imageIndexDigest → platformManifestDigest → configDigest → executedDigest`를
  한 줄로 추적한다.
- `modelRevision → files[] → treeSha256 → license/NOTICE`를 image digest와 별도지만
  같은 ledger로 보존한다.
- SPDX SBOM, in-toto/SLSA provenance, signature가 같은 executed digest를 가리키지
  않으면 acceptance를 실패시킨다.
- `network=NONE`, `modelSource=PRELOADED`, non-root, read-only, resource limits,
  redacted log와 cleanup을 실행 receipt의 필수 필드로 둔다.
- synthetic validator PASS를 실제 Paddle service·model·SBOM·attestation PASS로
  표현하지 않는다.

## 7-Tier·writer lesson

이번 변경은 연구/계획/lesson/review/ledger 문서만 포함한다. Kotlin source, test,
Spring, Exposed, coroutine, dependency catalog, native binding, model file,
Dockerfile, production API는 변경하지 않았다. 따라서 `$bluetape-kotlin-patterns`는
`N/A (Kotlin 변경 0개)`이며, Kotlin 코드 품질 PASS를 의미하지 않는다.

문서 작성에서는 다음을 지켰다.

- `PENDING`, `BLOCKED`, `REJECTED`, `DEFER`의 상태 의미를 섞지 않았다.
- 공식 product name, API, command, URL, digest, schema key는 그대로 두었다.
- “안전하다”, “강력하다” 같은 평가 대신 어떤 bytes·subject·receipt가 빠졌는지
  적었다.
- 현재 구현, 제안된 ledger, 후속 Type-A를 별도 문단과 gate로 나눴다.
- 독립 reviewer 결과와 main inline follow-up을 서로 덮어쓰지 않는다.

artifact-ledger JSON은 이 train의 contract template다. strict JSON Schema, type/pattern,
`additionalProperties` 정책과 tamper/subject mismatch negative test는 #609-C에서
구현하고, template의 `PENDING` 또는 `false` 값을 실행 receipt로 해석하지 않는다.

상태는 다음과 같이 고정한다.

| 상태 | 의미 |
|---|---|
| `PENDING` | 필수 검사를 아직 시도하지 않았거나 receipt가 없음 |
| `BLOCKED` | artifact·권한·선행 receipt가 없어 진행할 수 없음 |
| `REJECTED` | evidence가 있지만 digest·subject·서명·정책과 불일치하거나 변조됨 |
| `DEFER` | 증거와 별개로 현재 채택 결정을 의도적으로 보류함 |

### Writer SPW-01~05

| 항목 | 판정과 근거 |
|---|---|
| SPW-01 대상·독자·범위 | PASS — #609와 trusted artifact 후속 실행자를 명시했다. |
| SPW-02 실행·실패·재개 | PASS — producer 선택, ledger, attestation, offline smoke의 재개 순서를 기록했다. |
| SPW-03 한국어·machine token | PASS — 한국어 prose와 command/API/URL/status token을 보존했다. |
| SPW-04 source·readback | PASS(문서 범위) — 고정 official URL HTTP 200 readback, `python3 scripts/research/test_paddle_ocr_receipt.py`, `python3 scripts/research/test_paddle_ocr_smoke.py`, `python3 -m json.tool docs/superpowers/research/2026-08-26-issue-609-artifact-ledger-contract.json`, 그리고 `python3 -c 'import json; d=json.load(open("docs/superpowers/research/2026-08-26-issue-609-artifact-ledger-contract.json")); assert {"producer.workflowRef","image.packageLockSha256","licenseNotice.complete","sbom.path","provenance.path","signature.path","execution.policy.targetPlatform","execution.observed.targetPlatform"} <= set(d["requiredFields"]); assert {m["role"] for m in d["model"]["models"]} == {"detector","recognizer"}; print("artifact-ledger structural audit: PASS")'` 결과를 review에 연결했다. 실행 가능한 ledger validator는 #609-C 후속이다. |
| SPW-05 사실·불확실성 | PASS — 실제 artifact가 없음을 `PENDING/BLOCKED/DEFER`로 분리했다. |

## 미해결 항목

trusted producer, target platform, image/model bytes, SPDX SBOM, provenance, signer,
NOTICE, clean offline smoke, #544 동일 corpus 비교가 모두 남아 있다. 따라서 현재
lesson의 상태는 `LESSON RECORDED / TRUSTED_ARTIFACT_PRODUCER_PENDING`이다.

## DoD Status

- [x] 공식 serving 문서와 trusted artifact 증거의 차이를 기록했다.
- [x] digest·platform·model bytes·SBOM/provenance subject binding 교훈을 재사용 가능한 형태로 정리했다.
- [x] registry 권한 실패, emulation, `docker load`의 한계를 명시했다.
- [x] Type-E 범위와 `$bluetape-kotlin-patterns` N/A 경계를 기록했다.
- [ ] 실제 producer artifact, attestation, offline smoke, benchmark 확보

최종 상태: `LESSON RECORDED / TRUSTED ARTIFACT PRODUCER PENDING`
