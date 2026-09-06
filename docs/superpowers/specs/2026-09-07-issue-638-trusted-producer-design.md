# Issue #638 PaddleOCR trusted producer 설계

## 문서 메타데이터

| 항목 | 값 |
|---|---|
| 대상 | [#638 PaddleOCR trusted image/model producer pipeline과 registry 권한 구축](https://github.com/bluetape4k/bluetape4k-image/issues/638) |
| milestone | `1.1.0` |
| consumer gate | [#611 trusted producer 선택](https://github.com/bluetape4k/bluetape4k-image/issues/611) |
| 상위 acceptance | [#609 trusted artifact acceptance](https://github.com/bluetape4k/bluetape4k-image/issues/609) |
| spec review | [`2026-09-07-issue-638-trusted-producer-spec-review.md`](../reviews/2026-09-07-issue-638-trusted-producer-spec-review.md) |
| 기준 branch | `develop` |
| 승인된 방향 | 이 저장소가 소유하는 GitHub Actions producer, native `linux/amd64`, GHCR, GitHub artifact attestation |
| 문서 언어 | 한국어. 경로, API, digest, status, 명령은 원문 표기를 유지한다. |

## 문제와 목표

#611은 producer repository, workflow, builder, signer, OIDC issuer와 image/model
identity를 독립적으로 검증해야 한다. 현재 저장소에는 이 증거를 생성하는 producer가
없다. upstream source tag와 model URL은 찾았지만, mutable download 결과나 registry
metadata만으로는 실제 실행 image의 재현성, model tree, 재배포 고지, SBOM과
attestation subject를 증명할 수 없다.

#638의 목표는 다음 evidence chain을 한 번의 통제된 producer run에서 생성하는 것이다.

```text
immutable input allowlist
  -> verified ephemeral staging
  -> native linux/amd64 image build
  -> GHCR platform digest
  -> model/file/tree and license inventory
  -> SPDX JSON SBOM + build provenance
  -> signed GitHub attestations on the same digest
  -> fail-closed producer ledger
  -> #611 read-only verification
```

producer 구축과 실제 게시를 분리한다. 코드와 workflow를 검토·병합하는 단계는
package를 만들지 않는다. `workflow_dispatch`, 최초 GHCR publish, package visibility
변경은 각각 정확한 ref와 target을 다시 확인한 뒤 별도 승인으로 실행한다.

## 범위

### 포함

- `bluetape4k/bluetape4k-image`가 소유하는 수동 실행 producer workflow
- native GitHub-hosted `linux/amd64` runner에서 만드는 CPU service image
- PaddleOCR, PaddleX, PaddlePaddle wheel, base image, detector/recognizer model의
  immutable input allowlist
- model의 파일별 SHA-256, role별 tree SHA-256, pair binding SHA-256
- container와 model 재배포에 필요한 license/NOTICE inventory 검증
- `ghcr.io/bluetape4k/paddleocr-service` image push와 digest read-back
- 실행 platform manifest digest를 공통 subject로 사용하는 SPDX JSON SBOM과
  build provenance attestation
- repository/workflow/builder/signer/OIDC allowlist와 fail-closed ledger 생성
- #611이 credential 없이 실행할 수 있는 read-only 검증 명령과 artifact handoff

### 제외

- Kotlin production API, PaddleHTTP adapter, OCR 품질 benchmark와 adoption 결정
- `linux/arm64`, GPU/CUDA, multi-platform image
- mutable tag, first-use model download, 인증되지 않은 mirror
- 장기 PAT, registry password 또는 cloud credential 저장
- 자동 schedule/push publish와 자동 package visibility 변경
- model/image bytes, token, private registry response의 Git commit
- #609-E offline runtime acceptance와 #544 corpus 비교

## 검토한 대안

### A. 이 저장소가 소유하는 producer — 채택

workflow source, issue, ledger consumer와 trust policy를 한 저장소에서 연결한다.
`GITHUB_TOKEN`과 GitHub Actions OIDC를 사용하므로 장기 credential 없이 GHCR push와
attestation을 수행할 수 있다. #611은 repository와 workflow path를 좁게 허용하고
run ID와 digest를 직접 읽을 수 있다.

단점은 workflow 변경을 병합한 뒤에야 default branch에서 신뢰할 producer run을
만들 수 있다는 점이다. 이를 코드 병합, producer dispatch, package visibility,
consumer 검증의 네 단계 gate로 분리한다.

### B. 전용 producer 저장소 — 보류

artifact 생산 권한을 별도 저장소로 격리할 수 있지만 저장소 lifecycle, release,
cross-repository OIDC allowlist와 evidence handoff가 추가된다. 현재 한 종류의 CPU
image만 생산하므로 운영 비용이 격리 이점보다 크다. 향후 여러 저장소가 같은
producer를 공유하거나 GPU/multi-platform build가 필요해지면 다시 검토한다.

### C. upstream image/model 검증만 수행 — 거부

구축 비용은 가장 낮지만 upstream tag나 URL만으로 builder identity, package lock,
model NOTICE, 동일 subject의 SBOM/provenance를 증명하지 못한다. #638과 #609의
acceptance contract를 충족할 수 없다.

## 고정 입력과 미해결 입력

### source와 package 후보

| 입력 | 고정 값 | 근거 | 상태 |
|---|---|---|---|
| PaddleOCR | `v3.7.0` / `b03f46425e8ff4442b268ce449e3eef758146cd4` | [tag commit](https://github.com/PaddlePaddle/PaddleOCR/tree/b03f46425e8ff4442b268ce449e3eef758146cd4) | 고정 |
| PaddleX | `v3.7.2` / `ffb64904d23708863ff5b8da312a5cbd52a7f462` | [tag commit](https://github.com/PaddlePaddle/PaddleX/tree/ffb64904d23708863ff5b8da312a5cbd52a7f462) | 고정 |
| PaddlePaddle CPU | `3.2.1`, CPython 3.10, `linux_x86_64` wheel | [PaddleX CPU lock](https://github.com/PaddlePaddle/PaddleX/blob/ffb64904d23708863ff5b8da312a5cbd52a7f462/deploy/hps/server_env/requirements/cpu.txt) | 고정 |
| PaddleX CPU lock | upstream file SHA-256 `046bc0815b306f1de9995004bc3a5c9462e880ccd5ea72015871fb3245692fae` | 같은 commit의 generated lock | producer에서 재검증 |
| detector | `PP-OCRv5_mobile_det` official inference archive | PaddleOCR `v3.7.0` model list | archive SHA-256 미확정 |
| recognizer | `PP-OCRv5_mobile_rec` official inference archive | PaddleOCR `v3.7.0` model list | archive SHA-256 미확정 |
| base image | CPython 3.10 기반 `linux/amd64` OCI image | 구현 시 registry read-back | index/platform/config/base digest 미확정 |

source tag는 full commit으로 역참조한다. producer input에는 tag 이름이 아니라 full
commit, immutable archive URL과 expected SHA-256을 기록한다. upstream 문서가
`develop` 링크를 제공하더라도 allowlist에는 사용하지 않는다.

upstream PaddleX CPU lock은 compatibility resolution의 시작점이며 producer package
lock 자체가 아니다. resolution mode는 native `linux/amd64`에서 direct/transitive
Python package와 native wheel을 모두 내려받아 package name/version, immutable URL,
byte size, SHA-256, filename과 wheel platform tag를 canonical manifest로 만든다.
검토 후 commit된 manifest의 SHA-256을 `packageLockSha256`으로 사용한다. image build
단계는 verified local wheelhouse만 대상으로 hash-enforced install을 수행하며 index,
dependency resolution과 source build를 허용하지 않는다.

PaddleOCR/PaddleX source archive는 input lock의 revision·archive SHA로 검증한다.
resolution mode는 고정 build backend/toolchain과 `SOURCE_DATE_EPOCH`로 deterministic
wheel을 만들고 source ID/revision, wheel metadata, filename과 SHA-256을 candidate
package entry에 결합한다. maintainer가 그 값을 resolved lock에 commit한다. 승인된
producer run의 credential 없는 `source-repro-check` job은 source snapshot에서 wheel을
재생성해 exact hash를 확인하고 완결된 offline wheelhouse artifact를 출력한다. 뒤의
`image-build` job은 이 artifact의 run/input-lock binding과 모든 file hash를 검증한 뒤
`--no-index --no-deps --require-hashes`로만 설치하며 source tree와 build backend를
Docker context에 넣지 않는다.

### model acceptance 경계

현재 official model URL의 HTTP status, byte length와 ETag는 discovery evidence일
뿐 SHA-256 acceptance가 아니다. 구현 단계에서 통제된 staging command가 archive
bytes를 내려받고 SHA-256을 계산한다. 검토된 expected SHA-256이 allowlist에
commit되기 전까지 publish mode는 실패해야 한다.

각 model role은 다음 항목을 모두 가져야 한다.

- immutable source URL과 source revision
- archive byte size와 SHA-256
- 안전하게 압축을 푼 canonical file manifest
- `path\tbytes\tsha256\n` 행을 path순으로 직렬화한 tree SHA-256
- model pair의 고정 role 순서로 계산한 pair binding SHA-256
- artifact에 적용되는 license source와 재배포 NOTICE inventory

PaddleOCR/PaddleX source repository의 Apache-2.0 `LICENSE`는 source code의
근거다. 이를 model artifact의 전체 재배포 고지로 자동 승격하지 않는다. model별
license/NOTICE 근거가 없거나 inventory가 불완전하면 producer는 image를 게시하지
않고 `BLOCKED_LEGAL_INVENTORY`로 종료한다.

## 구성 요소

### 1. Input allowlist

repository에 machine-readable lock을 둔다. lock은 schema version, source full
commit, immutable URL, SHA-256, byte bounds, archive type, model role, license와
NOTICE source, target platform과 base image digest를 포함한다.

검증기는 unknown field, 빈 allowlist, mutable ref, `PENDING`, 중복 role, URL host
불일치와 잘못된 digest 형식을 거부한다. 허용 host도 lock과 별도 trust policy에
고정한다. URL은 HTTPS와 443 port만 허용하고 userinfo와 IP literal을 거부한다.
proxy 환경 변수는 비운다. 최초 URL과 최대 3회의 모든 redirect `Location`에 같은
scheme, port, userinfo, IP literal, normalized host allowlist 검증을 다시 적용한 뒤
각 hop의 DNS 결과를 새로 검사한다. loopback, private, link-local, multicast, reserved와 cloud metadata 대역은
거부하고 검증한 public IP에 연결을 고정하여 DNS rebinding을 막는다.

repository 경계는 다음과 같다.

| 경로 | 계약 |
|---|---|
| `docker/paddleocr/producer-input.schema.json` | strict JSON Schema와 unknown-field 거부 계약 |
| `docker/paddleocr/producer-input.lock.json` | publish가 읽는 완결된 source/base/package/model lock. `PENDING` 금지 |
| `docker/paddleocr/requirements.cpu.lock.txt` | 모든 Python artifact의 `--hash=sha256`가 있는 offline install input |
| `docker/paddleocr/trust-policy.json` | repository/workflow/ref/actor/runner/OIDC/host allowlist와 denylist source |
| `docker/paddleocr/legal-inventory.json` | code, base image, OS/Python package와 model별 license/NOTICE source/hash |
| `docker/paddleocr/producer-attempt.schema.json` | lifecycle, workflow/run identity와 모든 receipt hash의 strict contract |
| `docker/paddleocr/producer-evidence.schema.json` | image/model/legal/SBOM/provenance와 public evidence artifact의 strict contract |
| `docker/paddleocr/cleanup-fragment.schema.json` | job-local cleanup identity, target와 result receipt contract |
| `docker/paddleocr/cleanup.schema.json` | ordered job fragment merge와 aggregate cleanup contract |
| `docker/paddleocr/producer-result.schema.json` | stdout 한 줄 canonical result와 stable error contract |
| `docker/paddleocr/artifact-ledger-fragment.schema.json` | #609 producer/image/model/supply-chain subset의 strict mapping |
| `docker/paddleocr/revocations.schema.json` | revoked digest와 incident evidence strict contract |
| `docker/paddleocr/revocations.json` | 초기값 `{"schemaVersion":1,"previousDocumentSha256":null,"digests":[]}`인 hash-chained append-only deny source |
| `docker/paddleocr/emergency-deny-receipt.schema.json` | public verification failure의 digest-bound signed quarantine receipt contract |
| `docker/paddleocr/producer-reconciliation.schema.json` | attempt/release/revocation 상태와 incident closure strict contract |
| `docker/paddleocr/oci-handoff.schema.json` | image-build에서 staging-push로 넘기는 OCI layout strict binding |
| `docker/paddleocr/evidence-manifest.schema.json` | public evidence OCI의 image subject와 file inventory strict binding |
| `docker/paddleocr/Dockerfile` | credential 없는 native CPU OCI build |
| `scripts/research/paddle_ocr_producer.py` | strict input, staging, inventory, ledger/reconciliation validator CLI |
| `scripts/research/test_paddle_ocr_producer.py` | fixture와 negative test |

`producer-input.lock.json`의 root key는 `schemaVersion`, `targetPlatform`,
`baseImage`, `sources`, `packages`, `models`, `legalInventorySha256`로 제한한다.
각 non-model byte artifact는 `id`, `url`, `bytes`, `sha256`, `mediaType`을 가진다.
source에는 `revision`, package에는 `name`, `version`, `filename`, `wheelTags`를
추가한다. model은 `modelId`, `modelRevision`, `sourceId`, `role`, `url`, `bytes`,
`sha256`, `mediaType`, `archiveType`, `treeSha256`, `licenseExpression`,
`licenseSourcePath`, `licenseSha256`, `noticePath`, `noticeSha256`을 모두 요구한다.
`sourceId`는 `sources[].id`를 참조하고 `modelRevision`은 그 source의 full revision과
같아야 한다. `modelId`, `modelRevision`, source URL과 license/NOTICE field는 #609
`model.models[]`와 `licenseNotice.*`에 이름과 값의 손실 없이 복사한다. root object와 모든 nested
object는 `additionalProperties=false`다. `sources`는 PaddleOCR/PaddleX를 포함해 최소
2개이며 ID와 full revision이 unique다. `packages`는 최소 1개이고
`(name,version,filename)`이 unique다. registry artifact는 URL/bytes/SHA를, derived
wheel은 `derivedSourceId`, source revision, build toolchain과 expected wheel SHA를
`oneOf`로 요구하며 `derivedSourceId`는 `sources[].id`를 참조해야 한다. `models`는
detector와 recognizer 각 1개로 정확히 2개다.

`baseImage`는 `reference`, index/platform/config digest, `os=linux`,
`architecture=amd64`, nullable variant를 모두 요구한다. `legalInventorySha256`는
별도 inventory file의 canonical SHA와 같아야 한다. 모든 digest는 lowercase 64-hex,
bytes는 positive integer, URL은 위 allowlist contract를 만족해야 한다. schema example
fixture는 test data이고 publish input이 아니다.

값을 처음 확보하는 resolution mode는 write/OIDC credential 없이
`producer-input-candidate.json`과 wheel/model manifest를 workflow artifact로 출력한다.
candidate는 실행 lock이나 publish input이 아니며 자동 commit하지 않는다. maintainer가
source/license와 bytes를 검토해 모든 값을 확정한 뒤에만 resolved lock을 commit한다.

### 2. Ephemeral staging과 inventory

staging command는 run ID와 attempt 번호로 구분한 runner 임시 디렉터리만 사용한다.
개별 input의 byte limit은 lock에 기록하고 전체 staging은 1 GiB를 넘기지 않는다.
각 HTTP 시도는 connect/read 30초, 최대 3회와 2/4초 backoff를 적용한다. timeout,
HTTP `408`/`429`/`5xx`만 retryable이며 digest, redirect host, `4xx`, archive와 legal
inventory 오류는 permanent failure다. 각 download는 독립 `operationId`와 network
retry receipt를 만들며 다른 download/API/workflow counter와 합산하지 않는다.
download는 redirect 최종 host를 확인하며
streaming SHA-256을 계산한다. archive는 절대 경로, `..`, symlink, hard link,
device와 special file, sparse file, nested archive, normalized
duplicate path를 모두 거부한 뒤 추출한다. 압축 해제 한도는 총 1 GiB, 파일 10,000개,
path 깊이 16, UTF-8 path 240 bytes, compression ratio 100:1, 10분으로 고정한다.
추출기는 preflight에서 전체 entry를 검사하고 실제 write에서는 no-follow semantics와
staging root에 고정된 descriptor를 사용한다.

추출된 detector와 recognizer는 서로 다른 root에서 inventory를 만들고, canonical
file manifest와 role별 tree SHA-256을 계산한다. staging 결과에는 token, response
header, runner path를 넣지 않는다. `always()` finalizer는 성공, 실패와 취소에서
run-owned staging, Docker credential helper와 임시 auth file을 정리한다. cleanup
receipt에는 attempt ID, 정리 대상 종류, 결과, 원래 종료 상태와 receipt SHA-256을
기록하되 host 절대 경로는 기록하지 않는다. cleanup 실패는 원래 실패를 덮지 않고
별도 `cleanupVerified=false`로 남기며 `PRODUCER_PASS`를 금지한다.

하나의 finalizer가 다른 runner의 local state를 정리하지 않는다. validation, staging,
source-repro/image-build와 각 privileged job은 같은 job/runner의 마지막 `always()` step에서
자신이 만든 directory, Docker config/helper, temp auth와 credential file만 정리하고
`cleanup-<job>.json` fragment를 업로드한다. final `read-back/finalize`는 fragment를
strict schema로 합칠 뿐 다른 runner path를 삭제하지 않는다. hard runner loss로
fragment가 없으면 attempt는 `INTERRUPTED`, `cleanupVerified=false`로 고정하고 pass로
복구하지 않는다. 새 `RECONCILE` attempt는 registry/workflow artifact 같은 remote
state만 보상하며, GitHub-hosted ephemeral runner 폐기 이후의 local path를 존재한다고
추정하거나 fabricated cleanup receipt를 만들지 않는다.

model tree는 각 role의 runtime model root 아래 regular file만 포함하고 license와
NOTICE file은 포함하지 않는다. legal file은 `legal-inventory.json`과
`licenseNotice.inventorySha256`에서 별도로 hash한다. input lock은 model tree SHA와
legal inventory SHA를 모두 결합하므로 어느 한쪽 변경도 같은 producer input으로
취급하지 않는다. pair hash는 기존 #609의 detector/recognizer 고정 순서를 그대로
사용한다.

### 3. CPU service image

Dockerfile은 digest-pinned CPython 3.10 기반 `linux/amd64` base를 사용한다. PaddleX
generated CPU lock과 `source-repro-check`가 만든 verified wheelhouse만 설치한다.
PaddleOCR/PaddleX full commit은 input/provenance binding이며 source tree, source archive와
build backend는 Docker context와 최종 layer에 넣지 않는다. 검증된
detector/recognizer tree와 license inventory를 image에 복사한다.

첫 구현은 shared/remote BuildKit cache를 export하거나 import하지 않는다. 모델과
dependency layer가 stale input에서 재사용되는 경로를 닫는 대신 매 run의 build
시간을 receipt에 기록한다. 향후 cache를 추가하려면 source revision, package lock,
base digest와 모든 model SHA-256을 포함한 cache key와 credential/model-byte 비노출
검증을 별도 설계한다.

runtime은 non-root user를 사용하고 model 경로를 명시한다. image 안에서 model을
찾지 못했을 때 network download로 복구하지 않는다. readiness와 OCR endpoint의
구체적인 runtime 계약은 #609-E가 소유하지만, image는 network 없이 시작할 수 있는
파일 배치를 제공해야 한다.

image의 model source of truth는 image layer의
`/opt/bluetape4k/paddleocr/models/{detector,recognizer}`다. manifest와 pipeline config는
각각 `/opt/bluetape4k/paddleocr/model-manifest.json`과
`/opt/bluetape4k/paddleocr/ocr-pipeline.yaml`에 둔다. 외부 `/models` mount와 model
path override는 producer acceptance에서 거부한다.

`ENTRYPOINT`는 `["/opt/bluetape4k/bin/bluetape4k-paddleocr-service"]`, 기본 `CMD`는
`["--host","127.0.0.1","--port","8080"]`이다. wrapper는 pinned PaddleX basic-serving
app과 local pipeline config만 시작하고 `/health/ready`를 제공한다. upstream PaddleX
`/health`와 model manifest/tree 검증이 모두 성공한 뒤에만 readiness `200`을 반환한다.
model source probe와 remote fallback은 disable한다. 구현은
`paddle_ocr_smoke.py` config를 `modelSource=LEGACY_MOUNT|IMAGE`의 strict variant로
확장한다. `IMAGE` variant는 `/models` mount와 `paddlex --serve ...` override를 금지하고
config command를 `["--host","127.0.0.1","--port","8080"]`로 고정한다. 이때 최종
Docker argv는 image의 exec-form `ENTRYPOINT` 뒤에 이 세 argument만 붙인다. 기존
`LEGACY_MOUNT` variant의 external read-only mount와
`["paddlex","--serve","--pipeline","OCR",...]` command는 #545 역사적 fixture로만
유지한다. 두 variant와 cross-mode command/mount 거부를 각각 test한다.

### 4. Producer workflow

workflow는 `workflow_dispatch`만 허용하고 자동 publish trigger를 두지 않는다. input
`mode`는 `PRODUCE|RECONCILE` enum이다. `PRODUCE`는 resume field를 금지한다.
`RECONCILE`은 `resumeAttemptId`, `expectedPriorStatus`, `expectedInputLockSha256`,
`expectedStagingDigest`를 요구한다. `expectedReleaseDigest`와 `expectedEvidenceDigest`는
required string input이다. prior reconciliation에서 해당 object가 `null`이면 exact
sentinel `NONE`, 존재하면 digest를 입력한다. validator만 `NONE`을 JSON `null`로
정규화하며 빈 문자열이나 다른 sentinel을 거부한다. build, push, promotion을 실행하지
않는다.
허용 prior status는 remote staging이 존재하는 `PUBLISHED_UNVERIFIED`, `PROMOTING`,
`RELEASE_UNVERIFIED`, `QUARANTINE_PENDING`, `QUARANTINED`, `INTERRUPTED`뿐이다. staging
전 중단은 `RECONCILE` 대상이 아니며 새 `PRODUCE` attempt로 시작한다.
`develop` branch ruleset은 required CI를 통과한 PR만 병합하도록 하고 workflow,
input lock, trust policy와 producer script는 `CODEOWNERS` 보호 대상으로 둔다. 이
저장소는 solo-maintainer이므로 별도 human reviewer와 no-self-approval subgate는
`N/A`다. 대신 dispatch actor를 `debop`으로 제한하고, 외부 실행 직전 fresh approval,
exact head와 live ruleset/environment read-back을 receipt에 연결한다.

producer는 credential이 없는 validation/staging/`source-repro-check`/`image-build`,
`staging-push`, `staging-attest`, `staging-readback`, `release-promotion`,
`release-attest`, `release-readback`, `release-evidence-push`,
read-only `consumer-verify-private`, `public-visibility-readback`, credential 없는
`consumer-verify-public`, 조건부 `emergency-deny-attest`,
`release-readback/finalize` job으로
나눈다. build job은 OCI
layout과 그 SHA-256을 workflow
artifact로 넘기며 이후 job은 build script나 image entrypoint를 실행하지 않는다.
checkout은 모든 job에서 `persist-credentials: false`를 사용한다. Docker build
context, environment, build arg와 BuildKit secret에는 GitHub token이나 OIDC token을
전달하지 않는다.

각 privileged job은 token 발급 전에 다음 조건을 검증한다.

- repository가 `bluetape4k/bluetape4k-image`인가
- ref가 `refs/heads/develop`이고 checkout HEAD가 dispatch SHA와 일치하는가
- runner OS/architecture가 native `linux/amd64`인가
- input allowlist와 legal inventory가 모두 완결 상태인가
- workflow의 action이 full commit SHA로 고정되었는가
- `runs-on: ubuntu-24.04`와 `runner.environment == 'github-hosted'`인가
- dispatch actor, environment, ruleset/approval receipt가 trust policy와 일치하는가

validation/resolution은 15분, staging/build는 90분, push/attest/promotion/read-back은
각각 15분의 `timeout-minutes`를 가진다. workflow 전체 concurrency group은
`paddleocr-producer-${{ github.repository }}-linux-amd64`이고
`cancel-in-progress: false`다. 실행 중인 producer가 있으면 뒤 run은 build 전에
중복 attempt 검사를 수행하며, 동일 input-lock SHA의 이미 `PRODUCER_PASS`인 run이 있으면
새 publish를 거부한다.

visibility gate는 같은 workflow run의 protected environment에서 최대 15분 기다린다.
시간 안에 visibility 변경과 approval이 끝나면 같은 `attemptId`로 계속한다. timeout,
runner loss나 수동 중단은 그 attempt를 `INTERRUPTED`로 고정한다. 이후
`mode=RECONCILE` dispatch는 새 `attemptId`와 `replacesAttemptId`를 만들고, input lock,
release/evidence digest와 이전 attempt/evidence/reconciliation/cleanup hash를 모두
exact-match한 뒤 read-back부터 재개한다. 이전 attempt의 terminal status를 수정하거나
build/push를 다시 실행하지 않는다.

top-level permission은 `{}`로 두고 job별 최소 권한을 선언한다.

| job | permissions |
|---|---|
| validation/staging/source-repro-check | `contents: read`만 사용. write/OIDC token 없음 |
| image-build | `contents: read`, `actions: read`. 같은 run의 source-repro-check artifact만 download하고 write/OIDC token 없음 |
| staging-push | `contents: read`, `actions: read`, `packages: write`. 같은 run의 OCI handoff artifact만 download하고 OIDC token 없음 |
| staging-attest | `contents: read`, `actions: read`, `packages: read`, `attestations: write`, `id-token: write`. 같은 run의 SBOM/manifest bundle만 download하고 package write 없음 |
| staging-readback | `contents: read`, `packages: read`, `attestations: read`. write/OIDC token 없음 |
| release-promotion | `contents: read`, `packages: write`. OIDC token 없음 |
| release-attest | `contents: read`, `actions: read`, `packages: read`, `attestations: write`, `id-token: write`. 같은 run의 검증된 SBOM/manifest bundle만 download하고 package write 없음 |
| release-readback | `contents: read`, `packages: read`, `attestations: read`. write/OIDC token 없음 |
| release-evidence-push | `contents: read`, `actions: read`, `packages: write`. 같은 run의 exact evidence set만 download하고 OIDC token 없음 |
| consumer-verify-private | `contents: read`, `packages: read`. write/OIDC token 없음 |
| public-visibility-readback | `contents: read`, `packages: read`. package write 없음 |
| consumer-verify-public | `contents: read`만 사용. registry/API credential 없음 |
| emergency-deny-attest | `contents: read`, `actions: read`, `attestations: write`, `id-token: write`. exact prior attempt candidate/receipt만 download하고 package write 없음 |
| read-back/finalize | `contents: read`, `actions: read`, `packages: read`, `attestations: read`. write 권한 없음 |

artifact download consumer는 위 표의 `image-build`, `staging-push`,
`staging-attest`, `release-attest`, `release-evidence-push`, 조건부
`emergency-deny-attest`, `read-back/finalize`로 제한한다. 이 job들은
`actions: read`만 가지며 artifact upload에는 workflow runtime이 발급한 현재 run 범위
artifact service credential을 사용하므로 `actions: write`를 선언하지 않는다. download
직후 artifact ID/digest, source run ID/attempt, input-lock SHA와 producer `attemptId`를
exact-match한다. `RECONCILE` 외에는 cross-run download를 금지하고, reconciliation도
명시한 prior run ID와 저장된 artifact hash에 일치하는 artifact만 읽는다.

`release-promotion`은 `needs`로 staging digest/config/base read-back, legal inventory,
SBOM/provenance attestation, exact claim 검증과 pre-public secret scan을 모두 요구한다.
각 선행 job은 digest-bound boolean output을 내고 하나라도 false·missing이면 promotion
job 조건이 false가 아니라 명시적 failure로 끝나게 하는 gate job을 둔다. promotion은
별도 protected environment에서 실행하며 staging job의 write token을 재사용하지 않는다.
`release-evidence-push`는 검증된 bundle만 release package의 evidence OCI artifact로
추가한다. `consumer-verify-private`는 read token만으로 동일 bundle verifier를 먼저
통과시킨다. package visibility 변경은 workflow가 수행하지 않는다. owner가 별도
승인된 외부 작업으로 `public`으로 바꾼 뒤 protected `public-visibility-readback` job을
승인하면, 뒤의 `consumer-verify-public`이 새 credential 없이 anonymous pull과 local
bundle 검증을 실행한다. 이 verdict가 true인 경우에만 finalize가 `PRODUCER_PASS`를
기록한다.

private consumer 검증이 실패하면 visibility 변경을 금지하고 `RELEASE_UNVERIFIED`에서
incident를 연다. public read-back 뒤 anonymous 검증이 실패하면 먼저
`QUARANTINE_PENDING`으로 전이하고 stable tag를 만들지 않는다. finalize는 signed
receipt가 아니라 run-scoped `revocations.json` append candidate와 block verdict를
출력한다. owner가 candidate를 검토해 보호된 `develop`에 병합한 뒤 새 `RECONCILE`
attempt를 실행한다. 이 attempt의 `emergency-deny-attest`가 current revocation commit과
public image/evidence digest에 결합한 receipt를 GitHub artifact attestation으로
서명하고 read-back한다. emergency receipt의 digest/subject/current revocation/read-back이
완료된 뒤에만 `QUARANTINED`로 전이한다. owner는 별도 파괴적 승인을 받아 package visibility를
private로 되돌린 뒤 그 read-back을 incident closure 조건으로 기록한다. 이 경로에서
public digest를 `PRODUCER_PASS` 또는 accepted evidence로 광고할 수 없다.

push와 attest job은 `develop`만 허용하는 보호된 `paddleocr-producer` environment를
사용한다. environment 구성은 repository 외부 상태이므로 workflow merge와 분리해
확인한다. `ImageOS`, `ImageVersion`, `/etc/os-release`, runner environment를 기록하고
`self-hosted` label이나 예상하지 않은 hosted image면 credential 발급 전에 실패한다.
cache에는 model bytes와 credential을 저장하지 않는다. build log와 uploaded
artifact에는 authorization header나 token 값을 기록하지 않는다.

OCI handoff artifact 이름은 `paddleocr-oci-<attemptId>`이고 content는 regular file
`paddleocr-service.oci.tar`와 `handoff.json`뿐이다. tar raw bytes의 SHA-256, 최대 4 GiB,
workflow artifact ID/digest, run ID/attempt, input-lock SHA와 expected image config
digest를 `handoff.json`에 기록한다. full-SHA pinned upload/download action을 사용하고,
download 뒤 regular-file/no-symlink, size, artifact/run binding과 tar SHA를 확인한 다음
OCI layout descriptor를 검증한다. privileged job은 tar를 추출해 파일을 실행하지 않고
registry copy 도구의 OCI input으로만 읽는다.

OCI layout의 index, manifest와 모든 nested descriptor에서 `urls`와 foreign/nondistributable
media type을 거부한다. config와 layer digest마다 layout root 아래의 exact
`blobs/sha256/<64-hex>` regular file이 하나 있어야 하며 no-follow open, realpath
confinement, size와 digest를 다시 검증한다. local blob 밖 network fetch가 필요하거나
목록 밖 blob·symlink·hard link·special file이 있으면 privileged push/copy 전에
`SCHEMA_INVALID`로 실패한다.

`handoff.json`은 `oci-handoff.schema.json`을 따르며 root/nested unknown field를
거부한다. required field는 `schemaVersion=1`, artifact name/ID/digest, `runId`,
`runAttempt`, `attemptId`, `inputLockSha256`, tar path/bytes/SHA-256, image
index/platform/config/base digest와 target platform이다. tar path는 basename
`paddleocr-service.oci.tar`만 허용하고 모든 identity/digest는 attempt/evidence와
exact-match한다.

### 5. GHCR staging, promotion과 identity

build 결과는 private
`ghcr.io/bluetape4k/paddleocr-service-staging`에 exact `image-<attemptId>` tag로 먼저
push한다.
staging package는 public으로 바꾸지 않는다. scan, digest read-back, legal inventory,
SBOM과 provenance/signature 검증을 모두 통과한 digest만 registry-to-registry copy로
`ghcr.io/bluetape4k/paddleocr-service`에 promotion한다. copy는 image를 다시 만들거나
layer를 변형하지 않으며 source와 target digest equality를 확인한다.

두 package는 OCI source label로 이 저장소를 연결하고 push 주체를 이 workflow의
`GITHUB_TOKEN`으로 제한한다. consumer가 credential 없이 검증할 수 있도록 release
package의 목표 visibility는 `public`이다. 최초 package 생성과 visibility 변경은
각각 별도 외부 side-effect gate를 통과한다. release package에는 verification 전
digest를 push하지 않는다.

human-facing tag는 탐색용이며 acceptance에는 사용하지 않는다. ledger와 #611 handoff는
다음을 기록한다.

- image index digest
- `linux/amd64` platform manifest digest
- config digest와 base image digest
- package lock SHA-256
- 실제 build/run에서 관측한 platform

index가 선택한 platform manifest와 실제 실행 digest가 다르면 실패한다.

각 run은 `<run-id>.<run-attempt>` 형식의 `attemptId`, input-lock SHA-256과
nullable `replacesAttemptId`를 ledger에 기록한다. replacement는 이전 attempt의 full
ID, release/evidence digest와 attempt/evidence/reconciliation/cleanup SHA-256을 모두
기록한다. `attemptId`는 `^[1-9][0-9]*\.[1-9][0-9]*$`, image attempt tag는
`image-<attemptId>`와 `^image-[1-9][0-9]*\.[1-9][0-9]*$`, evidence attempt tag는
`evidence-<attemptId>`와 `^evidence-[1-9][0-9]*\.[1-9][0-9]*$`를 만족해야 한다.
staging과 release는 같은 `image-<attemptId>` tag를 사용하고 evidence는 같은
`evidence-<attemptId>` tag를 사용한다. tag는 caller 입력을 받지 않고 검증된 run ID와
run attempt에서 workflow가 유도한다. build 결과에는 이 immutable image attempt tag만
staging package에 붙이며 `PRODUCER_PASS` 전에는 stable tag를 만들지 않는다.
GitHub rerun처럼 같은 run ID에서 `run_attempt`만 증가해도 새 attempt로 취급하고
`replacesAttemptId`로 직전 attempt를 가리킨다. registry의 이전 image/evidence digest와
상태를 먼저 대조한다. `PUBLISHED_UNVERIFIED`, `QUARANTINED`, `REJECTED`, `FAILED`,
`CANCELLED`, `INTERRUPTED`, `REVOKED` digest는 #611 denylist에서 기계적으로
제외한다.

### 6. SBOM, provenance와 서명 검증

producer는 최종 `linux/amd64` platform manifest digest를 공통 subject로 사용한다.
SPDX JSON SBOM에는 OS와 Python package, source와 model/license inventory를 포함한다.
build provenance에는 repository, workflow path/ref, run ID, source commit, builder
identity와 재현 가능한 입력 digest를 포함한다.

GitHub artifact attestation action은 full commit SHA로 고정한다. provenance와 SBOM
attestation은 GitHub Actions OIDC로 서명된다. 초기 설계에는 별도 장기 Cosign key를
추가하지 않는다. `gh attestation verify` 결과에서 signer identity, OIDC issuer,
repository와 workflow를 추출하여 #609 ledger의 signature receipt로 정규화한다.

trust policy는 issuer, audience, certificate subject, repository, workflow path와
workflow file commit SHA, ref, environment, run ID, run attempt, head SHA를 exact-match
한다. 이 값은 GitHub run/environment API 결과와 양방향으로 대조한다. provenance와
SBOM attestation을 각각 검증하고 machine-readable JSON만 입력으로 받는다. attestation
누락·복수의 서로 다른 subject·malformed output·non-zero exit·claim 불일치는 모두
`REJECTED`다.

SBOM file hash가 맞더라도 attestation subject가 image digest와 다르면 실패한다.
workflow path, repository, signer 또는 issuer가 non-empty allowlist에 없으면
`verification.trustPolicyMatched=false`로 기록하고 `PRODUCER_PASS`로 만들지 않는다.

attestation read-back은 `attestations: read` permission과
`gh api repos/bluetape4k/bluetape4k-image/attestations/sha256:<digest>`의 JSON,
그리고 producer read token을 사용하는 `gh attestation verify`를 함께 실행한다. API `200`과 유일한 expected
subject/claim set만 성공이다. `401`/`403`은 `BLOCKED`, `404`는 missing attestation,
`429`/`5xx`는 bounded retry 대상이며 malformed/mismatched response는 `REJECTED`다.
각 GitHub API와 registry metadata 요청은 connect 10초, 전체 read 30초, response body
2 MiB로 제한한다. attestation bundle API만 4 MiB를 허용한다. paginated endpoint는
page당 100개, 최대 20 page/40 MiB이며 한 page씩 parse해 expected match만 보존하고
전체 응답을 `--slurp`하거나 메모리에 합치지 않는다. 20번째 page 뒤에도 `next` link가
있거나 body/time limit을 넘으면 `FAILED`/`REMOTE_LIMIT_EXCEEDED`로 fail closed한다.
malformed JSON, duplicate match와 schema mismatch는 `REJECTED`다.

private staging은 `staging-attest`가 provenance/SBOM attestation을 만들고, 별도
`staging-readback`이 `packages: read`, `attestations: read`만으로 subject와 claim을
확인한다. promotion 뒤 `release-attest`는 같은 digest와 release package subject-name으로
새 attestation을 만들고, 별도 `release-readback`이 write/OIDC token 없이 검증한다. #611은 public evidence OCI bundle을 anonymous
pull하고 raw platform manifest와 local bundle을 `gh attestation verify --bundle`에
입력한다. registry login, package read token이나 staging claim이 필요하면 consumer
gate는 실패한다.

### 7. Producer ledger와 #611 handoff

producer는 `producer-attempt.json`, `producer-evidence.json`, `reconciliation.json`,
`cleanup.json`, `artifact-ledger.fragment.json`, `result.json`을 출력한다. 각 문서는
대응하는 repository schema를 통과해야 하며 root와 모든 nested object에서
`additionalProperties=false`를 사용한다.

- `producer-attempt.schema.json`은 `schemaVersion=1`, `attemptId`, `producerStatus`,
  `lastCompletedStage`, `inputLockSha256`, `policySha256`, repository/workflow path, `workflowRef`, `workflowSha`,
  `ref`, `headSha`, decimal `runId`, positive `runAttempt`, actor, runner identity,
  environment, nullable `replacesAttemptId`, 이전 attempt의 release/evidence와
  attempt/evidence/reconciliation/cleanup hash, 단계별 RFC3339 timestamp, 현재
  evidence/reconciliation/cleanup/fragment SHA와 revocation binding을 모두 요구한다.
  schema artifact는 finalizer가 모든 companion failure-mode document를 만든 뒤에만
  출력하므로 현재 document SHA 네 개는 모든 상태에서 non-null lowercase 64-hex다.
- `producer-evidence.schema.json` root는 `schemaVersion=1`, `attemptId`,
  `producerStatus`, `lastCompletedStage`, `inputLockSha256`, `staging`, `release`,
  `payload`, `evidenceSubjectDigest`를 모두 required로 둔다. `payload` object는 package
  lock, detector/recognizer exact pair, legal inventory, JCS SPDX document와 두
  attestation descriptor를 요구한다. 각 attestation descriptor는 exact
  `attestations/provenance.bundle.jsonl` 또는 `attestations/sbom.bundle.jsonl` path,
  bytes, raw SHA-256, predicate type, subject digest, signer/issuer와 `verified`를 가진다.
  별도 signature file은 두 signed bundle과 그 검증 field가 대체한다.
  `evidence-manifest.json`의 hash, outer OCI manifest digest와 acceptance ref는 이
  문서에 넣지 않는다. 각 path는 bundle-relative canonical path이고 byte size와
  SHA-256을 가진다.
- `cleanup-fragment.schema.json`은 `schemaVersion=1`, attempt ID, workflow job ID,
  original producer status, 시작/종료 timestamp와 target array를 요구한다.
  target은 `kind`, host path 대신 logical ID의 SHA-256, `REMOVED|ABSENT|FAILED` result만
  가진다. cleanup fragment 자체에는 self hash field를 두지 않는다. aggregate
  `cleanup.json.fragments[].sha256`만 RFC 8785 canonical fragment bytes의 authoritative
  hash다. job ID와 `(kind,idSha256)` 중복, unknown field와 secret/path value를 거부한다.
- `cleanup.schema.json`은 attempt의 `startedJobIds`, fragment hash와 aggregate result를
  요구한다. fragment는 validation, staging, source-repro-check, image-build,
  staging-push, staging-attest, staging-readback, release-promotion, release-attest,
  release-readback, release-evidence-push, consumer-verify-private,
  public-visibility-readback, consumer-verify-public, emergency-deny-attest 순서로 정렬하고 started job마다
  정확히 하나만 허용한다. 서로 다른 fragment의 같은 target ID는 거부한다.
  `fragments[]`는 job ID, bundle-relative `cleanup-fragments/<job-id>.json`, bytes와
  SHA-256을 가지며 위 순서와 cardinality를 그대로 따른다. `verify-attempt --cleanup`은
  cleanup file의 parent 아래에서 이 regular file을 no-follow로 다시 열고 path/hash를
  재계산한다. absolute/parent path, symlink와 목록 밖 extra fragment를 거부한다.
  `cleanupVerified=true`는 모든 started job fragment가 있고 모든 target이
  `REMOVED|ABSENT`이며 fragment hash와 original status가 일치할 때만 허용한다.
- `producer-result.schema.json`은 아래 stdout 한 줄 JSON을 검증한다. attempt, evidence,
  reconciliation, cleanup, fragment는 서로의 canonical SHA-256과 같은 `attemptId`,
  `inputLockSha256`, release platform digest를 가져야 한다. 하나라도 다르면
  `SCHEMA_INVALID`로 실패한다. result의 `evidenceManifestDigest`, reconciliation의
  `evidence.manifestDigest`, registry outer OCI descriptor digest와 acceptance ref의
  digest는 모두 exact-match하고, evidence subject는 release platform digest와 같아야
  한다.

validator CLI는 repository root에서 다음 preamble과 경로를 사용한다. angle bracket를
shell에 그대로 넣지 않는다. caller는 먼저 환경 변수를 실제 값으로 export하고 빈 값
검사를 통과시킨다.

```bash
REPO_ROOT="$(git rev-parse --show-toplevel)"
ARTIFACT_DIR="${ARTIFACT_DIR:?export ARTIFACT_DIR to the extracted evidence directory}"
ATTEMPT_ID="${ATTEMPT_ID:?export ATTEMPT_ID as run-id.run-attempt}"

python3 "$REPO_ROOT/scripts/research/paddle_ocr_producer.py" validate-inputs \
  --lock "$REPO_ROOT/docker/paddleocr/producer-input.lock.json" \
  --policy "$REPO_ROOT/docker/paddleocr/trust-policy.json" \
  --legal "$REPO_ROOT/docker/paddleocr/legal-inventory.json"

python3 "$REPO_ROOT/scripts/research/paddle_ocr_producer.py" verify-attempt \
  --attempt "$ARTIFACT_DIR/producer-attempt.json" \
  --evidence "$ARTIFACT_DIR/producer-evidence.json" \
  --reconciliation "$ARTIFACT_DIR/reconciliation.json" \
  --cleanup "$ARTIFACT_DIR/cleanup.json" \
  --ledger-fragment "$ARTIFACT_DIR/artifact-ledger.fragment.json" \
  --revocations "$REPO_ROOT/docker/paddleocr/revocations.json"
```

stdout은 `producer-result.schema.json`을 따르는 canonical JSON 한 줄만 쓴다. required
key는 `schemaVersion=1`, `attemptId`, `producerStatus`, `lastCompletedStage`, `mapped609Status`,
`imagePlatformDigest`, `evidenceManifestDigest`, `reconciliationSha256`,
`ledgerFragmentSha256`, `revocationsSha256`, `errorCode`, `errorMessage`다. 성공은
`producerStatus=PRODUCER_PASS`, `mapped609Status=PENDING`, non-null digest/hash,
`errorCode=NONE`, `errorMessage=null`이다. 실패도 finalizer가 failure-mode
evidence/reconciliation/ledger document를 먼저 확정하므로 세 document hash는 항상
제공한다. image/evidence artifact digest만 `lastCompletedStage`가 검증한 범위까지
제공하고 stable non-`NONE` error code와 secret/path를 제거한 512-byte 이하 message를
가진다. diagnostic log는 stderr로만 쓴다. unknown/duplicate field, trailing data,
oversize document와 `PENDING` executable input은 거부한다.

repository가 작성하는 input/policy/legal lock, attempt, evidence, cleanup과 fragment,
ledger fragment, reconciliation, result, revocations, emergency receipt,
`evidence-manifest.json`, package/model manifest와 SPDX document는 UTF-8, BOM 없음,
[RFC 8785 JSON Canonicalization Scheme](https://www.rfc-editor.org/rfc/rfc8785)으로
직렬화한다. parser는 duplicate key와 schema 밖 number를 거부하고 numeric field는 JSON
integer만 사용한다. SHA-256 대상은 trailing newline 없는 exact JCS bytes다.
descriptor가 payload hash를 보유할 때는 descriptor 밖 payload만 hash하고 self hash를
payload에 두지 않는다.

registry에서 받은 `platform-manifest.json`과 GitHub가 서명한 두 attestation JSONL은
opaque byte-preserved evidence다. platform manifest는 registry가 digest를 계산한 raw
bytes를 그대로 보존하며 그 raw SHA-256이 platform digest와 같아야 한다. JSONL은
UTF-8/LF, BOM·CR·trailing blank line 없음과 한 줄당 하나의 유효한 signed envelope를
검사하되 parse 후 재직렬화하지 않는다. descriptor의 bytes/SHA-256은 저장된 raw file
전체를 대상으로 한다. `evidence-manifest.json.files[]`는 JCS 문서는 JCS bytes,
opaque 문서는 raw bytes의 hash를 기록한다. 구현 test는 JCS golden vector와 동치 JSON,
duplicate key 거부, raw manifest byte 변경, JSONL CRLF/reorder/re-serialization이 hash와
signature 검증을 깨뜨리는 fixture를 포함한다.

네 lifecycle schema는 root key를 모두 `required`로 두며 absent field를 허용하지 않는다.
`lastCompletedStage`는 `NONE|STAGING|EVIDENCE|RELEASE|PUBLIC_EVIDENCE`이고 `allOf`의
`if`/`then`과 exhaustive `oneOf`가 다음 null/non-null 계약을 강제한다.

| stage | evidence `staging` | evidence `payload` | evidence `release`/`evidenceSubjectDigest` | reconciliation `staging`/`release`/`evidence` | result image/evidence digest |
|---|---|---|---|---|---|
| `NONE` | `null` | `null` | 둘 다 `null` | 모두 `null` | 둘 다 `null` |
| `STAGING` | non-null | `null` | 둘 다 `null` | staging만 non-null | 둘 다 `null` |
| `EVIDENCE` | non-null | non-null | 둘 다 `null` | staging만 non-null | 둘 다 `null` |
| `RELEASE` | non-null | non-null | 둘 다 non-null | staging/release non-null, evidence `null` | image non-null, evidence `null` |
| `PUBLIC_EVIDENCE` | non-null | non-null | 둘 다 non-null | 모두 non-null | 둘 다 non-null |

`VALIDATING`, `BUILDING`, `BLOCKED_INPUT`, `BLOCKED_LEGAL_INVENTORY`는 `NONE`,
`PUBLISHED_UNVERIFIED`는 `STAGING`, `PROMOTING`은 `EVIDENCE`, `PRODUCER_PASS`,
`QUARANTINE_PENDING`, `QUARANTINED`는 `PUBLIC_EVIDENCE`만 허용한다.
`RELEASE_UNVERIFIED`는 registry read-back에 따라 `RELEASE|PUBLIC_EVIDENCE`다.
`REJECTED|REVOKED|FAILED|CANCELLED|INTERRUPTED`는 어느 stage든 가능하지만 위 stage
row를 정확히 따라야 한다. attempt/evidence/reconciliation/result의 status, stage,
package 존재성과 digest는 exact-match한다. attempt의 companion document SHA와 result의
`reconciliationSha256`, `ledgerFragmentSha256`, `revocationsSha256`는 모든 stage에서
non-null이며 failure-mode document가 없거나 hash가 다르면 result를 출력하지 않는다.

`producerStatus=PRODUCER_PASS`에서만 `errorCode=NONE`, `errorMessage=null`을 허용한다.
나머지 상태는 stable non-`NONE` error code를 요구한다. schema는 위 표에 없는
null/non-null 조합, result와 reconciliation의 release/evidence 존재성 불일치와 current
document hash가 `null`인데 그 문서를 참조하는 조합을 거부한다.

`artifact-ledger.fragment.json`은
`docker/paddleocr/artifact-ledger-fragment.schema.json`을 따르며 attempt/evidence에
기록된 fragment SHA-256과 일치해야 한다. `revocations.json` root는
`schemaVersion`, `previousDocumentSha256`, `digests`만 허용한다. 각 entry는
`incidentId`, `artifactKind=IMAGE|EVIDENCE`, 단일 digest, reason code, incident URL,
issued/acknowledged timestamp와 signer를 가진다. `(artifactKind,digest)`와
`(incidentId,artifactKind)`는 unique다. 한 incident가 public evidence를 quarantine하면
같은 `incidentId`의 IMAGE와 EVIDENCE entry가 정확히 하나씩 있어야 한다. 초기 empty
document만 `previousDocumentSha256=null`을 허용한다. 이후 revision은 직전 canonical
document SHA-256을 parent로 기록하고 이전 digest entry를 순서까지 그대로 포함한 뒤
새 unique entry만 append한다.

attempt/evidence/reconciliation은 `revocationsSha256`, 이 file을 읽은 40-hex
`revocationsCommitSha`, `previousDocumentSha256`을 함께 기록한다. verifier는 exact
commit의 Git blob을 canonical JSON으로 다시 hash하고 parent chain에서 entry
제거·변경·재정렬이 없음을 확인한다. file/hash/commit/parent mismatch,
unknown/duplicate field와 같은 digest의 상충 entry는 실패한다.

권위 있는 revocation source는
`bluetape4k/bluetape4k-image@refs/heads/develop:docker/paddleocr/revocations.json`으로
고정한다. producer finalize와 #611 verifier는 검증 시점의 protected `develop` HEAD를
credential 없는 public HTTPS fetch로 fresh read-back한다. evidence의
`revocationsCommitSha`는 그 HEAD와 같거나 local `git merge-base --is-ancestor`를
통과해야 한다. verifier는 evidence commit부터 current HEAD까지 이 file의 모든 변경
commit과 JCS parent hash를 순서대로 읽어 append-only chain을 검증하고, current
document에서 image/evidence digest가 revoked되지 않았음을 확인한다. ancestor가 아닌
commit, 누락된 중간 revision, force-push 흔적, current blob/hash
불일치와 stale chain을 모두 `REJECTED`로 처리한다. evidence에 담긴 과거 snapshot의
self-consistency만으로 acceptance하지 않는다.

`emergency-deny-receipt.schema.json`은 `schemaVersion=1`, image/evidence digest,
incident URL, reason code, repository, workflow path/ref/SHA, run ID/attempt, signer
identity, OIDC issuer/audience, current protected `develop` HEAD,
`revocationsCommitSha`, `revocationsSha256`, issued timestamp와 `revocationEntries`를
모두 required로 두고 unknown field를 거부한다. current revocation document에는 두
digest와 incident가 exact-match하는 append entry가 있어야 한다. `revocationEntries`는
IMAGE와 EVIDENCE 각 하나의 `artifactKind`, digest와 해당 canonical entry SHA-256을
가진 정확히 두 원소의 array이며 current document의 같은 `incidentId` entry와
exact-match한다. receipt JCS bytes의
SHA-256을 attestation subject로 삼아 GitHub provenance bundle을 만들고, read-only
verifier가 producer와 같은 exact signer/OIDC/repository/workflow policy로 검증한다.
current branch·revocation blob·receipt subject·signature bundle 중 하나라도 다르면
`QUARANTINE_PENDING`에 남긴다. `QUARANTINED`와 incident closure는 이 receipt의
`verified=true`와 `denylistVerified=true`를 모두 요구한다.

fragment와 #609 canonical field mapping은 다음과 같다. `workflowRef`는 workflow file
SHA가 아니라 GitHub/OIDC full branch ref인
`bluetape4k/bluetape4k-image/.github/workflows/paddleocr-producer.yml@refs/heads/develop`다.
workflow file content commit은 `workflowSha`, dispatch checkout은 `headSha`, branch는
`ref=refs/heads/develop`, 실행 식별자는 decimal `runId`와 `runAttempt`로 분리한다.
fragment는 이 필드를 모두 보존하고 #609 기존 `producer.workflowRunId`에는
`<runId>.<runAttempt>`, `producer.sourceRevision`에는 `headSha`를 넣는다.

| fragment source | #609 field | canonical rule |
|---|---|---|
| run repository/workflow | `producer.repository`, `producer.workflow` | `bluetape4k/bluetape4k-image`, `.github/workflows/paddleocr-producer.yml` exact match |
| workflow branch | `producer.workflowRef` | full workflow path와 `refs/heads/develop`; `trustPolicy.allowedWorkflowRefs` exact membership |
| workflow content | `producer.workflowSha` | 실행된 workflow file을 포함한 40-hex commit; `allowedWorkflowShas` exact membership |
| dispatch checkout | `producer.ref`, `producer.sourceRevision` | `refs/heads/develop`, 40-hex `headSha` exact match |
| run identity | `producer.workflowRunId`, `producer.runAttempt` | decimal run ID와 positive attempt; compatibility 값은 `<runId>.<runAttempt>` |
| hosted runner/OIDC | `producer.builderIdentity`, `producer.signerIdentity`, `producer.oidcIssuer` | trust-policy exact claim values |
| release package/digest | `image.imageRef`, `image.imageIndexDigest`, `image.platformManifestDigest` | `ghcr.io/bluetape4k/paddleocr-service@sha256:<digest>`와 release read-back digest |
| OCI config/base/platform | `image.configDigest`, `image.baseImageDigests`, `image.os`, `image.architecture`, `image.variant` | registry descriptor와 input lock equality |
| package manifest | `image.packageLockSha256` | complete package artifact manifest canonical SHA |
| role manifests | `model.models[]`, `model.pairBindingSha256`, `licenseNotice.*` | detector/recognizer exact cardinality와 recomputed hash |
| attestation bundles | `sbom.*`, `provenance.*`, `signature.*` | release platform digest 공통 subject, verified=true |
| trust policy | `trustPolicy.*` | non-empty allowlist와 recomputed policy SHA |

fragment에는 #609 `execution.observed.*`, smoke, no-egress와 cleanup acceptance를 넣지
않는다. fragment status는 `PRODUCER_PASS|BLOCKED|REJECTED`이며 최종 #609
`ledger.status`로 복사하지 않는다. 기존 `paddle_ocr_receipt.py`는 #545/#609-E service
runtime receipt 전용이다. #609-E가 실행될 때 fragment digest와 service receipt hash를
최종 #609 ledger에 함께 결합한다.

evidence OCI의 file allowlist는 `producer-evidence.json`,
`artifact-ledger.fragment.json`, `inputs/producer-input.lock.json`,
`manifests/package-lock.json`, `manifests/model-detector.json`,
`manifests/model-recognizer.json`, `platform-manifest.json`, `sbom.spdx.json`,
`legal-inventory.json`, `attestations/provenance.bundle.jsonl`과
`attestations/sbom.bundle.jsonl`의 정확히 11개로 닫는다. 이 non-secret file만 release package와 같은 OCI repository의 evidence artifact로
게시한다. `artifactType`은
`application/vnd.bluetape4k.paddleocr.producer-evidence.v1`, OCI `subject.digest`는 release
platform manifest digest다. acceptance ref는 tag가 아니라
`ghcr.io/bluetape4k/paddleocr-service@sha256:<evidence-manifest-digest>`이며 producer
result의 `evidenceManifestDigest`와 같아야 한다. `evidence-manifest.json`은 bundle file
목록, size/SHA-256, image subject, attempt/run/input-lock/revocations binding을 가진다.
이 문서는 `evidence-manifest.schema.json`을 따르며 `schemaVersion=1`, `artifactType`,
image index/platform/config/base digest, subject digest,
run/attempt/input-lock/revocations binding과 canonical relative path·positive bytes·SHA-256의
unique file array를 모두 요구한다. 자기 자신은 file array에서 제외하고 OCI manifest가
그 bytes를 별도 layer로 결합한다. outer OCI manifest digest는 이 JSON 안에 넣지 않고
producer result와 reconciliation에서만 기록하여 순환 참조를 막는다.
`producer-attempt.json`, `reconciliation.json`, `cleanup.json`, cleanup fragment와
`result.json`은 outer digest 또는 그 digest를 가진 문서의 hash를 포함할 수 있으므로
evidence OCI layer와 `evidence-manifest.json.files[]`에서 반드시 제외한다. 이 문서는
evidence push 전에 닫고, post-publication result/reconciliation은 workflow artifact와
#609 ledger handoff로 별도 보존한다. root/nested
unknown field, 절대·상위 path와 중복 path를 거부한다.

`evidence-manifest.json`은 1 MiB 이하, 각 allowlist file은 128 MiB 이하, 11개 file
합계는 512 MiB 이하로 제한한다. path depth는 3, UTF-8 path는 240 bytes를 넘지
않는다. OCI manifest는 1 MiB 이하이고 evidence manifest를 포함해 최대 16개 local
layer descriptor와 총 513 MiB만 허용한다. 모든 layer는 허용 media type, lowercase
digest, positive bounded size와 exact `org.opencontainers.image.title`을 가져야 하고
`urls`, foreign/nondistributable layer와 duplicate title/digest를 거부한다. OCI config
descriptor도 `urls`가 없어야 하고
`application/vnd.bluetape4k.paddleocr.evidence.config.v1+json`, 64 KiB 이하의
uncompressed canonical JSON, expected digest로 고정한다. public
evidence manifest layer의 media type은
`application/vnd.bluetape4k.paddleocr.evidence.manifest.v1+json`, 나머지 11개 file
layer는 `application/vnd.bluetape4k.paddleocr.evidence.file.v1`로 고정한다. 두 media
type은 uncompressed raw blob이며 descriptor `size`는 materialized file bytes와 같아야
한다. gzip/zstd/tar media type, compression/encoding annotation과 layer 안 archive를
거부하므로 descriptor preflight의 513 MiB가 expanded disk 상한과 같다. public
consumer는 blob을 받기 전에 remote OCI manifest만 60초 안에 fetch해 config와 layer
descriptor의 count·size·media type·title·subject·`urls` 부재를 preflight한다. 초과나
mismatch는 blob download 없이 `QUARANTINE_PENDING`으로 fail closed한다. 직접
`oras pull --output`을 사용하지 않는다. validator materializer가 allowlist layer를
하나씩 anonymous `oras blob fetch`로 streaming하고, fresh root directory descriptor에
canonical relative path의 각 component를 `openat` 계열로 순회한다. empty, `.`, `..`와
allowlist 밖 component를 거부하고, 선언된 `inputs`, `manifests`, `attestations` parent만
mode `0700` `mkdirat`으로 만든 뒤 각 directory를 `O_DIRECTORY|O_NOFOLLOW`로 다시
연다. final file은 parent directory fd 기준 `O_NOFOLLOW|O_CREAT|O_EXCL`, mode `0600`으로
열어 root-pinned regular file에만 쓴다.
write 중 descriptor size와 총 513 MiB를 강제하고 완료 전 temp file은 verifier 입력에
노출하지 않는다. 각 file의 bytes/digest/title을 확인한 뒤 같은 parent fd 안에서
no-replace atomic rename하며 10분
budget 안에 정확한 11개 file과 evidence manifest만 완성한다.
`release-evidence-push`가 중단되면 reconciliation은 `evidence-<attemptId>` tag를 모든
page에서 조회한다. 0건은 `RELEASE_UNVERIFIED`, 유일한 expected manifest/subject는
read-back 재개, 2건 이상·다른 subject/artifact type/file manifest는 `REJECTED`다.
evidence read-back이 완료되기 전에는 package visibility 변경을 금지한다.

#611은 public package에서 write credential이나 registry login 없이 digest-pinned bundle을
받는다. `consumer-verify-public`은 ORAS `v1.3.4` linux/amd64 archive URL과 SHA-256
`f27adb935022d94df8dc77719c322dda592c78a0d57a6f7dcdd8d900b248c454`를 tool allowlist에
고정하고 검증된 archive에서 꺼낸 binary의 절대 경로를 `ORAS_BIN`으로 사용한다.
workflow artifact는 사고 조사용 fallback일 뿐 AC-08 증거가 아니다.

고정 download URL은
[`https://github.com/oras-project/oras/releases/download/v1.3.4/oras_1.3.4_linux_amd64.tar.gz`](https://github.com/oras-project/oras/releases/download/v1.3.4/oras_1.3.4_linux_amd64.tar.gz)다.
consumer workflow는 이 URL만 30초와 8 MiB download limit으로 fresh mode `0700`
tool directory에 내려받고 다음 archive check를 통과한 뒤 추출 전 entry를 검사한다.
archive는 basename `oras` regular file 하나와 허용된 text file만 포함해야 하며 절대·상위
path, duplicate, symlink, hard link, special/sparse file과 16 MiB 초과 expanded size를
거부한다. no-follow descriptor로 쓰고 extraction root 밖 realpath를 거부한다.

```bash
ORAS_ARCHIVE="${ORAS_ARCHIVE:?export the downloaded ORAS archive path}"
printf '%s  %s\n' \
  f27adb935022d94df8dc77719c322dda592c78a0d57a6f7dcdd8d900b248c454 \
  "$ORAS_ARCHIVE" | sha256sum --check --strict
```

```bash
EVIDENCE_REF="${EVIDENCE_REF:?export digest-pinned public evidence OCI ref}"
ORAS_TOOL_DIR="${ORAS_TOOL_DIR:?export the fresh ORAS extraction directory}"
ORAS_BIN="${ORAS_BIN:?export the absolute path to verified ORAS v1.3.4}"
case "$ORAS_BIN" in /*) ;; *) exit 64 ;; esac
test -x "$ORAS_BIN" || exit 64
test -f "$ORAS_BIN" && test ! -L "$ORAS_BIN" || exit 64
ORAS_TOOL_ROOT="$(realpath "$ORAS_TOOL_DIR")"
ORAS_REAL="$(realpath "$ORAS_BIN")"
case "$ORAS_REAL" in "$ORAS_TOOL_ROOT"/oras) ;; *) exit 64 ;; esac
"$ORAS_BIN" version | grep -Eq '^Version:[[:space:]]+1\.3\.4$' || exit 64
printf '%s\n' "$EVIDENCE_REF" |
  grep -Eq '^ghcr\.io/bluetape4k/paddleocr-service@sha256:[0-9a-f]{64}$' || exit 64
EVIDENCE_DIR="$(mktemp -d "${RUNNER_TEMP:-/tmp}/paddleocr-evidence.XXXXXX")"
chmod 0700 "$EVIDENCE_DIR"
OCI_MANIFEST="$(mktemp "${RUNNER_TEMP:-/tmp}/paddleocr-remote-manifest.XXXXXX.json")"
timeout 60s "$ORAS_BIN" manifest fetch "$EVIDENCE_REF" --output "$OCI_MANIFEST"
python3 "${REPO_ROOT:?export repository root}/scripts/research/paddle_ocr_producer.py" \
  verify-remote-evidence \
  --manifest "$OCI_MANIFEST" \
  --max-manifest-bytes 1048576 \
  --max-layers 16 \
  --max-layer-bytes 134217728 \
  --max-total-bytes 537919488
timeout 10m python3 "$REPO_ROOT/scripts/research/paddle_ocr_producer.py" \
  materialize-remote-evidence \
  --oras-bin "$ORAS_BIN" \
  --ref "$EVIDENCE_REF" \
  --manifest "$OCI_MANIFEST" \
  --root "$EVIDENCE_DIR" \
  --max-total-bytes 537919488
rm -f "$OCI_MANIFEST"

COMMON_ATTEST_ARGS=(
  --repo bluetape4k/bluetape4k-image
  --signer-workflow bluetape4k/bluetape4k-image/.github/workflows/paddleocr-producer.yml
  --signer-digest "${WORKFLOW_SHA:?export the allowlisted workflow SHA}"
  --source-digest "${HEAD_SHA:?export the producer head SHA}"
  --source-ref refs/heads/develop
  --cert-oidc-issuer https://token.actions.githubusercontent.com
  --deny-self-hosted-runners
  --format json
)
gh attestation verify "$EVIDENCE_DIR/platform-manifest.json" \
  --bundle "$EVIDENCE_DIR/attestations/provenance.bundle.jsonl" \
  --predicate-type https://slsa.dev/provenance/v1 \
  "${COMMON_ATTEST_ARGS[@]}" > "$EVIDENCE_DIR/provenance.verify.json"
gh attestation verify "$EVIDENCE_DIR/platform-manifest.json" \
  --bundle "$EVIDENCE_DIR/attestations/sbom.bundle.jsonl" \
  --predicate-type https://spdx.dev/Document/v2.3 \
  "${COMMON_ATTEST_ARGS[@]}" > "$EVIDENCE_DIR/sbom.verify.json"
```

anonymous ORAS manifest/blob fetch의 timeout, connection reset, `429`와 `5xx`, 그리고 API가 이미
`public`을 확인한 직후의 `401|403|404`는 `REGISTRY` transient로 분류해 최대 3회와
2/4초 backoff만 적용한다. 세 번 뒤에도 같으면 `QUARANTINE_PENDING`이다. digest,
artifact type, subject, file hash mismatch와 두 local attestation verifier의 non-zero는
permanent verification failure라 retry하지 않고 즉시 `QUARANTINE_PENDING`으로 간다.

`platform-manifest.json` raw byte SHA-256은 image platform digest와 같아야 한다. 위
명령의 JSON은 `verify-attempt`가 certificate subject, repository, workflow SHA/ref,
environment, run ID/attempt, head SHA와 unique predicate/subject를 다시 exact-match한다.
그 뒤 #611은 GHCR image descriptor와 model/tree/pair/legal hash를 재계산하고 positive/
negative verifier suite를 실행한다. workflow artifact fallback은 owner가
`gh run download "$RUN_ID" --name "paddleocr-evidence-$ATTEMPT_ID" --dir
"$ARTIFACT_DIR"`로 진단할 때만 사용한다.

하나라도 재현되지 않으면 #611은 #609-C를 열지 않는다. producer pass invariant에는
`secretScan == PASS`, `cleanupVerified == true`, staging/release digest equality,
revocation chain과 exact OIDC/run claim match를 포함한다. 각 JSON, fragment와 evidence
manifest digest는 job summary와 #611 handoff에 기록한다.

## 상태 전이와 실패 처리

아래 값은 `producer-attempt.schemaVersion=1`의 `producerAttempt.status`이며 #609
`ledger.status`가 아니다. #609 schema는 변경하지 않는다. #638가 성공하면
producer attempt만 `PRODUCER_PASS`가 되고 #609 ledger는 offline smoke와 cleanup/no-
egress receipt가 추가될 때까지 `PENDING` 또는 `BLOCKED`다.

| producer status | result error / exit | #609 mapped status | 의미와 후속 |
|---|---|---|---|
| `VALIDATING` | terminal result 없음 | `PENDING` | allowlist와 source/legal evidence 검사 중, publish 금지 |
| `BLOCKED_INPUT` | `BLOCKED_INPUT` / 10 | `BLOCKED` | digest, ref, host 또는 lock 수정 후 새 commit에서 재실행 |
| `BLOCKED_LEGAL_INVENTORY` | `BLOCKED_LEGAL_INVENTORY` / 11 | `BLOCKED` | model/container license 또는 NOTICE 보강 전 publish 금지 |
| `BUILDING` | terminal result 없음 | `PENDING` | native `linux/amd64` build 중, staging 외부 bytes 금지 |
| `PUBLISHED_UNVERIFIED` | `PARTIAL_PUBLICATION` / 20 | `PENDING` | private staging read-back/attestation 전 promotion 금지 |
| `PROMOTING` | `PARTIAL_PUBLICATION` / 20 | `PENDING` | target equality read-back 전 consumer 사용 금지 |
| `RELEASE_UNVERIFIED` | `PARTIAL_PUBLICATION` / 20 | `PENDING` | release 검증 전 stable tag와 consumer handoff 금지 |
| `QUARANTINE_PENDING` | `PARTIAL_PUBLICATION` / 20 | `BLOCKED` | public 검증 실패, emergency deny receipt read-back 전 사용 금지 |
| `QUARANTINED` | `QUARANTINED` / 21 | `BLOCKED` | 접근 주체 제한, 조사와 rotation runbook 실행 |
| `PRODUCER_PASS` | `NONE` / 0 | `PENDING` | producer evidence 완료, #611 read-only gate로 전달 |
| `REJECTED` | `REJECTED` / 22 | `REJECTED` | 해당 digest 폐기, 같은 tag 재사용 금지 |
| `REVOKED` | `REVOKED` / 23 | `REJECTED` | #611 denylist와 revocation receipt 우선 적용 |
| `FAILED` | `FAILED` / 30 | `BLOCKED` | finalizer 후 원인과 cleanup 결과 보존 |
| `CANCELLED` | `CANCELLED` / 31 | `BLOCKED` | finalizer 실행, `PRODUCER_PASS` 전이 금지 |
| `INTERRUPTED` | `INTERRUPTED` / 32 | `BLOCKED` | run API와 registry reconciliation 전 재시도 금지 |

schema parse/contract 실패는 producer status를 `BLOCKED_INPUT`, result error를
`SCHEMA_INVALID`, exit을 40, #609 mapped status를 `BLOCKED`로 기록한다. 위 표와 이
schema-error rule이 status/error/exit mapping의 유일한 source of truth다.

상태는 append-only attempt ledger에서 `VALIDATING -> BUILDING ->
PUBLISHED_UNVERIFIED -> PROMOTING -> RELEASE_UNVERIFIED -> PRODUCER_PASS` 방향으로만
진행한다. 어느 단계에서든
`BLOCKED_*`, `QUARANTINED`, `REJECTED`, `FAILED`, `CANCELLED`, `INTERRUPTED`로 끝날 수
있다. intermediate enum에는 `PUBLISHED_UNVERIFIED`, `PROMOTING`,
`RELEASE_UNVERIFIED`, `QUARANTINE_PENDING`이 포함된다. public verification failure만
`QUARANTINE_PENDING -> QUARANTINED`를 거치며,
terminal attempt를 `PRODUCER_PASS`로 덮어쓰지 않는다. producer의
`BLOCKED_*`, `QUARANTINE_PENDING`, `QUARANTINED`, `FAILED`는 #609 `BLOCKED`, `REJECTED`와 `REVOKED`는
#609 `REJECTED`, 미실행·대기 상태는 #609 `PENDING`으로 매핑한다. #609 `PASS`는
#609-E가 별도로 판정한다. `PRODUCER_PASS` 후 사고는
기존 ledger를 수정하지 않고 별도 append-only revocation receipt를 추가한다.

release copy가 중단되면 target package의 attempt tag와 manifest digest를 조회한다.
target이 없으면 transient copy failure로 bounded retry하고, exact staging digest면
`RELEASE_UNVERIFIED`에서 검증을 재개한다. 다른 digest나 복수 tag면 `REJECTED`다.
#611이 release digest를 거부해도 먼저 `QUARANTINE_PENDING`으로 전이하고 위와 동일한
current revocation merge, 새 `RECONCILE`, signed emergency receipt attestation과
read-back을 거친다. `QUARANTINED`는 표의 모든 invariant가 충족된 뒤에만 기록한다.
stable tag는 이전 known-good digest에 유지한다. package 전체 접근 제한이 필요하면
별도 승인으로 visibility를 private로 바꾸며 evidence와 incident receipt를 보존한다.

push 뒤 검증이 실패하면 image bytes를 성공 artifact로 광고하지 않는다. GHCR package
삭제는 파괴적 조치이므로 자동화하지 않고 digest를 `REJECTED`로 기록한다. input/build가
바뀌거나 remote digest가 없는 실패 뒤의 `PRODUCE` 재시도는 새 attempt와 새 immutable
digest를 만든다. remote staging이 존재하는 interruption은 release/evidence의 존재와
무관하게 먼저 기존 attempt를 `INTERRUPTED`로 고정하고, 새 `RECONCILE` attempt가
required `expectedStagingDigest`를 기준으로 그 digest를 재사용해
read-back/compensation만 수행한다. release 또는 evidence가 있는데 staging이 없으면
registry state가 계약 밖이므로 `REJECTED` incident로 고정하고 `RECONCILE`이나 새
`PRODUCE`로 재사용하지 않는다. 두 mode 모두 `replacesAttemptId`와 이전
hash를 기록하며 `RECONCILE`은 새 image/evidence를 build/push하지 않는다.

### 사후 폐기와 rollback runbook

secret 노출, model/license 변경 통지, digest/attestation 불일치 또는 critical upstream
취약점이 확인되면 owner는 다음 순서로 처리한다.

1. digest를 #611 trust policy denylist에 추가하고 revocation receipt를 발행한다.
2. release package visibility를 private로 바꾸는 파괴적 side effect는 별도 승인을
   받은 뒤 수행하며, 불가능하면 package description과 linked issue에 revocation을
   게시한다.
3. 영향을 받는 credential을 rotation하고 staging/release package write를 중지한다.
4. downstream #609/#545/#544/#547/#169에 revoked digest와 대체 상태를 통지한다.
5. 이전 known-good digest가 현재 policy를 다시 통과할 때만 consumer pin을 복구한다.
6. 수정된 input으로 새 attempt/digest를 만들며 revoked digest나 tag를 재사용하지
   않는다.

revocation은 기존 attestation과 incident receipt를 보존한다. 삭제는 복구 가능성과
감사 증거를 검토한 별도 파괴적 승인 없이는 실행하지 않는다.

## 보안과 운영 경계

- 모든 third-party GitHub Action은 full commit SHA로 고정한다.
- `pull_request_target`, fork code checkout과 untrusted user input을 producer에 사용하지
  않는다.
- shell은 `set -Eeuo pipefail`을 사용하고 untrusted value를 command string으로
  재평가하지 않는다.
- download host와 redirect, byte count, timeout, digest를 검증한다.
- download는 validated public IP에 고정하고 모든 symlink/special file, archive bomb와
  normalized duplicate path를 추출 전에 거부한다.
- token과 authorization header는 command line, artifact, cache, job summary에 남기지
  않는다.
- validation/staging/source-repro/image-build와 privileged job을 분리하고 package write는 push job,
  attestation/OIDC write는 attest job에만 준다.
- unprivileged build job은 GitHub write/OIDC token을 받지 않고, privileged job은
  upstream build script나 생성된 image code를 실행하지 않는다.
- concurrency group은 한 producer publish만 허용하고 진행 중 publish를 자동
  취소하지 않는다.
- 모든 job은 bounded timeout을 가진다. HTTP download, GitHub API와 registry operation은
  각각 고유 `operationId`를 사용하고 분류된 transient 오류에만 operation별 최대 3회와
  2/4초 backoff를 적용한다. retry receipt는 kind, operation ID, 시도 수, delay,
  terminal classification을 기록한다. workflow rerun ordinal/lineage와 합산하지 않고
  permanent mismatch는 재시도하지 않는다.
- run artifact와 GHCR digest의 retention/visibility를 read-back하여 ledger에 기록한다.
- 실패 시 mutable tag를 되돌리는 대신 rejected digest와 run ID를 보존한다.
- OCI layout을 push하기 전에 checkout, staging manifest, image filesystem/layers와
  workflow artifact를 fail-closed secret scan한다. push job log와 attestation artifact는
  public visibility 변경 전에 다시 검사하고 `secretScan=PASS` receipt를 남긴다.
  scan 누락·실패는 push 또는 visibility/consumer handoff를 차단한다. 사후 노출을
  발견하면 digest를 quarantine 상태로 표시하고 credential rotation을 시작한다.

### 운영 소유권과 보존

| 대상 | owner/승인 경계 | read-back evidence |
|---|---|---|
| workflow, input lock, trust policy, CODEOWNERS | repository owner `debop`; PR exact-head CI/review와 별도 merge 승인 | branch ruleset, commit SHA, CODEOWNERS |
| `paddleocr-producer` environment와 dispatch | repository owner `debop`; exact-head fresh dispatch 승인 | environment deployment와 run actor |
| staging/release GHCR package와 visibility | organization/package owner `debop`; 최초 생성·visibility·삭제 각각 별도 승인 | package API permissions/visibility |
| #611 verifier, denylist와 revocation | issue owner `debop`; producer write credential 없이 검증 | trust policy SHA, verifier receipt |
| emergency credential rotation | repository/organization owner `debop`; incident가 확인되면 즉시 실행 | token permission read-back과 incident receipt |

accepted release image, canonical producer evidence, SPDX SBOM, legal inventory,
attestation bundle과 revocation receipt는 자동 삭제하지 않는다. 모든 linked issue와
downstream consumer가 종료된 뒤에도 최소 3년 보존하며, 그 이후 삭제·이관은 별도
파괴적 승인과 독립 export 검증이 필요하다. GitHub attestation store와 함께 GHCR OCI
evidence artifact/referrer에 bundle을 보존하여 한 저장소의 retention에만 의존하지
않는다. workflow run logs와 일반 workflow artifact는 최소 90일 보존하고
실행 직후 configured retention을 read-back한다. GitHub 정책이 90일을 허용하지 않으면
producer acceptance를 `BLOCKED`로 둔다. private staging의 실패 image는 30일 격리 후
삭제 후보로 표시하되 metadata, receipt hash와 incident 기록은 유지한다. 실제 삭제는
package owner의 별도 파괴적 승인 후 수행한다.

manual run 직후 owner는 다음 preamble로 placeholder를 실제 값에 고정한 뒤 run,
package와 image를 대조한다. attestation은 위 #611 절의 provenance/SBOM 분리 명령을
사용한다.

```bash
REPO=bluetape4k/bluetape4k-image
REPO_ROOT="$(git rev-parse --show-toplevel)"
RUN_ID="${RUN_ID:?export the decimal workflow run ID}"
export ATTEMPT_ID="${ATTEMPT_ID:?export run-id.run-attempt}"
export IMAGE_TAG="image-$ATTEMPT_ID"
export EVIDENCE_TAG="evidence-$ATTEMPT_ID"
IMAGE_REF=ghcr.io/bluetape4k/paddleocr-service
IMAGE_DIGEST="${IMAGE_DIGEST:?export sha256:... platform manifest digest}"

gh run view "$RUN_ID" --repo "$REPO"
python3 "$REPO_ROOT/scripts/research/paddle_ocr_producer.py" readback-packages \
  --owner bluetape4k \
  --release-package paddleocr-service \
  --staging-package paddleocr-service-staging \
  --attempt-id "$ATTEMPT_ID" \
  --image-tag "$IMAGE_TAG" \
  --evidence-tag "$EVIDENCE_TAG" \
  --connect-timeout-seconds 10 \
  --read-timeout-seconds 30 \
  --max-page-bytes 2097152 \
  --max-pages 20
docker buildx imagetools inspect "$IMAGE_REF@$IMAGE_DIGEST"
```

stable result code는 위 상태/exit mapping 표만 사용한다. package API `200`의
`visibility`, version `name`과 `metadata.container.tags`를 reconciliation JSON에
기록한다. `404`는 absent, `401`/`403`은 `BLOCKED`, `429`/`5xx`는 bounded retry,
digest/tag 불일치는 `REJECTED`다. versions 응답은 page별로 검증하며 exact
`image-<attemptId>`와 `evidence-<attemptId>` tag에 맞는 record만 bounded accumulator에
보존한다. 각
selector의 0건은 absent, 2건 이상은 ambiguous package state로 처리하며 둘 다
`PRODUCER_PASS`를 금지한다. 유일한 결과의 `.id`를
`versionId`, `.name`을 `manifestDigest`, package root의 `.visibility`를 canonical
reconciliation field에 기록한다. staging과 release의 index/platform/config/base
digest가 각각 exact-match하는지 registry inspect 결과와 다시 대조한다.

`producer-reconciliation.schema.json`은 root `schemaVersion=1`, `attemptId`,
`producerStatus`, `lastCompletedStage`, `mapped609Status`, `inputLockSha256`, `staging`, `release`, `evidence`,
`replacesAttemptId`, `replacedReleaseDigest`, `replacedEvidenceDigest`,
`replacedAttemptSha256`, `replacedEvidenceSha256`, `replacedReconciliationSha256`,
`replacedCleanupSha256`,
`workflowRetryOrdinal`, `retryReceipts`, `secretScan`, `cleanupVerified`, `denylistVerified`, `visibilityReadBack`,
`downstreamReadBack`, `revocationsSha256`, `revocationsCommitSha`,
`previousDocumentSha256`, `statusChangedAt`, `incidentUrl`, `ownerAcknowledgedAt`, `closedAt`만
허용하고 이 root key를 모두 `required`로 둔다. root와 모든 nested object는
`additionalProperties=false`다. status는 이 문서의 producer enum, mapped status는
`PENDING|BLOCKED|REJECTED`, `attemptId`는 `<decimal-run-id>.<decimal-attempt>` 형식,
artifact digest는 `sha256:` prefix가 있는 lowercase 64-hex, document SHA는 prefix 없는
lowercase 64-hex, commit은 40-hex, workflow retry ordinal은 positive integer, boolean
필드는 JSON boolean이다. `retryReceipts`의 각 object는 unique `operationId`,
`kind=HTTP_DOWNLOAD|GITHUB_API|REGISTRY`, `attempts=1..3`, `delaysSeconds`의 `[2,4]`
prefix와 `terminal=SUCCESS|TRANSIENT_EXHAUSTED|PERMANENT_FAILURE`만 허용한다.
`previousDocumentSha256`은 initial document에서만 `null`이다. `secretScan`은
`NOT_RUN|PASS|FAIL` enum이다.
초기 `PRODUCE` attempt의 replacement field는 모두 `null`이다. `RECONCILE` attempt는
`replacesAttemptId`와 replaced attempt/evidence/reconciliation/cleanup document hash를
모두 non-null로 요구하고 이전 문서와 exact-match한다. `replacedReleaseDigest`와
`replacedEvidenceDigest`는 prior reconciliation object가 `null`이면 `null`, 존재하면
non-null exact digest여야 한다. prior status와 registry absence/read-back도 함께
exact-match한다.

`staging`과 `release`는 `null` 또는 `package`, positive integer `versionId`, exact
`attemptTag=image-<attemptId>`, `visibility=private|public`,
`pullMode=PACKAGES_READ|ANONYMOUS`,
`pullVerified`, `indexDigest`, `manifestDigest`, `configDigest`, `baseDigest`를 모두 요구하는
object다. 네 digest는 OCI descriptor read-back 값이고 object별 unknown field를 거부한다.
`evidence`는 `null` 또는 release와 같은 `package`, positive `versionId`, exact
`evidence-<attemptId>` tag, visibility, pull mode/result, expected `artifactType`,
evidence `manifestDigest`, release image `subjectDigest`와 `fileManifestSha256`을 모두
요구하는 object다. `subjectDigest`는 release platform manifest digest와 같아야 한다.
`fileManifestSha256`는 outer digest와 self entry가 없는 exact
`evidence-manifest.json` JCS bytes의 prefix 없는 SHA-256이며 producer result의 outer
`evidenceManifestDigest`와 다른 값이다. reconciliation은 registry OCI manifest에서
evidence manifest layer를 찾아 raw bytes를 JCS로 검증·hash한 값과 exact-match한다.
세 timestamp field는 다음 계약을 따른다. `statusChangedAt`은 항상 non-null RFC3339,
`ownerAcknowledgedAt`과 `closedAt`은 RFC3339 string 또는 `null`이며 생략할 수 없다.
`incidentUrl`도 생략할 수 없고 `null` 또는 이 저장소의 HTTPS issue/comment URL이다.

schema는 `allOf`의 `if: producerStatus`/`then`으로 다음 조건을 강제한다. 표의
`true`는 해당 boolean이 정확히 true여야 함을 뜻한다. terminal 상태가 아직 조건을
채우지 못하면 `closedAt=null`을 유지한다.

| status group | package/state invariant | closure/read-back invariant |
|---|---|---|
| `VALIDATING` | staging/release/evidence `null`, mapped `PENDING` | 네 read-back false, incident/owner/closed `null` |
| `BUILDING` | staging/release/evidence `null`, mapped `PENDING` | visibility/downstream false, incident/owner/closed `null` |
| `BLOCKED_INPUT`, `BLOCKED_LEGAL_INVENTORY` | staging/release/evidence `null`, mapped `BLOCKED` | `denylistVerified`, absence를 뜻하는 `visibilityReadBack`, `cleanupVerified`, `downstreamReadBack`가 모두 true일 때 non-null `closedAt`; incident/owner는 `null` |
| `PUBLISHED_UNVERIFIED` | staging non-null/private, release/evidence `null`, mapped `PENDING` | `closedAt=null`; 15분 초과 또는 terminal workflow conclusion이면 incident/owner non-null |
| `PROMOTING` | `lastCompletedStage=EVIDENCE`, staging non-null/private, release/evidence `null`, mapped `PENDING` | `closedAt=null`; 15분 초과 또는 terminal workflow conclusion이면 incident/owner non-null |
| `RELEASE_UNVERIFIED` | staging/release non-null, evidence null 또는 non-null, mapped `PENDING` | `closedAt=null`; 15분 초과 또는 terminal workflow conclusion이면 incident/owner non-null |
| `QUARANTINE_PENDING` | release/evidence public, mapped `BLOCKED`, public consumer failure receipt non-null | incident/owner non-null, `denylistVerified=false`, `closedAt=null`; promotion/stable tag 금지 |
| `PRODUCER_PASS` | staging/release index/manifest/config/base digest 모두 동일, evidence non-null/subject equality, staging private/`PACKAGES_READ`, release/evidence public/`ANONYMOUS`, 세 pull verified, mapped `PENDING`, `secretScan=PASS` | denylist/visibility/cleanup/downstream 모두 true, incident/owner/closed `null` |
| `QUARANTINED` | mapped `BLOCKED`, current revocation에 image/evidence digest가 있고 signed emergency deny receipt가 exact subject를 가짐 | incident/owner non-null; `denylistVerified=true`, emergency receipt attestation verified, current revocation commit, visibility rollback, cleanup/downstream 모두 true일 때만 non-null `closedAt` |
| `REJECTED`, `REVOKED` | mapped `REJECTED`, 존재하는 package digest가 denylist에 있음 | incident/owner non-null; denylist/visibility/cleanup/downstream 모두 true일 때만 non-null `closedAt` |
| `FAILED`, `CANCELLED`, `INTERRUPTED` | mapped `BLOCKED`; package가 있으면 그 digest가 denylist에 있음 | incident/owner non-null; package 존재/부재 visibility 대조, denylist, cleanup, downstream이 모두 true일 때만 non-null `closedAt` |

`closedAt`이 non-null이면 `ownerAcknowledgedAt <= closedAt`이고 두 값 모두
`statusChangedAt` 이후여야 한다. package가 없는 terminal 상태의
`visibilityReadBack=true`는 staging/release version 0건을 확인했다는 뜻이고,
`denylistVerified=true`는 exact revocations document를 검증했다는 뜻이다.

job timeout 또는 비정상 run conclusion이 있으면 즉시
reconciliation을 실행하고, `PUBLISHED_UNVERIFIED`가 15분을 넘으면 release promotion을
차단한 채 incident issue/comment와 cleanup receipt를 남긴다. owner가 reconciliation,
denylist/visibility, cleanup과 downstream 통지를 read-back해야 incident를 닫는다.

## 검증 설계

### 로컬·PR 검증

- input lock schema와 semantic validation unit test
- candidate/resolved lock 분리와 모든 Python/native artifact hash-enforced offline install test
- source-repro-check wheel hash/run binding과 image-build source/build-backend 부재 test
- source/model SHA-256과 canonical tree/pair hash fixture test
- model runtime tree에서 legal file을 제외하고 legal inventory를 별도 결합하는 test
- archive traversal, 모든 link/special file, alias/duplicate path, nested archive,
  sparse file, decompression ratio·file count·path·time oversize negative test
- HTTPS/port/userinfo/IP literal, redirect hop, private/link-local/metadata IP와 DNS
  rebinding negative test
- 모든 redirect hop의 scheme/port/userinfo/IP literal/host 재검증 test
- incomplete legal inventory와 `PENDING` value 거부 test
- workflow repository/ref/platform/hosted-runner/actor/environment/permission policy test
- build와 push/attest credential isolation, `persist-credentials: false` audit
- 동일 input의 manual dispatch 두 건, concurrency와 duplicate `PRODUCER_PASS` run 거부 test
- 상태 전이, 취소·중단 finalizer와 cleanup receipt fixture test
- attempt/evidence/cleanup/reconciliation/result strict schema와 cross-document digest binding test
- revocation document commit/parent hash chain, entry removal·변경·재정렬, stale/diverged
  snapshot과 current protected `develop` chain 불일치 거부 test
- emergency deny receipt의 image/evidence/incident/current revocation/signature binding과
  unsigned·stale receipt 거부 test
- transient/permanent retry 분류, 최대 시도 수와 lineage test
- ledger issuer/audience/subject/repository/workflow SHA/ref/environment/run/head mismatch,
  missing/ambiguous/malformed attestation과 non-zero verifier negative test
- pre-push와 pre-public secret scan fail-closed test
- baked model root, exact ENTRYPOINT/CMD, `/health/ready`와 external model override 거부 test
- producer validator CLI schema/exit/error-code와 #609 fragment adapter test
- OCI handoff name/file/hash/size/run binding과 safe read test
- OCI handoff의 local blob confinement, foreign layer/descriptor `urls` 거부와
  evidence manifest strict schema, index/platform/config/base equality test
- public evidence OCI subject/digest, exact file allowlist, file/layer count·size·path·time
  limit, uncompressed-only media type, compressed/archive layer 거부, safe materialization,
  anonymous pull과 provenance/SPDX predicate 분리 test
- GitHub API/registry response timeout, page/byte 상한, streaming selector와 duplicate
  match 거부 test
- RFC 8785 JCS golden vector, 동치 JSON digest와 duplicate key 거부 test
- 기존 `scripts/research/test_paddle_ocr_receipt.py`
- 기존 `scripts/research/test_paddle_ocr_smoke.py`
- `actionlint`
- `git diff --check`

PR 검증은 network에서 실제 model/image를 게시하지 않는다. 작은 fixture로 fail-closed
동작을 증명한다.

### 승인된 producer run 검증

- exact `develop` head와 workflow run ID read-back
- native `linux/amd64` runner와 build platform receipt
- allowlist의 실제 source/package/model bytes SHA-256 재검증
- GHCR index/platform/config/base digest read-back
- image 내부 model file/tree/pair hash와 license inventory 재계산
- SPDX JSON schema와 package/model inventory 검사
- provenance/SBOM attestation의 동일 subject 확인
- 잘못된 repository/workflow/signer/issuer/subject를 거부하는 independent negative test
- secret/log/cache scan
- exact actor, ruleset/environment approval, hosted runner image와 OIDC/run claim read-back
- job timeout, build duration, retry와 cleanup receipt read-back
- attempt lineage와 partial publication registry reconciliation
- `PROMOTING`/`RELEASE_UNVERIFIED` partial target과 #611 reject reconciliation
- private staging, release promotion digest equality와 visibility read-back
- revocation/denylist, known-good rollback과 downstream notification rehearsal
- retention, owner 권한과 `PUBLISHED_UNVERIFIED` timeout runbook read-back
- #611 credential-free read-only verifier 실행

## Compatibility와 rollout

이 설계는 기존 Kotlin module, public API, Gradle dependency와 Maven publication을
변경하지 않는다. 추가되는 producer는 기본적으로 수동이며 기존 CI 성공 조건에
자동으로 image publish를 끼워 넣지 않는다.

rollout은 다음 gate를 따른다.

1. producer 코드·workflow spec과 fixture 검증
2. base/model/license allowlist의 실제 digest 확정
3. 구현 PR 생성과 exact-head CI/review
4. 승인된 merge
5. `develop` exact SHA에서 producer `workflow_dispatch` 승인·실행
6. private staging package와 repository 연결 read-back
7. 검증된 digest만 release package로 promotion
8. release package `public` visibility 변경 승인·실행·read-back
9. #611 independent consumer 검증과 #609-C 재개 판정

5~8단계는 GitHub 외부 상태를 바꾸므로 앞 단계의 승인으로 자동 실행하지 않는다.

## Acceptance criteria

| ID | 완료 조건 | 증거 |
|---|---|---|
| AC-01 | producer repository, workflow path/full commit, run/attempt ID, trusted actor, GitHub-hosted native platform과 builder identity가 고정된다. | workflow/run/environment API read-back, runner image/platform receipt |
| AC-02 | private staging과 public release GHCR package, visibility, repository 연결, push/pull 권한과 credential 경계가 기록된다. | package API read-back, permission audit |
| AC-03 | image index/platform/config/base digest와 package lock hash가 ledger에 있다. | registry inspect와 lock verifier |
| AC-04 | detector/recognizer source, archive/file/tree/pair hash와 license/NOTICE가 검증된다. | canonical manifests, legal inventory receipt |
| AC-05 | 같은 platform digest를 subject로 하는 SPDX JSON SBOM과 build provenance attestation이 존재한다. | attestation bundle과 `gh attestation verify` |
| AC-06 | issuer/audience/subject/signer/repository/workflow SHA/ref/environment/run/head allowlist가 non-empty이고 mismatch가 fail-closed다. | positive/negative verifier 결과 |
| AC-07 | image가 고정 ENTRYPOINT/CMD, baked model root와 `/health/ready` 계약을 제공하고 first-use download·external model override를 거부한다. | image filesystem/metadata와 wrapper contract test. 처리량, memory/resource limit와 실제 no-egress runtime은 #609-E/#544에서 `PENDING`으로 판정 |
| AC-08 | pre-push/pre-public secret scan과 cleanup이 PASS이고 #611이 ledger와 명령을 credential 없이 읽고 재검증한다. | scan/cleanup receipt, #611 gate verdict와 linked run/digest |

AC-01부터 AC-08까지 모두 PASS일 때만 #638을 완료한다. model-specific legal evidence가
확보되지 않으면 구현 자체가 정상이어도 AC-04가 `BLOCKED`이므로 issue를 닫지 않는다.

## 알려진 위험과 중단 조건

| 위험 | 대응 | 중단 조건 |
|---|---|---|
| upstream model archive가 같은 URL에서 바뀜 | expected SHA-256과 size를 commit하고 매 run 재검증 | mismatch 즉시 `BLOCKED_INPUT` |
| model license 범위가 불명확함 | source license와 model 재배포 근거를 분리 | model별 근거 없으면 `BLOCKED_LEGAL_INVENTORY` |
| GHCR index와 실행 platform digest 혼동 | platform manifest를 공통 subject로 사용 | subject mismatch면 `REJECTED` |
| GitHub Action tag가 재지정됨 | 모든 action full SHA pin | unpinned action 발견 시 workflow validation 실패 |
| staging/release attestation 실패 | promotion 전에는 `PUBLISHED_UNVERIFIED`, promotion 뒤에는 `RELEASE_UNVERIFIED`로 유지 | stable tag·consumer handoff·자동 삭제 금지 |
| token/log/cache 노출 | 최소 권한, masking, artifact/cache allowlist와 scan | 노출 가능성이 있으면 run 중단 및 token 폐기 절차 시작 |
| hosted runner/package 정책 변경 | GitHub API와 공식 문서를 실행 직전에 재확인 | 필요한 permission 또는 verification 기능이 없으면 dispatch 보류 |

## Writer SPW-01~05

| 항목 | 결과와 근거 |
|---|---|
| SPW-01 대상·목적·근거 | PASS — #638 구현자와 #611 verifier를 독자로 고정하고 live issue, #609 ledger, upstream full commit과 GitHub 공식 attestation 경계를 근거로 사용했다. model SHA와 legal inventory는 미확정으로 표시했다. |
| SPW-02 spec 계약 | PASS — 범위, 대안, 구성 요소, data flow, permissions, failure state, compatibility, rollout, acceptance criteria와 stop condition을 포함했다. |
| SPW-03 한국어 technical register | PASS — 한국어 문장에 source, digest, subject, workflow, status와 명령 token을 그대로 유지하고 동일 개념의 용어를 통일했다. |
| SPW-04 의미·추적성 | PASS — AC-01~08을 #638 완료 조건과 연결하고 #609 ledger invariant, #611 consumer, upstream source와 GitHub permission 계약을 대조했다. |
| SPW-05 최종 read-back | PASS — 미해결 작성 표식 0건, AC 8개, shell block 4개, Markdown link와 구조, permission/tag/reconciliation 계약을 다시 읽고 검증했다. |

## DoD Status

- [x] 문제, 목표, 포함·제외 범위를 고정했다.
- [x] 세 가지 대안과 A안 채택 근거를 기록했다.
- [x] architecture, component, data flow, error handling과 security boundary를 정의했다.
- [x] local/PR test와 승인된 producer run 검증을 분리했다.
- [x] AC-01~08과 downstream #611 handoff를 연결했다.
- [x] workflow merge, dispatch, package visibility와 consumer 검증 gate를 분리했다.
- [x] SPW-05 final read-back
- [x] 여섯 관점 spec review와 main-session integration
- [ ] 작성된 spec에 대한 사용자 검토

최종 상태: `SPEC REVIEWED / USER REVIEW PENDING`
