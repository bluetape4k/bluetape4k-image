# Issue #609 trusted artifact producer stacked train 계획

## 계획 메타데이터

| 항목 | 값 |
|---|---|
| 대상 | [#609 trusted artifact producer·SBOM·attestation 재개 gate](https://github.com/bluetape4k/bluetape4k-image/issues/609) |
| 선행 | [#544 corpus v2](https://github.com/bluetape4k/bluetape4k-image/issues/544), [#545 service/container gate](https://github.com/bluetape4k/bluetape4k-image/issues/545), [#546 provider-neutral OCR API·PaddleHTTP adapter 경계](https://github.com/bluetape4k/bluetape4k-image/issues/546) |
| 후속 | [#547 adoption decision](https://github.com/bluetape4k/bluetape4k-image/issues/547), [#169 Type-A 구현](https://github.com/bluetape4k/bluetape4k-image/issues/169), [#513 main epic](https://github.com/bluetape4k/bluetape4k-image/issues/513) |
| 실행 유형 | Type-E 연구·문서·검증. 실제 PaddleOCR 실행은 후속 gate |
| 기준 base | `develop` @ `fb15509b46a6c7103e8db58a67f2ea0708af7a88` |
| 승인 | 사용자 승인 후 `bluetape-flow` run `20260826T094849Z-f412674b` 실행 |

## 목표와 비목표

목표는 “공식 tag를 찾았다”와 “검증 가능한 trusted artifact를 확보했다”를 분리하고,
producer·플랫폼·model·SBOM·provenance·signature·offline 실행을 하나의 재개 순서로
고정하는 것이다.

이번 train에서는 다음을 하지 않는다.

- Paddle/PaddleX Python dependency, Kotlin API, HTTP adapter, production service 추가
- model bytes, Dockerfile, native runtime, mutable tag pull, first-use download
- registry credential 발급·저장, 외부 egress를 허용한 smoke, benchmark 실행
- #544 baseline 교체, #547 `DEFER` 변경, #169 Type-A 구현 착수

## stacked PR train

각 단계는 이전 단계의 immutable receipt와 artifact ledger를 입력으로 삼는다. 선행
단계가 `BLOCKED`이면 뒤 단계의 PR을 만들거나 실행하지 않는다.

| 순서 | 단계 | 산출물 | 선행 조건 | 종료 조건 |
|---:|---|---|---|---|
| 0 | #544 baseline | corpus manifest, Tesseract baseline, protocol receipt | 기존 #544 train | baseline SHA와 동일 corpus receipt가 존재 |
| 1 | #609-A 연구 계약 | trusted producer 연구, source ledger, artifact-ledger template | #545 Train 2의 availability boundary | 공식 source·선택지·플랫폼·attestation 계약 문서화 |
| 2 | #609-B producer 선택 | producer, source/workflow, target platform, registry 권한 범위 | #609-A `PASS` | 선택 결정과 rejection 근거, credential은 비밀값 없이 기록 |
| 3 | #609-C image/model ledger | image index/platform/config/base digest, package lock, model tree/file hash, license/NOTICE, strict schema/validator | #609-B | 모든 필수 digest가 실제 bytes와 일치하고 schema·negative test·ledger checksum 생성 |
| 4 | #609-D attestation | SPDX SBOM, in-toto/SLSA provenance, signer/OIDC receipt, same-subject 검증 | #609-C | SBOM·provenance·signature subject가 실행 platform digest와 동일 |
| 5 | #609-E offline smoke | clean host, preloaded image/model, no-egress, limits, redacted logs, cleanup | #609-D | readiness·OCR smoke·negative case·cleanup receipt 모두 PASS |
| 6 | #544-B comparison | 동일 corpus OCR 품질·성능·오류 비교 | #609-E, #544 baseline | 비교 결과와 통계·환경·resource receipt 재현 가능 |
| 7 | #547 decision | ADOPT/DEFER/REJECT decision record | #544-B와 모든 supply-chain receipt | decision이 새 receipt와 #545/#169/#513에 연결 |
| 8 | #169 Type-A | production API/dependency/service 구현 | #547가 ADOPT일 때만 | 별도 Type-A workflow와 Kotlin pattern gate 승인 |

현재 live Issue #609의 DoD는 `Required checks: 0/7; Blocked: 7`이다. plan의 각
gate `PENDING`은 해당 단계의 연구 증거를 아직 시도하지 않았다는 뜻이며, live
acceptance gate는 여전히 `BLOCKED`다.

현재 PR은 1단계만 다룬다. ledger JSON은 strict schema의 입력 계약 template이며
validator와 negative test는 #609-C 산출물이다. 2단계 이후의 실제 artifact와 실행
결과는 존재하지 않으며, 이 문서가 그 부재를 PASS로 승격하지 않는다.

## Issue #609 일곱 acceptance gate

| Gate | 필요한 evidence | 현재 상태 | 실패 처리 |
|---|---|---|---|
| G1 producer/platform | producer, source commit, workflow run, target `linux/amd64` 또는 `linux/arm64` | `PENDING` | 선택 전 실행 금지 |
| G2 image identity | image ref, index digest, platform manifest digest, config/base digest, package lock | `PENDING` | tag 또는 digest 누락이면 `BLOCKED` |
| G3 model identity | detector/recognizer revision, file/tree SHA, bytes, license/NOTICE | `PENDING` | metadata만 있으면 `BLOCKED` |
| G4 attestation | SPDX SBOM, provenance, signature/issuer/workflow, same subject | `PENDING` | artifact·권한·선행 receipt가 없으면 `BLOCKED`; subject mismatch·서명 불일치면 `REJECTED` |
| G5 runtime isolation | offline preload, `network=none`, non-root, read-only, resource limits, redacted logs | `PENDING` | egress·cleanup 실패면 `BLOCKED` |
| G6 negative cases | missing image/model, tamper, symlink, unlisted file, digest mismatch, signer mismatch | `PENDING` | fail-open이면 P0/P1 즉시 중단 |
| G7 decision linkage | #544 comparison, #547 decision, #545/#169/#513 readback | `PENDING` | 선행 receipt 없으면 후속 PR 보류 |

## artifact ledger invariant

후속 단계는 ledger의 `execution.observed.executedImageDigest`를 실제 실행한
platform manifest digest로 채운 뒤 다음 invariant를 만족해야 한다.

```text
ledger.status == PASS
AND verification.indexSelectsPlatformManifest == true
AND verification.manifestConfigMatches == true
AND verification.baseDigestsMatch == true
AND image.platformManifestDigest == execution.observed.executedImageDigest
AND canonical(image.os, image.architecture, image.variant) == execution.policy.targetPlatform
AND execution.policy.targetPlatform == execution.observed.targetPlatform
AND verification.platformMatches == true
AND sbom.subjectDigest == execution.observed.executedImageDigest
AND provenance.subjectDigest == execution.observed.executedImageDigest
AND signature.subjectDigest == execution.observed.executedImageDigest
AND image.packageLockSha256 != PENDING
AND sbom.path != PENDING
AND sbom.sha256 != PENDING
AND sbom.verified == true
AND provenance.path != PENDING
AND provenance.sha256 != PENDING
AND provenance.verified == true
AND signature.path != PENDING
AND signature.sha256 != PENDING
AND signature.signerIdentity != PENDING
AND signature.oidcIssuer != PENDING
AND signature.verified == true
AND model.models[role=detector].treeSha256 == recomputedModelTreeSha256[detector]
AND model.models[role=recognizer].treeSha256 == recomputedModelTreeSha256[recognizer]
AND model.models[role=detector].licenseVerified == true
AND model.models[role=detector].noticeVerified == true
AND model.models[role=recognizer].licenseVerified == true
AND model.models[role=recognizer].noticeVerified == true
AND licenseNotice.complete == true
AND licenseNotice.inventorySha256 != PENDING
AND licenseNotice.verified == true
AND producer.workflowRef IN trustPolicy.allowedWorkflowRefs
AND producer.repository IN trustPolicy.allowedRepositories
AND producer.workflow IN trustPolicy.allowedWorkflowPaths
AND producer.builderIdentity IN trustPolicy.allowedBuilderIdentities
AND producer.signerIdentity IN trustPolicy.allowedSignerIdentities
AND producer.oidcIssuer IN trustPolicy.allowedOidcIssuers
AND trustPolicy.allowlistNonEmpty == true
AND trustPolicy.policySha256 != PENDING
AND execution.policy.network == NONE
AND execution.policy.modelSource == PRELOADED
AND execution.observed.network == NONE
AND execution.observed.modelSource == PRELOADED
AND execution.observed.containerUser == 65532:65532
AND execution.observed.readOnlyFilesystem == true
AND execution.observed.noNewPrivileges == true
AND execution.observed.capDrop == ALL
AND execution.observed.resourceLimits.pids != PENDING
AND execution.observed.resourceLimits.memory != PENDING
AND execution.observed.resourceLimits.cpu != PENDING
AND execution.observed.resourceLimits.tmpfs != PENDING
AND execution.observed.requestLimits != PENDING
AND execution.observed.logRedactionVerified == true
AND execution.observed.smokeReceiptSha256 != PENDING
AND execution.observed.cleanupReceiptSha256 != PENDING
AND execution.observed.noEgressReceiptSha256 != PENDING
AND verification.offlineNoEgress == true
AND verification.imageSubjectMatches == true
AND verification.platformMatches == true
AND verification.sbomSubjectMatches == true
AND verification.provenanceSubjectMatches == true
AND verification.signatureSubjectMatches == true
AND verification.modelTreeRecomputed == true
AND verification.cleanupVerified == true
AND verification.negativeCasesFailClosed == true
AND verification.trustPolicyMatched == true
```

`verification.trustPolicyMatched`는 opaque 선언값이 아니다. verifier가 위 다섯
membership, `producer.workflowRef` membership, non-empty allowlist, full-commit
source policy, non-PENDING `policySha256`를 모두 계산한 conjunction이어야 한다.
하나라도 빠지거나 정책 hash가 재계산되지 않으면 `false`로 남긴다.

`imageIndexDigest`만 맞고 플랫폼 manifest가 다르거나, manifest의 config/base digest가
ledger와 다르거나, SBOM 파일의 SHA-256만 맞고 subject가 다르면 invariant를 만족하지
않는다. `PENDING` ledger는 template이며 실행 입력이 아니다. 정책 필드와 실제 관측
필드를 혼동하지 않도록 `execution.policy`와 `execution.observed`를 분리한다.

모델 tree hash는 role별로 계산한다. 각 role의 canonical sorted file manifest를
`path\tbytes\tsha256\n` 형식으로 직렬화한 UTF-8 bytes의 SHA-256을
`detectorTreeSha256`와 `recognizerTreeSha256`로 기록한다. pair hash는
`detector\n<detectorTreeSha256>\nrecognizer\n<recognizerTreeSha256>\n`을 같은
방식으로 계산한 `pairBindingSha256`이며 role 순서는 고정한다.

`recomputedModelTreeSha256[detector]`와 `[recognizer]`는 #609-C verifier가 각 role의
실제 staged file manifest에서 계산해 내는 출력이다. verifier는 그 두 출력을
ledger의 동일 role `treeSha256`와 비교하고 pair hash도 같은 두 출력으로 다시
계산한다. 따라서 model card revision만 있고 파일·license/NOTICE·package lock이
없으면 이 invariant를 만족할 수 없다. `canonical(os, architecture, variant)`는
OCI descriptor의 `linux/amd64`, `linux/arm64`, 필요 시 variant를 포함한 문자열이며
raw host 값은 별도 receipt에 보존한다.

## producer·플랫폼 선택 기준

| 기준 | 최소 요구 | 확인 방법 |
|---|---|---|
| source | PaddleOCR/PaddleX source full commit, package lock | upstream commit URL와 lock SHA |
| builder | hosted/trusted workflow, builder identity, run id | provenance predicate와 workflow readback |
| platform | 실행 host와 동일한 OS/architecture/variant | `imagetools inspect`와 runtime receipt |
| image | digest-pinned index/platform/config/base identity | registry manifest와 local inspect |
| model | full revision, file/tree hash, license/NOTICE | staged manifest와 streaming hash |
| supply chain | SPDX JSON, in-toto/SLSA, signer/OIDC policy | allowlist와 independent verifier |
| runtime | no egress, non-root, read-only, limits, cleanup | smoke harness receipt |

공식 HPS tag는 upstream 호환성을 보여 주는 후보일 뿐이며, 위 표의 builder·subject·
signature 증거가 없으면 선택 완료로 기록하지 않는다. local arm64에서 amd64를
emulation으로 실행하는 것도 platform 증거가 아니다.
runtime raw architecture가 `aarch64`로 나오면 raw 값을 보존하되 ledger에는
canonical OCI `os=linux`, `architecture=arm64`로 정규화한다.

## 위험과 대안

| 위험 | 영향 | 완화 | 잔여 판단 |
|---|---|---|---|
| mutable tag 재지정 | 같은 입력을 재현할 수 없음 | index/platform/config digest를 모두 pin | digest 수집 전 `BLOCKED` |
| multi-platform index 혼동 | arm64/amd64 bytes가 달라짐 | platform별 subject와 attestation 분리 | native receipt 전 `PENDING` |
| 공식 registry `401` | artifact 부재와 권한 실패를 혼동 | redacted endpoint·status를 `BLOCKED`로 보존 | 인증 증거 전 보류 |
| model first-use download | egress·drift·license 누락 | pre-baked 모델 파일 묶음, offline staging, file/tree hash | model ledger 전 실행 금지 |
| SBOM/provenance subject mismatch | 다른 image를 검증할 위험 | executed platform digest를 모든 subject에 대조 | mismatch는 `REJECTED` |
| container NOTICE 누락 | 재배포·법적 추적성 부족 | producer third-party inventory와 license/NOTICE 수집 | inventory 전 보류 |
| emulation 성능 착시 | benchmark가 native 지원을 대표하지 않음 | target platform builder 또는 hosted runner 사용 | emulation 결과 폐기 |
| attestation가 local load에서 소실 | offline image가 서명 없이 실행됨 | load 후 독립 verifier와 새 receipt 실행 | 검증 전 `BLOCKED` |

## 7-Tier 실행 검토 계획

1. **계약·범위** — #609의 7 gates, #545/#544 선행, #547/#169 후속과 Type-E 한계를
   확인한다.
2. **보안·공급망** — producer, digest, signer, SBOM, provenance, NOTICE, no-egress를
   같은 subject에 연결하고 fail-closed negative case를 확인한다.
3. **정확성·추적성** — 공식 source URL/commit, ledger SHA, receipt와 live GitHub
   metadata를 서로 대조한다.
4. **운영·플랫폼** — target architecture, native/emulation 구분, resource/cleanup,
   registry 권한 실패를 검토한다.
5. **성능·benchmark** — #544와 동일 corpus·환경·resource를 사용할 때만 숫자를
   기록한다. artifact가 없으면 `N/A/PENDING`이다.
6. **API·호환성** — 이번 train은 Kotlin/API/dependency를 변경하지 않는다. ADOPT 후
   별도 Type-A에서 `$bluetape-kotlin-patterns`를 적용한다.
7. **문서·CI·release** — 한국어 SPW-01~05, link/readback, docs-only CI, PR metadata와
   merge gate를 별도로 검증한다.

독립 reviewer는 main lane과 다른 read-only lane에서 source claim, ledger invariant,
P0-P3 위험, writer 경계를 판정한다. timeout이면 timeout receipt와 main inline review를
분리해서 기록하며, 독립 PASS를 추정하지 않는다.

## Writer SPW-01~05

| 항목 | 판정과 근거 |
|---|---|
| SPW-01 대상·독자·범위 | PASS — #609와 선행·후속 issue/epic, 실행 독자를 메타데이터와 train 표에 고정했다. |
| SPW-02 실행·실패·재개 | PASS — 0~8 stacked 단계와 gate별 `PENDING/BLOCKED/REJECTED` 전이를 기록했다. |
| SPW-03 한국어·machine token | PASS — 한국어 prose와 API/URL/digest/schema/status token을 분리했다. |
| SPW-04 source·readback | PASS(문서 범위) — 공식 source HTTP 200 readback, `python3 scripts/research/test_paddle_ocr_receipt.py`, `python3 scripts/research/test_paddle_ocr_smoke.py`, `python3 -m json.tool docs/superpowers/research/2026-08-26-issue-609-artifact-ledger-contract.json`, 그리고 `python3 -c 'import json; d=json.load(open("docs/superpowers/research/2026-08-26-issue-609-artifact-ledger-contract.json")); assert {"producer.workflowRef","image.packageLockSha256","licenseNotice.complete","sbom.path","provenance.path","signature.path","execution.policy.targetPlatform","execution.observed.targetPlatform"} <= set(d["requiredFields"]); assert {m["role"] for m in d["model"]["models"]} == {"detector","recognizer"}; print("artifact-ledger structural audit: PASS")'` structural audit를 실행했다. ledger executable validator는 #609-C 후속 산출물이다. |
| SPW-05 사실·불확실성 | PASS — 실제 artifact가 없다는 사실과 후속 조건을 명시하고 benchmark/ADOPT를 보류했다. |

## 완료·재개 기준

### 이번 PR의 완료 기준

- 연구 문서가 공식 URL/고정 commit과 한계를 기록한다.
- ledger template가 실제 digest를 발명하지 않고 `PENDING` 상태를 명시한다.
- plan이 선후 관계와 후속 issue/epic을 연결한다.
- lesson과 7-Tier review가 독립 reviewer 결과와 P0-P3 disposition을 기록한다.
- Kotlin/API/dependency/model/runtime 변경이 없고 문서 검증만 수행한다.

### 다음 단계로 넘어가는 기준

- trusted producer와 target platform이 승인된 source/workflow receipt로 식별된다.
- image/model/SBOM/provenance/signature의 subject digest가 동일하다.
- clean offline smoke와 negative cases가 모두 PASS다.
- #544 동일 corpus 비교가 끝나고 #547가 새 receipt를 읽었다.

위 조건 중 하나라도 빠지면 현재 상태는 `PENDING` 또는 `BLOCKED`이며, #547 ADOPT와
#169 Type-A PR을 생성하지 않는다.

## DoD Status

- [x] #609를 #545/#544/#547/#169/#513 순서에 넣은 stacked train을 정의했다.
- [x] producer·platform·image·model·SBOM·attestation·offline 실행의 7 gate를 정의했다.
- [x] 가능성·위험성·장점·단점·대안·중단 조건을 비교했다.
- [x] 실제 artifact가 없을 때의 `PENDING/BLOCKED/REJECTED` 처리와 선행 의존성을 고정했다.
- [x] 이번 PR의 Type-E 범위와 후속 Type-A 경계를 분리했다.
- [ ] trusted producer 선택과 artifact receipt 수집
- [ ] offline smoke, benchmark, #547 decision, #169 implementation

최종 상태: `PLAN READY / EXECUTION BLOCKED UNTIL TRUSTED ARTIFACT EVIDENCE`
