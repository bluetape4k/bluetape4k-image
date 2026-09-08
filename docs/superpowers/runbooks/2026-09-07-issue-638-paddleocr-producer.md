# PaddleOCR trusted producer 운영 runbook (#638)

이 문서는 Issue #638의 수동 PaddleOCR trusted producer를 실행하고,
workflow·registry·attestation·public evidence를 read-back하는 절차를 고정한다.
현재 CLI와 workflow에 구현된 명령만 실행 가능한 절차로 표시한다. CLI의
`--output`은 command별 canonical payload를 쓰고, stdout은 `data.*` envelope를
반환하므로 둘을 혼동하지 않는다.

## 목적과 결정 경계

producer는 고정된 source/model/package 입력으로 linux/amd64 image를 만들고,
동일한 platform digest에 SBOM·provenance·signature·public evidence를 묶는
증거를 생성한다. PRODUCER_PASS는 producer 증거가 계약을 통과했다는 뜻이며
PaddleOCR 채택(adoption)이나 Kotlin runtime module 추가를 승인하지 않는다.

이 증거는 [#609](https://github.com/bluetape4k/bluetape4k-image/issues/609)와
[#611](https://github.com/bluetape4k/bluetape4k-image/issues/611)의 입력으로
사용한다. 두 issue의 adoption·offline smoke·운영비 결정은 여전히 PENDING이고,
최종 채택 결정은 [#547](https://github.com/bluetape4k/bluetape4k-image/issues/547)의
별도 결정으로 남는다. 이 runbook은 그 결정을 대신하지 않는다.

## 지원 범위와 prerequisites

지원하는 실행 환경은 다음으로 고정한다.

| 항목 | 요구 사항 |
| --- | --- |
| runner | manual GitHub-hosted linux/amd64 (ubuntu-24.04) |
| Python | 3.9 또는 3.13 |
| 도구 | gh, Docker Buildx, verified ORAS v1.3.4 |
| 권한 | repository/environment/package owner가 승인한 workflow dispatch와 package read/write 권한 |
| 대상 | bluetape4k/bluetape4k-image, protected develop, workflow의 exact commit SHA |

GPU runner, arm64 또는 multiarch build, arbitrary tag/ref, model override,
local publish, workflow artifact를 public evidence 대신 사용하는 fallback은
지원하지 않는다. producer가 사용하는 model·package·legal input은 repository의
lock과 inventory에서만 읽는다.

## 0. repo root와 output directory 고정

모든 local 명령은 repo root에서 실행한다. RUN_ID와 ATTEMPT_ID는 workflow의 실제
run/read-back 값으로 채우고 임의로 만들지 않는다.

~~~bash
set -euo pipefail
REPO=bluetape4k/bluetape4k-image
REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"
mkdir -p build
test -n "$RUN_ID" || { echo "RUN_ID is required" >&2; exit 40; }
test -n "$ATTEMPT_ID" || { echo "ATTEMPT_ID is required" >&2; exit 40; }
test -n "$ARTIFACT_DIR" || { echo "ARTIFACT_DIR is required" >&2; exit 40; }
IMAGE_TAG="image-$ATTEMPT_ID"
EVIDENCE_TAG="evidence-$ATTEMPT_ID"

python3 scripts/research/paddle_ocr_producer.py validate-inputs \
  --lock docker/paddleocr/producer-input.lock.json \
  --policy docker/paddleocr/trust-policy.json \
  --legal docker/paddleocr/legal-inventory.json

python3 scripts/research/paddle_ocr_producer.py verify-attempt \
  --attempt "$ARTIFACT_DIR/producer-attempt.json" \
  --evidence "$ARTIFACT_DIR/producer-evidence.json" \
  --reconciliation "$ARTIFACT_DIR/reconciliation.json" \
  --cleanup "$ARTIFACT_DIR/cleanup.json" \
  --ledger-fragment "$ARTIFACT_DIR/artifact-ledger.fragment.json" \
  --revocations docker/paddleocr/revocations.json
~~~

validate-inputs와 verify-attempt는 현재 CLI가 제공한다. verify-attempt의
출력에서 producerStatus=PRODUCER_PASS, exitCode=0을 확인해도 public visibility
read-back과 anonymous public verify가 끝난 것은 아니다.

### read-back adapter와 output shape

`readback-workflow-run`, `readback-packages`, `readback-public-gate`,
`readback-incident`, `readback-retention`은 bounded GitHub read-only 호출을
수행한다. `prepare-reconcile-inputs`는 prior 문서의 canonical bytes를 로컬에서
검증하고 `validate-reconcile-state`는 prior state artifact와 현재 package read-back을
결합한다. incident와 rollback adapter는 승인 marker와 exact digest를 확인한다.
`--output`을 지원하는 command의 output file는 raw payload이고, output file가
없는 `readback-packages`는 stdout envelope를 파일로 보존한다.

## 1. PR credential-free validation

PR에서는 registry push, package visibility 변경, issue/comment 작성, workflow
dispatch를 수행하지 않는다. 환경에 남은 credential도 검증 경로에 노출하지
않도록 별도의 빈 HOME과 DOCKER_CONFIG를 사용한다.

~~~bash
PR_HOME="$(mktemp -d)"
PR_DOCKER_CONFIG="$PR_HOME/docker"
mkdir -m 0700 "$PR_DOCKER_CONFIG"
trap 'rm -rf "$PR_HOME"' EXIT
env -i \
  HOME="$PR_HOME" \
  DOCKER_CONFIG="$PR_DOCKER_CONFIG" \
  PATH="/usr/local/bin:/usr/bin:/bin" \
  python3 -m json.tool docker/paddleocr/producer-input.lock.json > /dev/null
env -i \
  HOME="$PR_HOME" \
  DOCKER_CONFIG="$PR_DOCKER_CONFIG" \
  PATH="/usr/local/bin:/usr/bin:/bin" \
  python3 .github/scripts/test-paddleocr-producer-workflow.py
~~~

PR validation은 local schema·workflow·negative fixture만 통과시키며 실제
PRODUCE나 registry read/write 권한을 부여하지 않는다. gh, Docker credential,
GH_TOKEN, GITHUB_TOKEN을 이 단계의 입력으로 사용하지 않는다.

## 2. PRODUCE dispatch와 exact run 선택

PRODUCE는 protected develop의 exact head와 fresh owner approval이 있을 때만
dispatch한다. 먼저 현재 commit과 trust context를 확인한다.

~~~bash
HEAD_SHA="$(git rev-parse HEAD)"
test "$HEAD_SHA" = "$(gh api "repos/$REPO/commits/develop" --jq .sha)"
test -n "$PRODUCE_APPROVAL" || { echo "fresh PRODUCE approval is required" >&2; exit 40; }
test "$PRODUCE_APPROVAL" = "APPROVED:$REPO:PRODUCE:$HEAD_SHA"

python3 scripts/research/paddle_ocr_producer.py validate-trust-context \
  --lock docker/paddleocr/producer-input.lock.json \
  --policy docker/paddleocr/trust-policy.json \
  --legal docker/paddleocr/legal-inventory.json \
  --repository "$REPO" \
  --workflow .github/workflows/paddleocr-producer.yml \
  --ref refs/heads/develop \
  --actor debop \
  --runner-environment github-hosted \
  --oidc-issuer https://token.actions.githubusercontent.com \
  --audience sigstore

GH_BIN="$(command -v gh)"
case "$GH_BIN" in /*) ;; *) exit 40 ;; esac
BEFORE_RUNS="$REPO_ROOT/build/before-workflow-runs.json"
python3 scripts/research/paddle_ocr_producer.py snapshot-workflow-runs \
  --repo "$REPO" \
  --workflow paddleocr-producer.yml \
  --branch develop \
  --event workflow_dispatch \
  --gh-bin "$GH_BIN" \
  --operation-timeout-seconds 60 \
  --max-page-bytes 2097152 --max-page-items 100 --max-pages 20 \
  --max-total-bytes 41943040 \
  --output "$BEFORE_RUNS"
gh workflow run paddleocr-producer.yml --repo "$REPO" --ref develop \
  -f mode=PRODUCE \
  -f resumeAttemptId=NONE \
  -f expectedPriorStatus=NONE \
  -f expectedInputLockSha256=NONE \
  -f expectedStagingDigest=NONE \
  -f expectedReleaseDigest=NONE \
  -f expectedEvidenceDigest=NONE
~~~

dispatch 직후에는 새 run 하나만 선택해야 한다. 수동 rerun은 run_attempt=1인
새 dispatch가 아니므로 producer attempt로 재사용하지 않는다. before snapshot은
`snapshot-workflow-runs`가 canonical `{"runIds": [...]}`로 만들고,
`select-dispatched-run`은 `--candidates`를 생략하면 bounded remote fetch를
수행한다. raw `gh api --paginate` response를 candidates로 직접 전달하지 않는다.

~~~bash
python3 scripts/research/paddle_ocr_producer.py select-dispatched-run \
  --before "$BEFORE_RUNS" \
  --repo "$REPO" \
  --workflow paddleocr-producer.yml \
  --branch develop \
  --event workflow_dispatch \
  --gh-bin "$GH_BIN" \
  --operation-timeout-seconds 60 \
  --max-page-bytes 2097152 --max-page-items 100 --max-pages 20 \
  --max-total-bytes 41943040 \
  --expected-head "$HEAD_SHA" \
  --expected-workflow .github/workflows/paddleocr-producer.yml \
  > build/selected-run.json
RUN_ID="$(python3 -c 'import json; print(json.load(open("build/selected-run.json"))["data"]["databaseId"])')"
RUN_ATTEMPT="$(python3 -c 'import json; print(json.load(open("build/selected-run.json"))["data"]["runAttempt"])')"
ATTEMPT_ID="$RUN_ID.$RUN_ATTEMPT"
gh run view "$RUN_ID" --repo "$REPO" --json databaseId,headSha,path,event,runAttempt,status,conclusion
~~~

stdout envelope의 `data.databaseId`와 `data.runAttempt`를 사용한다. `--output`
옵션을 함께 주면 output file에는 선택된 raw run이 기록되므로 이 단계의
envelope extraction과 섞지 않는다. 아래 fixture는 nested API adapter shape가
top-level로 평탄화되지 않는지 bash와 zsh에서 확인한다.

## 3. staging, release, attestation read-back

producer workflow는 build → private staging push → same-digest attestation →
private release promotion → release attestation → evidence push 순서로 실행한다.
run이 끝난 뒤 owner 권한으로 artifact를 추출하고 verify-attempt를 실행한다.

~~~bash
mkdir -p "$ARTIFACT_DIR"
gh run download "$RUN_ID" --repo "$REPO" --dir "$ARTIFACT_DIR"
python3 scripts/research/paddle_ocr_producer.py verify-attempt \
  --attempt "$ARTIFACT_DIR/producer-attempt.json" \
  --evidence "$ARTIFACT_DIR/producer-evidence.json" \
  --reconciliation "$ARTIFACT_DIR/reconciliation.json" \
  --cleanup "$ARTIFACT_DIR/cleanup.json" \
  --ledger-fragment "$ARTIFACT_DIR/artifact-ledger.fragment.json" \
  --revocations docker/paddleocr/revocations.json

python3 scripts/research/paddle_ocr_producer.py readback-workflow-run \
  --repo "$REPO" \
  --run-id "$RUN_ID" \
  --run-attempt "$RUN_ATTEMPT" \
  --expected-head "$HEAD_SHA" \
  --gh-bin "$GH_BIN" \
  --operation-timeout-seconds 60 \
  --output build/run-readback.json

python3 scripts/research/paddle_ocr_producer.py readback-packages \
  --owner bluetape4k \
  --release-package paddleocr-service \
  --staging-package paddleocr-service-staging \
  --attempt-id "$ATTEMPT_ID" \
  --image-tag "$IMAGE_TAG" \
  --evidence-tag "$EVIDENCE_TAG" \
  --gh-bin "$GH_BIN" \
  --operation-timeout-seconds 60 \
  --connect-timeout-seconds 10 \
  --read-timeout-seconds 30 \
  --max-page-bytes 2097152 --max-page-items 100 --max-pages 20 \
  --max-total-bytes 41943040 \
  > build/package-readback.json
~~~

staging과 release는 push 직후 private이어야 하고, public visibility 단계가
성공한 뒤에만 release package가 public이어야 한다. visibility read-back은
상태를 바꾸지 않는 gh api 조회로만 수행한다.

~~~bash
STAGING_VISIBILITY="$(gh api /orgs/bluetape4k/packages/container/paddleocr-service-staging --jq .visibility)"
RELEASE_VISIBILITY="$(gh api /orgs/bluetape4k/packages/container/paddleocr-service --jq .visibility)"
test "$STAGING_VISIBILITY" = private
test "$RELEASE_VISIBILITY" = public
~~~

위 read-back이 기대와 다르면 publish를 계속하지 않고 RECONCILE 또는 quarantine
경로로 이동한다. gh api 조회 결과만으로 visibility를 변경했다고 간주하지 않는다.

## 4. verified ORAS와 anonymous public evidence

public evidence 검증은 별도 빈 credential 환경에서 수행한다. ORAS는
linux/amd64 v1.3.4만 사용하며 다음 URL과 SHA-256을 문자 그대로 고정한다.

| 항목 | 고정 값 |
| --- | --- |
| URL | https://github.com/oras-project/oras/releases/download/v1.3.4/oras_1.3.4_linux_amd64.tar.gz |
| SHA-256 | f27adb935022d94df8dc77719c322dda592c78a0d57a6f7dcdd8d900b248c454 |
| download limit | 8 MiB (8388608 bytes) |
| expanded limit | 16 MiB (16777216 bytes) |
| archive policy | oras, LICENSE, README.md regular files만 허용; member preflight 후 no-follow extraction |

bootstrap-oras가 regular-file, archive member allowlist, archive preflight,
expanded-size와 no-follow extraction을 검증한다. shell의 GNU timeout에
의존하지 않으며, verifier의 내부 deadline argument를 사용한다.

~~~bash
ORAS_URL="https://github.com/oras-project/oras/releases/download/v1.3.4/oras_1.3.4_linux_amd64.tar.gz"
ORAS_SHA256="f27adb935022d94df8dc77719c322dda592c78a0d57a6f7dcdd8d900b248c454"
ORAS_ARCHIVE="$PWD/build/oras_1.3.4_linux_amd64.tar.gz"
mkdir -p build/oras-tools
curl --fail --location --proto '=https' --tlsv1.2 --max-time 60 \
  "$ORAS_URL" --output "$ORAS_ARCHIVE"
test "$(wc -c < "$ORAS_ARCHIVE")" -le 8388608
test "$(shasum -a 256 "$ORAS_ARCHIVE" | awk '{print $1}')" = "$ORAS_SHA256"
python3 scripts/research/paddle_ocr_producer.py bootstrap-oras \
  --archive "$ORAS_ARCHIVE" \
  --tool-root "$PWD/build/oras-tools"
ORAS_BIN="$PWD/build/oras-tools/oras"
case "$ORAS_BIN" in /*) ;; *) exit 40 ;; esac
test -x "$ORAS_BIN"
"$ORAS_BIN" version
~~~

검증 binary는 반드시 absolute path로 넘긴다. ORAS version 출력에
Version: 1.3.4가 없으면 public verify를 진행하지 않는다.

EVIDENCE_REF는 release package의 digest-pinned public evidence reference여야
한다. tag 또는 mutable ref를 넣지 않는다. verifier는 anonymous 환경에서
manifest digest, config digest, evidence manifest와 각 blob digest를 순서대로
fetch하고, 같은 platform subject의 provenance·SBOM attestation을 gh로 검증한다.

~~~bash
ANON_HOME="$(mktemp -d)"
ANON_DOCKER_CONFIG="$ANON_HOME/docker"
mkdir -m 0700 "$ANON_DOCKER_CONFIG"
PYTHON_BIN="$(command -v python3)"
GH_BIN="$(command -v gh)"
# EVIDENCE_REF=ghcr.io/bluetape4k/paddleocr-service@sha256:<64 lowercase hex chars>
test -n "$ORAS_BIN" || { echo "absolute ORAS_BIN is required" >&2; exit 40; }
test -n "$EVIDENCE_REF" || { echo "digest-pinned EVIDENCE_REF is required" >&2; exit 40; }
case "$ORAS_BIN" in /*) ;; *) exit 40 ;; esac
case "$GH_BIN" in /*) ;; *) exit 40 ;; esac
trap 'rm -rf "$ANON_HOME"' EXIT
env -i \
  HOME="$ANON_HOME" \
  DOCKER_CONFIG="$ANON_DOCKER_CONFIG" \
  PATH="$(dirname "$PYTHON_BIN"):$(dirname "$GH_BIN"):/usr/bin:/bin" \
  "$PYTHON_BIN" scripts/research/paddle_ocr_producer.py verify-public-evidence \
  --oras-bin "$ORAS_BIN" \
  --gh-bin "$GH_BIN" \
  --ref "$EVIDENCE_REF" \
  --root "$ANON_HOME/evidence" \
  --operation-timeout-seconds 60 \
  --materialization-timeout-seconds 600
~~~

operation-timeout-seconds 60은 manifest/blob/attestation 한 operation의 상한이고
materialization-timeout-seconds 600은 전체 materialization 상한이다. 각 command의
내부 process group cleanup과 bounded output을 verifier가 담당한다. credential
file, GH_TOKEN, GITHUB_TOKEN, registry auth helper가 보이면 검증을 시작하지 않는다.

## 5. RECONCILE read-back과 새 attempt

실패·runner loss·visibility lag가 발생하면 원래 terminal attempt를 수정하지
않고, 이전 결과를 read-back한 뒤 fresh RECONCILE dispatch를 만든다.
RECONCILE은 build/push/promotion을 실행하지 않고 prior run과 package 상태를
확인해 보상 계획을 만든다.

~~~bash
python3 scripts/research/paddle_ocr_producer.py prepare-reconcile-inputs \
  --prior-result "$PRIOR_RESULT" \
  --prior-reconciliation "$PRIOR_RECONCILIATION" \
  --prior-evidence "$PRIOR_EVIDENCE" \
  --prior-cleanup "$PRIOR_CLEANUP" \
  --output build/reconcile-inputs.json
RESUME_ATTEMPT_ID="$(python3 -c 'import json; print(json.load(open("build/reconcile-inputs.json"))["data"]["resumeAttemptId"])')"
EXPECTED_PRIOR_STATUS="$(python3 -c 'import json; print(json.load(open("build/reconcile-inputs.json"))["data"]["expectedPriorStatus"])')"
EXPECTED_INPUT_LOCK_SHA256="$(python3 -c 'import json; print(json.load(open("build/reconcile-inputs.json"))["data"]["expectedInputLockSha256"])')"
EXPECTED_STAGING_DIGEST="$(python3 -c 'import json; print(json.load(open("build/reconcile-inputs.json"))["data"]["expectedStagingDigest"])')"
EXPECTED_RELEASE_DIGEST="$(python3 -c 'import json; print(json.load(open("build/reconcile-inputs.json"))["data"]["expectedReleaseDigest"])')"
EXPECTED_EVIDENCE_DIGEST="$(python3 -c 'import json; print(json.load(open("build/reconcile-inputs.json"))["data"]["expectedEvidenceDigest"])')"
~~~

caller는 네 prior document의 canonical bytes와 hash를 검증하고 다음 six fields를
모두 기록해야 한다. 새 RECONCILE workflow는 prior run의 exact reconciliation-state
artifact와 현재 registry absence/digest를 다시 비교한다.

| field | 의미 |
| --- | --- |
| resumeAttemptId | 재조정할 이전 run-id.run-attempt |
| expectedPriorStatus | prior result의 status |
| expectedInputLockSha256 | prior input lock hash |
| expectedStagingDigest | staging image digest 또는 NONE |
| expectedReleaseDigest | release image digest 또는 NONE |
| expectedEvidenceDigest | public evidence digest 또는 NONE |

새 dispatch 전에 exact target head와 fresh RECONCILE approval을 다시 확인한다.

~~~bash
HEAD_SHA="$(git rev-parse HEAD)"
test "$HEAD_SHA" = "$(gh api "repos/$REPO/commits/develop" --jq .sha)"
test -n "$RECONCILE_APPROVAL" || { echo "fresh RECONCILE approval is required" >&2; exit 40; }
test "$RECONCILE_APPROVAL" = "APPROVED:$REPO:RECONCILE:$HEAD_SHA"
test -n "$RESUME_ATTEMPT_ID" || exit 40
test -n "$EXPECTED_PRIOR_STATUS" || exit 40
test -n "$EXPECTED_INPUT_LOCK_SHA256" || exit 40
test -n "$EXPECTED_STAGING_DIGEST" || exit 40
test -n "$EXPECTED_RELEASE_DIGEST" || exit 40
test -n "$EXPECTED_EVIDENCE_DIGEST" || exit 40

python3 scripts/research/paddle_ocr_producer.py validate-dispatch \
  --mode RECONCILE \
  --resume-attempt-id "$RESUME_ATTEMPT_ID" \
  --expected-prior-status "$EXPECTED_PRIOR_STATUS" \
  --expected-input-lock-sha256 "$EXPECTED_INPUT_LOCK_SHA256" \
  --expected-staging-digest "$EXPECTED_STAGING_DIGEST" \
  --expected-release-digest "$EXPECTED_RELEASE_DIGEST" \
  --expected-evidence-digest "$EXPECTED_EVIDENCE_DIGEST"

GH_HTTP_TIMEOUT=60 gh workflow run paddleocr-producer.yml \
  --repo "$REPO" --ref develop \
  -f mode=RECONCILE \
  -f resumeAttemptId="$RESUME_ATTEMPT_ID" \
  -f expectedPriorStatus="$EXPECTED_PRIOR_STATUS" \
  -f expectedInputLockSha256="$EXPECTED_INPUT_LOCK_SHA256" \
  -f expectedStagingDigest="$EXPECTED_STAGING_DIGEST" \
  -f expectedReleaseDigest="$EXPECTED_RELEASE_DIGEST" \
  -f expectedEvidenceDigest="$EXPECTED_EVIDENCE_DIGEST"
~~~

RECONCILE workflow와 같은 read-back을 로컬에서 재현하려면 다음 명령을 사용한다.
`PRIOR_STATE`, `PRIOR_RESULT`, `PRIOR_EVIDENCE`, `PRIOR_RECONCILIATION`,
`PRIOR_CLEANUP`은 exact prior run의
`paddleocr-reconcile-state-<attemptId>` artifact에서 받은 다섯 파일이어야 한다.
validator는 state가 기록한 네 document hash를 실제 canonical bytes와 다시 비교한다.
이 block은 package visibility나 tag를 바꾸지 않는다.

~~~bash
RESUME_RUN_ID="$(printf '%s' "$RESUME_ATTEMPT_ID" | cut -d. -f1)"
RESUME_RUN_ATTEMPT="$(printf '%s' "$RESUME_ATTEMPT_ID" | cut -d. -f2)"
PRIOR_HEAD="$(jq -er '.workflowHeadSha' "$PRIOR_STATE")"
python3 scripts/research/paddle_ocr_producer.py readback-workflow-run \
  --repo "$REPO" --run-id "$RESUME_RUN_ID" --run-attempt "$RESUME_RUN_ATTEMPT" \
  --expected-head "$PRIOR_HEAD" --gh-bin "$GH_BIN" \
  --connect-timeout-seconds 10 --read-timeout-seconds 30 \
  --operation-timeout-seconds 60 --output build/prior-run-readback.json
python3 scripts/research/paddle_ocr_producer.py readback-packages \
  --owner bluetape4k --staging-package paddleocr-service-staging \
  --release-package paddleocr-service --attempt-id "$RESUME_ATTEMPT_ID" \
  --image-tag "image-$RESUME_ATTEMPT_ID" \
  --evidence-tag "evidence-$RESUME_ATTEMPT_ID" \
  --gh-bin "$GH_BIN" --connect-timeout-seconds 10 --read-timeout-seconds 30 \
  --operation-timeout-seconds 60 > build/package-readback-reconcile.json
python3 scripts/research/paddle_ocr_producer.py validate-reconcile-state \
  --resume-attempt-id "$RESUME_ATTEMPT_ID" \
  --expected-prior-status "$EXPECTED_PRIOR_STATUS" \
  --expected-input-lock-sha256 "$EXPECTED_INPUT_LOCK_SHA256" \
  --expected-staging-digest "$EXPECTED_STAGING_DIGEST" \
  --expected-release-digest "$EXPECTED_RELEASE_DIGEST" \
  --expected-evidence-digest "$EXPECTED_EVIDENCE_DIGEST" \
  --prior-state "$PRIOR_STATE" \
  --prior-result "$PRIOR_RESULT" \
  --prior-evidence "$PRIOR_EVIDENCE" \
  --prior-reconciliation "$PRIOR_RECONCILIATION" \
  --prior-cleanup "$PRIOR_CLEANUP" \
  --package-readback build/package-readback-reconcile.json \
  --input-lock docker/paddleocr/producer-input.lock.json \
  --output build/reconcile-observation.json
~~~

## 6. revocation, incident, quarantine와 emergency denial

public verify 실패, non-success conclusion, digest mismatch, attestation mismatch,
cleanup failure 또는 runner interruption은 QUARANTINE_PENDING 후보로 기록한다.
protected develop에서 revocation document와 signed emergency receipt를 남긴
뒤에만 quarantine을 QUARANTINED로 종결한다.

현재 CLI가 제공하는 revocation·emergency verification은 다음과 같다.

~~~bash
python3 scripts/research/paddle_ocr_producer.py append-revocation \
  --previous docker/paddleocr/revocations.json \
  --entry build/revocation-entry.json \
  --output build/revocations-next.json
python3 scripts/research/paddle_ocr_producer.py verify-emergency-receipt \
  --receipt build/emergency-receipt.json \
  --revocations build/revocations-next.json
~~~

incident issue/comment side effect는 exact reconciliation hash에 묶인 fresh approval
marker가 없으면 시작하지 않는다. 아래 marker는 사용자가 이 구체 작업을 승인한
뒤에만 설정한다.

~~~bash
test -n "$INCIDENT_APPROVAL" || exit 40
test "$INCIDENT_APPROVAL" = "issue-638-incident:$RECONCILIATION_SHA"
python3 scripts/research/paddle_ocr_producer.py open-or-link-incident \
  --repo "$REPO" --reconciliation "$RECONCILIATION" \
  --expected-sha256 "$RECONCILIATION_SHA" \
  --approval-marker "$INCIDENT_APPROVAL" \
  --gh-bin "$GH_BIN" --operation-timeout-seconds 60 \
  --output build/incident-opened.json
~~~

현재 CLI의 acknowledge-incident와 close-incident는 local read-back receipt를
만든다. reconciliation JSON의 prefix-free SHA-256을 항상 exact 비교하고,
closure condition 네 가지가 모두 true일 때만 closedAt을 기록한다.

~~~bash
test -n "$RECONCILIATION" || exit 40
test -n "$RECONCILIATION_SHA" || exit 40
python3 scripts/research/paddle_ocr_producer.py acknowledge-incident \
  --reconciliation "$RECONCILIATION" \
  --expected-sha256 "$RECONCILIATION_SHA" \
  --incident build/incident-opened.json \
  --output build/incident-acknowledged.json
python3 scripts/research/paddle_ocr_producer.py close-incident \
  --reconciliation "$RECONCILIATION" \
  --expected-sha256 "$RECONCILIATION_SHA" \
  --incident build/incident-acknowledged.json \
  --require-denylist \
  --require-visibility \
  --require-cleanup \
  --require-downstream \
  --output build/incident-closed.json
python3 scripts/research/paddle_ocr_producer.py readback-incident \
  --repo "$REPO" --run-id "$RUN_ID" --run-attempt "$RUN_ATTEMPT" \
  --reconciliation build/incident-closed.json \
  --expected-sha256 "$(sha256sum build/incident-closed.json | awk '{print $1}')" \
  --gh-bin "$GH_BIN" --operation-timeout-seconds 60 \
  --output build/incident-readback.json
~~~

require-denylist, require-visibility, require-cleanup, require-downstream 중
하나라도 충족하지 못하면 close receipt는 실패하고 incident를 닫지 않는다.
downstream notification은 caller-owned 외부 작업이며, receipt·read-back
없이는 closure condition을 true로 만들지 않는다.

## 7. retention과 cleanup

| evidence 또는 resource | 보존 규칙 |
| --- | --- |
| workflow logs와 workflow artifacts | 최소 90일 |
| accepted release image, public evidence, SBOM, legal inventory, attestation, revocation | 최소 3년 |
| failed private staging | 30일 quarantine 뒤 삭제 후보로만 표시 |
| accepted evidence | 자동 삭제 금지; 3년 후에도 별도 destructive approval과 독립 export 검증 없이는 삭제하지 않음 |

cleanup fragment와 aggregate receipt는 각 job의 terminal path에서 남기며,
successful accepted evidence와 revocation chain을 private staging cleanup
대상으로 합치지 않는다. retention 만료는 삭제 권한을 자동으로 부여하지 않는다.

repository와 workflow artifact retention은 read-only 명령으로 다시 확인한다.
GitHub API가 실제 설정값을 제공하지 않거나 요구 기간을 충족하지 못하면 명령은
fail-closed로 종료하며, 요청한 숫자만으로 retention 증명을 대신하지 않는다.

~~~bash
python3 scripts/research/paddle_ocr_producer.py readback-retention \
  --repo "$REPO" \
  --required-run-days 90 \
  --required-accepted-days 1095 \
  --failed-staging-quarantine-days 30 \
  --operation-timeout-seconds 60 \
  --output build/retention-readback.json
~~~

## 8. known-good rollback

rollback은 먼저 read-only plan을 만들고, 별도 destructive approval이 생긴 뒤에만
실행한다.

~~~bash
python3 scripts/research/paddle_ocr_producer.py plan-known-good-rollback \
  --current-digest "$CURRENT_DIGEST" \
  --candidates build/known-good-candidates.json \
  --revocations docker/paddleocr/revocations.json \
  --require-downstream-notification \
  --output build/known-good-rollback-plan.json
~~~

plan-known-good-rollback 결과의 current digest, known-good digest, revocation
absence, downstream notification requirement을 read-back한 뒤 approval marker를
exact 값으로 만든다. 실제 package visibility와 stable tag mutation은 이 별도
승인 전에는 수행하지 않는다.

~~~bash
test -n "$APPROVAL_MARKER" || exit 40
test -n "$REGISTRY_CONFIG" || exit 40
case "$REGISTRY_CONFIG" in /*) ;; *) exit 40 ;; esac
test -f "$REGISTRY_CONFIG" && test ! -L "$REGISTRY_CONFIG"
test "$(stat -f '%Lp' "$REGISTRY_CONFIG" 2>/dev/null || stat -c '%a' "$REGISTRY_CONFIG")" = 600
test "$APPROVAL_MARKER" = \
  "issue-638-rollback:$RELEASE_PACKAGE_ID:$CURRENT_DIGEST:$KNOWN_GOOD_DIGEST"
python3 scripts/research/paddle_ocr_producer.py execute-known-good-rollback \
  --repo "$REPO" --release-package-id "$RELEASE_PACKAGE_ID" \
  --rollback-plan build/known-good-rollback-plan.json \
  --approval-marker "$APPROVAL_MARKER" \
  --expected-current-digest "$CURRENT_DIGEST" \
  --known-good-digest "$KNOWN_GOOD_DIGEST" \
  --mutation visibility-and-stable-tag \
  --oras-bin "$ORAS_BIN" \
  --registry-config "$REGISTRY_CONFIG" \
  --operation-timeout-seconds 60 \
  --output build/known-good-rollback-result.json
python3 scripts/research/paddle_ocr_producer.py readback-known-good-rollback \
  --repo "$REPO" --release-package-id "$RELEASE_PACKAGE_ID" \
  --expected-digest "$KNOWN_GOOD_DIGEST" \
  --operation-timeout-seconds 60 \
  --output build/known-good-rollback-readback.json
~~~

## STATUS_CONTRACT: status, exit, publish 가능 여부와 다음 명령

verify-attempt와 lifecycle receipt가 만든 status/exit contract는 다음과 같이
해석한다.

| producer status | exit | publish 가능 여부 | 다음 조치 |
| --- | ---: | --- | --- |
| PRODUCER_PASS | 0 | PENDING — public evidence read-back 전 | staging/release/public evidence digest와 attestation을 read-back |
| PUBLISHED_UNVERIFIED, PROMOTING, RELEASE_UNVERIFIED | 20 | PENDING | publish 중단, remote read-back 후 RECONCILE |
| QUARANTINE_PENDING | 20 | BLOCKED | revocation·signed receipt·quarantine을 유지하고 emergency denial |
| QUARANTINED | 21 | BLOCKED | quarantine 유지, 새로운 승인 없이는 재사용 금지 |
| REJECTED | 22 | REJECTED | digest를 폐기하고 새 input 또는 새 attempt로 시작 |
| REVOKED | 23 | REJECTED | revocation chain을 보존하고 digest 재사용 금지 |
| FAILED | 30 | BLOCKED | cleanup receipt와 remote state를 read-back한 뒤 원인 수정 |
| CANCELLED | 31 | BLOCKED | cleanup 후 RECONCILE 또는 새 승인된 attempt |
| INTERRUPTED | 32 | BLOCKED | cleanup 후 RECONCILE; 원래 attempt를 수정하지 않음 |
| schema/input invalid | 40 | BLOCKED | input/schema를 수정하고 새 commit으로 재검증 |

PRODUCER_PASS라도 #609/#611 adoption status를 ADOPTED로 바꾸지 않는다.
PENDING, BLOCKED, REJECTED, DEFER는 각각 다른 상태이며 한 receipt로 서로
승격하지 않는다.

## Local source links

- [producer workflow](../../../.github/workflows/paddleocr-producer.yml)
- [producer CLI](../../../scripts/research/paddle_ocr_producer.py)
- [producer library](../../../scripts/research/paddle_ocr_producer_lib/)
- [input lock](../../../docker/paddleocr/producer-input.lock.json)
- [trust policy](../../../docker/paddleocr/trust-policy.json)
- [legal inventory](../../../docker/paddleocr/legal-inventory.json)
- [revocations](../../../docker/paddleocr/revocations.json)
- [Issue #638](https://github.com/bluetape4k/bluetape4k-image/issues/638)

<!-- RUNBOOK_TEST_FIXTURE_BEGIN -->

다음 fixture block은 network와 mutation 없이 nested API adapter extraction만
검증한다. test script가 fake gh를 주입한다.

~~~bash
set -euo pipefail
test -n "$RUNBOOK_FIXTURE_ROOT" || exit 40
mkdir -p "$RUNBOOK_FIXTURE_ROOT"
RUN_READBACK_JSON="$(gh api "repos/bluetape4k/bluetape4k-image/actions/runs/$RUN_ID")"
RUN_DATABASE_ID="$(printf '%s' "$RUN_READBACK_JSON" | python3 -c 'import json, sys; print(json.load(sys.stdin)["data"]["databaseId"])')"
RUN_ATTEMPT="$(printf '%s' "$RUN_READBACK_JSON" | python3 -c 'import json, sys; print(json.load(sys.stdin)["data"]["runAttempt"])')"
printf '%s.%s\n' "$RUN_DATABASE_ID" "$RUN_ATTEMPT" > "$RUNBOOK_FIXTURE_ROOT/selected-attempt"
test "$(cat "$RUNBOOK_FIXTURE_ROOT/selected-attempt")" = "638.1"
~~~

<!-- RUNBOOK_TEST_FIXTURE_END -->
