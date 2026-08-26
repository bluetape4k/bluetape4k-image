# Issue #609 trusted artifact producer·SBOM·attestation 연구

## 조사 메타데이터

| 항목 | 값 |
|---|---|
| Issue | [#609 trusted artifact producer·SBOM·attestation 재개 gate](https://github.com/bluetape4k/bluetape4k-image/issues/609) |
| 상위 Issue | [#545 PaddleOCR service/container 공급망·보안·CI 검증](https://github.com/bluetape4k/bluetape4k-image/issues/545) |
| 상위 Epic | [#169 image intelligence](https://github.com/bluetape4k/bluetape4k-image/issues/169) |
| Main epic | [#513](https://github.com/bluetape4k/bluetape4k-image/issues/513) |
| 기준 base | `develop` @ `fb15509b46a6c7103e8db58a67f2ea0708af7a88` |
| 조사일 | 2026-08-26 (Asia/Seoul) |
| 유형 | Type-E 연구·문서·검증 계약 |
| 상태 | `TRUSTED_ARTIFACT_PRODUCER_PENDING` |
| 변경 범위 | 연구 문서, 계획, lesson, review, ledger template만 변경. Kotlin/API/dependency/model/service 변경 없음 |

## 결론

PaddleOCR와 PaddleX의 공식 문서는 serving 방법과 Docker 후보를 제시하지만, 문서의
이미지 tag나 model card만으로는 Issue #609가 요구하는 trusted artifact를 증명할 수
없다. 현재 재개 기준은 다음 하나의 immutable tuple을 같은 ledger에 묶는 것이다.

```text
(producer, sourceRevision, workflowRunId, imageIndexDigest,
 platformManifestDigest, configDigest, modelRevision, modelTreeSha256,
 sbomSubjectDigest, provenanceSubjectDigest, signerIdentity, targetPlatform)
```

현재 확인된 공식 후보에는 실제 subject digest, 서명된 SPDX SBOM, in-toto/SLSA
provenance, 완전한 third-party NOTICE, offline no-egress 실행 receipt가 모두 없다.
따라서 `#547 DEFER`, Tesseract baseline 유지, PaddleOCR benchmark·Type-A 구현 보류를
그대로 유지한다. 이 문서는 artifact를 다운로드하거나 실행하지 않는다.

## 공식 source와 주장 경계

공식 source는 2026-08-26에 upstream commit 또는 공식 문서 URL로 다시 확인했다.
source가 serving 방법을 설명한다는 사실과 해당 bytes를 trusted producer가 만들었다는
사실을 분리한다.

| 주장 | 공식 source | 이번 train에서 확인한 사실 | 확인하지 못한 사실 |
|---|---|---|---|
| PaddleOCR production serving 경로 | [PaddleOCR serving guide](https://github.com/PaddlePaddle/PaddleOCR/blob/b03f46425e8ff4442b268ce449e3eef758146cd4/docs/version3.x/inference_deployment/serving/serving.en.md#L5-L14) | PaddleX Basic Serving을 안내하고 고안정성 경로로 NVIDIA Triton을 제시한다. | 해당 serving image의 immutable digest, signer, SBOM, provenance |
| PaddleX Docker 후보 | [PaddleX serving guide](https://github.com/PaddlePaddle/PaddleX/blob/ffb64904d23708863ff5b8da312a5cbd52a7f462/docs/pipeline_deploy/serving.en.md#L316-L357), [PaddleX 3.5 installation example](https://paddlepaddle.github.io/PaddleX/3.5/en/installation/installation.html) | pinned serving source와 별도로 3.5 설치 문서의 예시 tag를 역사적 참고로 확인했다. 예제에는 mutable tag와 host/network 전제가 있다. 현재 producer version으로 채택하지 않는다. | tag가 가리키는 현재 bytes, platform manifest digest, provenance, SBOM subject |
| PaddleOCR HPI 지원 플랫폼 | [HPI architecture guide](https://github.com/PaddlePaddle/PaddleOCR/blob/b03f46425e8ff4442b268ce449e3eef758146cd4/docs/version3.x/inference_deployment/local_inference/high_performance_inference.en.md#L27-L44) | 문서가 Linux x86-64를 명시하므로 local arm64 native 지원은 별도 증명이 필요하다. | arm64용 native image와 실행 성능 |
| offline image 전달 | [PaddleOCR-VL deployment guide](https://github.com/PaddlePaddle/PaddleOCR/blob/b03f46425e8ff4442b268ce449e3eef758146cd4/docs/version3.x/pipeline_usage/PaddleOCR-VL.en.md#L260-L309) | `docker save`/`docker load`를 설명한다. `latest-*` tag는 immutable identity가 아니다. | load 후 attestation 보존, 실제 no-egress model 실행 |
| build 플랫폼 | [PaddleOCR VL build script](https://github.com/PaddlePaddle/PaddleOCR/blob/eeb12cd4de185ba444a2aff6b56ab31995558e1c/deploy/paddleocr_vl_docker/build_vlm.sh#L8-L42) | 기본 `linux/amd64`, offline build와 platform 옵션을 확인했다. | script 자체가 SBOM/provenance/서명을 생성한다는 보장 |
| digest 의미 | [Docker image digests](https://docs.docker.com/dhi/explore/security-concepts/digests/), [Docker image pull](https://docs.docker.com/reference/cli/docker/image/pull/) | `image@sha256:<digest>`가 tag보다 정확한 content identity다. | digest만으로 producer·builder·license를 증명할 수 없음 |
| multi-platform identity | [Docker multi-platform builds](https://docs.docker.com/build/building/multi-platform/), [imagetools inspect](https://docs.docker.com/reference/cli/docker/buildx/imagetools/inspect/) | index digest와 플랫폼별 manifest digest를 분리해야 한다. arm64에서 amd64 emulation은 native 증거가 아니다. | 현재 candidate의 trusted platform manifest |
| SBOM/provenance | [Docker Build attestations](https://docs.docker.com/build/metadata/attestations/), [Docker SBOM attestations](https://docs.docker.com/build/metadata/attestations/sbom/), [Docker GitHub Actions attestations](https://docs.docker.com/build/ci/github-actions/attestations/) | BuildKit의 SPDX SBOM과 in-toto provenance 생성 경로를 확인했다. registry push와 subject 검증이 필요하다. | 실제 image subject에 붙은 attestation과 서명 |
| attestation 검증 | [in-toto Statement v1](https://github.com/in-toto/attestation/blob/main/spec/v1/statement.md), [in-toto validation](https://github.com/in-toto/attestation/blob/main/docs/validation.md), [SLSA Build](https://slsa.dev/spec/v1.2/build-track-basics), [Cosign verification](https://docs.sigstore.dev/cosign/verifying/verify/) | subject digest, signer identity, issuer, predicate와 실제 artifact digest를 함께 검증해야 한다. | 허용 signer의 실제 서명 receipt |
| 모델 identity | [PP-OCRv6 medium detector @ `8e0f56fb2ef86b461d99cfc7ac5c137738985f61`](https://huggingface.co/PaddlePaddle/PP-OCRv6_medium_det/tree/8e0f56fb2ef86b461d99cfc7ac5c137738985f61), [PP-OCRv6 medium recognizer @ `e5a92bcbc5cc1b494628e458d267778f0704fd7c`](https://huggingface.co/PaddlePaddle/PP-OCRv6_medium_rec/tree/e5a92bcbc5cc1b494628e458d267778f0704fd7c) | full revision과 model card license metadata를 후보 identity로 고정했다. 이 링크는 model bytes/tree/NOTICE 검증을 대신하지 않는다. | 전체 model bytes/tree, NOTICE, detector·recognizer pair의 한 번에 검증된 고정 파일 묶음 |
| license source | [PaddleOCR LICENSE](https://github.com/PaddlePaddle/PaddleOCR/blob/b03f46425e8ff4442b268ce449e3eef758146cd4/LICENSE), [PaddleX LICENSE](https://github.com/PaddlePaddle/PaddleX/blob/ffb64904d23708863ff5b8da312a5cbd52a7f462/LICENSE) | upstream code의 Apache-2.0 선언을 확인했다. | container와 transitive dependency 전체의 NOTICE/third-party inventory |

공식 문서의 tag, `docker push`, `docker save`, model card revision은 각각 실행 방법,
전달 방법, upstream metadata를 말할 뿐이다. producer identity, build input, 대상
platform, 실제 bytes와 서명된 attestation을 대신하지 않는다.

## trusted producer 선택지

| 선택지 | 가능성 | 장점 | 위험·중단 조건 | 이번 train 판정 |
|---|---:|---|---|---|
| PaddleX 공식 HPS image tag | 조건부 | upstream serving 지원과 빠른 CPU/GPU 탐색 | tag mutable, digest/SBOM/provenance 미확인, x86-64 제약 | 후보로만 유지 |
| pinned source에서 자체 image 재생성 | 높음 | source·base·package lock·고정 모델 파일 묶음과 builder를 통제하고 attestation 생성 가능 | trusted hosted builder, native dependency, NOTICE 수집 책임이 필요 | 후속 권장 경로 |
| 공식 고정 모델 파일 묶음 + 최소 CPU serving image | 높음 | model commit/file hash를 좁은 범위로 고정하고 비교 가능 | image와 model의 provenance를 각각 증명해야 함 | CPU benchmark 후속 후보 |
| 인증된 registry mirror | 조건부 | 승인된 네트워크 경로와 cache 정책을 만들 수 있음 | mirror가 원 producer와 동일 subject임을 다시 증명해야 함 | 원본 attestation 없으면 거부 |
| mutable tag·first-use model download | 낮음 | 초기 탐색이 빠름 | drift, egress, 재현 불가, license/NOTICE 누락 | 명시적 거부 |
| amd64 image를 arm64에서 emulation 실행 | 낮음 | 임시 smoke 가능 | native 지원·성능·메모리 증거가 아님 | benchmark 입력으로 거부 |

권장 producer는 pinned source와 model revision을 trusted hosted builder에서 만들고,
플랫폼별 digest를 registry에 push한 뒤 같은 digest에 SPDX SBOM과 signed
in-toto/SLSA provenance를 첨부하는 방식이다. 공식 image tag를 그대로 채택하는
것보다 운영 부담은 크지만, Issue #609의 검증 경계를 만족시킬 수 있다.

## 플랫폼 정책

현재 local Colima/Docker host는 `linux/arm64`이고, 이전 train에서 확인한 공개
Paddle image 후보는 `linux/amd64`였다. 따라서 한 개의 모호한 multi-platform tag를
실행 입력으로 삼지 않는다.

1. 후속 실행 train은 `linux/amd64` hosted CPU runner 또는 검증된 `linux/arm64`
   builder 중 하나를 먼저 선택한다.
2. ledger에는 image index digest와 함께 `os`, `architecture`, `variant`,
   `platformManifestDigest`, `configDigest`를 모두 기록한다.
3. multi-platform index를 배포하더라도 acceptance receipt는 실제 실행한
   platform manifest subject에 연결한다.
4. QEMU emulation은 탐색용 정보일 뿐 native smoke·benchmark·adoption 증거로
   승격하지 않는다.
5. 플랫폼별 image가 서로 다른 bytes이면 각각의 subject에 SBOM·provenance·서명을
   붙인다. index 하나의 서명만으로 모든 플랫폼 결과를 덮지 않는다.
6. runtime raw 값이 `aarch64`로 보고되더라도 ledger의 canonical OCI 값은
   `os=linux`, `architecture=arm64`로 저장하고 raw environment receipt를 함께
   보존한다.

## model·license·NOTICE ledger

model card의 revision과 license를 확인한 뒤에도 다음 항목이 있어야 model artifact를
acceptance로 승격한다.

- detector와 recognizer 각각의 full immutable revision
- 고정 모델 파일 묶음의 모든 파일 경로, byte size, SHA-256, 전체 tree SHA-256
- source URL, download time, producer, cache/import receipt
- SPDX license expression과 원문 license 파일
- model과 container/transitive dependency의 NOTICE/third-party inventory
- symlink·path traversal·unlisted file·size limit 검사 결과
- `offline=true`와 first-use network 금지 확인

실제 model bytes는 이번 train에서 받거나 저장하지 않는다. ledger template의
`PENDING` 값은 미확인 상태이며 실제 SHA-256을 의미하지 않는다.

## SBOM·provenance·attestation acceptance

다음 조건을 하나의 image subject digest에 대해 모두 확인해야 한다.

| 단계 | 필요한 증거 | 실패 시 판정 |
|---|---|---|
| producer | repository, workflow, commit, builder identity, creation time | `BLOCKED` |
| image | index/platform manifest/config digest, base image digest, package lock | `BLOCKED` |
| SBOM | SPDX JSON, file SHA-256, SBOM artifact SHA-256, subject digest | `BLOCKED` |
| provenance | in-toto/SLSA predicate, source revision, workflow run, builder | `BLOCKED` |
| signature | signer identity, OIDC issuer, signature/timestamp, policy decision | `BLOCKED` |
| subject binding | SBOM·provenance·signature subject가 실행한 platform digest와 동일 | `BLOCKED` |
| offline execution | preloaded image/model, `network=none`, no-egress receipt, cleanup | `BLOCKED` |

Docker BuildKit의 `--sbom=true`, `--provenance=mode=max`는 생성 경로일 뿐이다.
생성 옵션이 있었다는 사실만으로 서명·issuer·subject 일치를 승인하지 않는다.
`docker save`/`docker load`는 bytes 전달 방법이며 attestation 보존을 자동 보장하지
않으므로 load 후 독립 검증 receipt를 새로 만든다.

최소 검증 순서는 다음과 같다.

```bash
docker buildx imagetools inspect IMAGE@sha256:IMAGE_INDEX_DIGEST
gh attestation verify oci://REGISTRY/REPOSITORY \
  -R OWNER/REPOSITORY \
  --signer-workflow OWNER/REPOSITORY/.github/workflows/producer.yml
cosign verify IMAGE@sha256:PLATFORM_MANIFEST_DIGEST \
  --certificate-oidc-issuer ISSUER \
  --certificate-identity-regexp 'ALLOWED_IDENTITY_REGEX'
```

위 명령의 실제 값과 성공 결과가 없으면 이 문서의 상태를 `PASS`로 바꾸지 않는다.

## canonical 상태와 fail-closed 실행 경계

문서와 validator는 다음 상태를 같은 의미로 사용한다.

| 상태 | 의미 | 다음 조치 |
|---|---|---|
| `PENDING` | 필수 검사를 아직 시도하지 않았거나 receipt가 없다. | evidence를 수집한 뒤 재평가 |
| `BLOCKED` | artifact·권한·선행 receipt가 없어 현재 실행을 진행할 수 없다. | blocker를 해소하고 새 receipt 생성 |
| `REJECTED` | evidence는 있지만 digest·subject·서명·trust policy와 불일치하거나 변조됐다. | 해당 artifact를 폐기하고 재사용하지 않음 |
| `DEFER` | 증거와 별개로 현재 제품 채택 결정을 의도적으로 보류한다. | #547 decision train에서 재평가 |

`PENDING`을 `PASS`로, `BLOCKED`를 “없음”으로, `REJECTED`를 일시적 권한 실패로
바꾸지 않는다.

기존 `scripts/research/paddle_ocr_smoke.py`와
`scripts/research/paddle_ocr_receipt.py`의 preflight/receipt 계약을 후속 실행 train의
입구로 사용한다.

- image ref는 `name@sha256:<64 hex>`만 허용한다.
- 고정 모델 파일 묶음은 컨테이너 시작 전에 immutable manifest와 file/tree hash를
  통과해야 한다.
- 서비스는 loopback bind, `network=none`, read-only filesystem, non-root,
  `no-new-privileges`, cap drop, pids/memory/cpu/tmpfs limit를 적용한다.
- command에는 shell syntax, download, remote install을 넣지 않는다.
- 로그는 redaction 후 보존하고, cleanup 실패는 acceptance 실패로 처리한다.
- missing image/model, digest mismatch, symlink, unlisted file, SBOM subject mismatch,
  signer mismatch는 원인에 따라 `BLOCKED` 또는 `REJECTED`로 끝낸다.

이번 train에서는 이 preflight를 실제 Paddle artifact에 실행하지 않았다. 이전
Train 2의 synthetic availability receipt와 현재 validator 테스트는 harness 계약을
증명하지만, service readiness·OCR quality·SBOM signature를 증명하지 않는다.

## 7-Tier·writer 판정

| Tier | 범위 | 판정 |
|---|---|---|
| 1. 계약·범위 | #609/#545/#169/#513 연결, Type-E와 non-goal | PASS |
| 2. 보안·공급망 | producer, digest, model bytes, SBOM, attestation, egress | 문서 계약 PASS / 실제 artifact PENDING |
| 3. 정확성·추적성 | pinned official source, ledger tuple, subject binding | 설계 계약 PASS / 실제 subject PENDING |
| 4. 운영·플랫폼 | arm64/amd64 분리, offline/no-egress, fail-closed | 재개 조건 PASS / 실제 실행 PENDING |
| 5. 성능·benchmark | #544 동일 corpus, CPU/GPU, SLO | N/A/PENDING — 실행 artifact 없음 |
| 6. API·호환성 | Kotlin, dependency, service/API | N/A — 변경 파일 0개 |
| 7. 문서·CI·release | Korean writer, links, CI/PR readback, release boundary | 문서 계약 PASS / SPW·PR live gate PENDING |

### Writer SPW-01~05

| 항목 | 판정과 근거 |
|---|---|
| SPW-01 대상·독자·범위 | PASS — #609, #545, #544, #547, #169와 후속 독자를 메타데이터와 plan에 고정했다. |
| SPW-02 실행·실패·재개 | PASS — producer 선택부터 offline smoke까지 순서, 상태, 중단 조건을 기록했다. |
| SPW-03 한국어·machine token | PASS — 한국어 기술 문체를 사용하고 command/API/URL/digest/status token을 보존했다. |
| SPW-04 source·readback | PASS(문서 범위) — 고정 upstream URL/commit의 HTTP 200 readback, `python3 scripts/research/test_paddle_ocr_receipt.py`, `python3 scripts/research/test_paddle_ocr_smoke.py`, `python3 -m json.tool docs/superpowers/research/2026-08-26-issue-609-artifact-ledger-contract.json`, 그리고 `python3 -c 'import json; d=json.load(open("docs/superpowers/research/2026-08-26-issue-609-artifact-ledger-contract.json")); assert {"producer.workflowRef","image.packageLockSha256","licenseNotice.complete","sbom.path","provenance.path","signature.path","execution.policy.targetPlatform","execution.observed.targetPlatform"} <= set(d["requiredFields"]); assert {m["role"] for m in d["model"]["models"]} == {"detector","recognizer"}; print("artifact-ledger structural audit: PASS")'` 결과를 review artifact에 연결했다. 실행 가능한 ledger validator와 실제 artifact 검증은 #609-C 후속이다. |
| SPW-05 사실·불확실성 | PASS — 실제 artifact 부재와 `PENDING/BLOCKED/DEFER`를 문서와 ledger에서 분리했다. |

`$bluetape-kotlin-patterns`는 Kotlin 파일과 테스트를 한 줄도 변경하지 않았으므로
`N/A (0 Kotlin files touched)`다. 이는 Kotlin 품질 검토를 통과했다는 뜻이 아니다.
문서에는 command, API name, URL, digest와 `PENDING/BLOCKED/DEFER` 토큰을 그대로
보존한다.

## 재개 순서와 중단 조건

1. trusted producer와 target platform을 선택하고 builder/source/workflow identity를
   등록한다.
2. 플랫폼별 image digest/config/base digest와 exact package lock을 수집한다.
3. detector/recognizer model tree, file hash, license/NOTICE를 staging에서 검증한다.
4. 같은 image subject에 SPDX SBOM, in-toto/SLSA provenance, signature를 연결하고
   독립 verifier로 검증한다.
5. preloaded image/model로 clean `network=none` smoke와 cleanup을 실행한다.
6. [#544](https://github.com/bluetape4k/bluetape4k-image/issues/544)와 같은 corpus로
   OCR 품질·성능·오류를 비교한다.
7. 모든 receipt와 ledger를 다시 읽은 뒤에만 [#547](https://github.com/bluetape4k/bluetape4k-image/issues/547)
   adoption decision과 [#169](https://github.com/bluetape4k/bluetape4k-image/issues/169)
   Type-A implementation을 검토한다.

다음 항목이 하나라도 빠지면 실행 train을 중단한다: mutable tag, first-use download,
platform mismatch, unsigned subject, SBOM/provenance subject 불일치, NOTICE 누락,
registry 권한 실패를 성공으로 해석하는 경우, emulation 결과를 native 증거로
표현하는 경우.

## DoD Status

- [x] 공식 producer/serving·Docker·SPDX·in-toto/SLSA·Cosign source와 적용 한계를 기록했다.
- [x] image index/platform/config digest와 model revision/tree/NOTICE의 분리 계약을 고정했다.
- [x] trusted producer 선택지의 가능성·장점·위험·대안·중단 조건을 비교했다.
- [x] local arm64와 candidate amd64의 차이를 native acceptance 경계로 기록했다.
- [x] 실제 image/model/SBOM/provenance/signature를 수집·실행하지 않았고 상태를 `PENDING/BLOCKED`로 유지했다.
- [x] 후속 execution·benchmark·adoption 순서를 #544/#545/#547/#169에 연결했다.
- [ ] trusted producer artifact와 signed attestation 확보
- [ ] clean offline PaddleOCR smoke 및 #544 동일 corpus benchmark

최종 판정: `TRUSTED_ARTIFACT_PRODUCER_PENDING / PaddleOCR provider DEFER 유지`
