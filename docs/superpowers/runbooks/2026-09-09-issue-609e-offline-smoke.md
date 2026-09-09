# PaddleOCR no-egress acceptance runbook (#609-E)

이 runbook은 [Issue #609](https://github.com/bluetape4k/bluetape4k-image/issues/609)의
1.1.0 후속 범위인 IMAGE 모드 no-egress acceptance를 재현 가능한 절차로 고정한다.
producer가 만든 immutable image digest를 native `linux/amd64` 호스트에서 detached
container로 실행하고, 외부 네트워크와 published port 없이 내부 loopback probe를
수행한다. 실제 PaddleOCR 인식 결과와 성능 평가는 이 절차의 성공 조건에 포함하지
않으며 별도 후속 범위로 남긴다.

## 판정 경계

`paddle_ocr_smoke.py --execute`는 입력과 Docker 실행 계획을 검증하지만
`executionStatus=PENDING`인 preflight receipt만 만든다. 이 문서의
`paddle_ocr_acceptance.py`가 detached container의 platform, security, readiness,
request limit, 로그, resource, cleanup을 실행 검증하며 PASS/FAIL receipt를 만든다.
기존 `paddle_ocr_receipt.py`의 `CONTRACT_ONLY` receipt는 실행 증거로 승격하지 않는다.

PASS에는 다음 조건이 모두 필요하다.

| 항목 | 조건 |
| --- | --- |
| image | `@sha256:` digest로 고정된 GHCR image |
| platform | image와 Docker host 모두 `linux/amd64` |
| network | `--network none`, published port 없음 |
| identity | `65532:65532`, read-only root, `CAP_DROP=ALL`, `no-new-privileges` |
| limits | request/response byte limit과 PID/CPU/memory limit이 inspect와 probe에서 확인됨 |
| probes | `127.0.0.1` readiness와 `/ocr` loopback probe가 성공함 |
| cleanup | stop, remove, post-cleanup inspect가 모두 검증됨 |

## 사전 조건

실행은 repository owner가 승인한 GitHub-hosted native `linux/amd64` runner에서 한다.
사용할 image는 producer public evidence의 exact digest와 일치해야 하며 mutable tag나
arm64 에뮬레이션을 사용하지 않는다. fixture manifest와 service config는 같은 실행
디렉터리에서 immutable input으로 보존한다.

```bash
set -euo pipefail
RUN_ROOT="$PWD/build/paddleocr-acceptance/<run-id>"
FIXTURE_MANIFEST="$RUN_ROOT/fixture-manifest.json"
CONFIG="$RUN_ROOT/image-service-config.json"
OUTPUT_ROOT="$RUN_ROOT/output"
REPORT="$RUN_ROOT/acceptance-receipt.json"
IMAGE='ghcr.io/bluetape4k/paddleocr-service@sha256:<64-lowercase-hex>'
mkdir -p "$OUTPUT_ROOT"
test -f "$FIXTURE_MANIFEST" && test -f "$CONFIG"
```

config는 `modelSource=IMAGE`, `host=127.0.0.1`, `network=none`, `modelMount=null`,
`outputMount=/out`이어야 한다. command는 `--host 127.0.0.1 --port 8080`만 포함하고,
request/response limit과 readiness timeout은 config의 정수 값을 그대로 receipt에
기록한다.

## GitHub-hosted native 실행

로컬 Colima arm64는 native acceptance의 증거가 될 수 없으므로, 병합된 exact
`develop` head를 GitHub-hosted `ubuntu-24.04` runner에서 실행한다. workflow는 image
digest와 브랜치/head를 고정하고 fixture, preflight receipt, acceptance receipt를
하나의 artifact로 보존한다.

```bash
BRANCH='develop'
HEAD="$(git rev-parse HEAD)"
gh workflow run paddleocr-acceptance.yml \
  --ref "$BRANCH" \
  -f expectedHead="$HEAD"
```

workflow run이 끝나면 `paddleocr-acceptance-<run-id>.<attempt>` artifact의
`acceptance-receipt.json`이 PASS인지 확인하고, run URL과 receipt SHA-256을
[#609](https://github.com/bluetape4k/bluetape4k-image/issues/609)에 기록한다.

## 실행 순서

먼저 preflight를 실행해 fixture/config digest와 Docker argv를 확인한다. 이 단계는
실제 container를 시작하지 않는다.

```bash
python3 scripts/research/paddle_ocr_smoke.py \
  --image "$IMAGE" \
  --fixture-manifest "$FIXTURE_MANIFEST" \
  --config "$CONFIG" \
  --output-root "$OUTPUT_ROOT" \
  --execute
```

그 다음 acceptance를 실행한다. script는 image inspect format으로 digest와
platform을 다시 읽고, `--rm`을 제거한 detached container를 안전한 이름으로
시작한다. Docker inspect 결과를 읽은 뒤 `docker exec` 내부 Python probe로
`/health/ready`와 `/ocr`를 확인하므로 host port를 열지 않는다.

```bash
python3 scripts/research/paddle_ocr_acceptance.py \
  --image "$IMAGE" \
  --fixture-manifest "$FIXTURE_MANIFEST" \
  --config "$CONFIG" \
  --output-root "$OUTPUT_ROOT" \
  --report "$REPORT"
```

image digest read-back에 사용하는 Docker format은 다음과 같다.

```bash
docker image inspect "$IMAGE" \
  --format '{{.Os}}/{{.Architecture}}{{printf "\n"}}{{index .RepoDigests 0}}'
```

receipt는 `docker/paddleocr/acceptance-receipt.schema.json`으로 검증한다.

```bash
python3 -m json.tool docker/paddleocr/acceptance-receipt.schema.json >/dev/null
REPORT="$REPORT" python3 - <<'PY'
import json
import os
from pathlib import Path

receipt = json.loads(Path(os.environ["REPORT"]).read_text(encoding="utf-8"))
assert receipt["status"] == "PASS"
assert receipt["executionStatus"] == "PASS"
assert receipt["platform"]["image"] == "linux/amd64"
assert receipt["platform"]["host"] == "linux/amd64"
assert all(receipt["security"].values())
assert receipt["observed"]["requestLimit"]["status"] == 413
assert receipt["observed"]["cleanup"]["verified"] is True
PY
```

## 실패 경계와 후속 조치

`linux/aarch64` host, missing image digest, proxy/override 환경, published port,
bridge network, root user, writable root, capability 누락, credential가 포함된
로그, 미검증 cleanup은 모두 FAIL이다. 예전 producer run
`34258892748.1`의 image와 receipt는 producer evidence에는 유효하지만 이 acceptance
계약의 native runtime PASS를 대신하지 않는다. local Colima arm64에서 남는
`docker host platform must be linux/amd64`는 의도된 fail-closed 증거이며, native
amd64 runner에서 동일 digest를 다시 실행해야 한다.

이 절차가 PASS가 되면 receipt와 exact image digest를 [#609](https://github.com/bluetape4k/bluetape4k-image/issues/609)에
첨부한다. no-egress acceptance 이후의 #544-B 성능 측정과 #547 운영 채택 결정은
별도 issue 범위로 유지한다. 관련 구현은
[`paddle_ocr_acceptance.py`](../../../scripts/research/paddle_ocr_acceptance.py),
[`test_paddle_ocr_acceptance.py`](../../../scripts/research/test_paddle_ocr_acceptance.py),
[`paddle_ocr_smoke.py`](../../../scripts/research/paddle_ocr_smoke.py),
[`service.py`](../../../docker/paddleocr/service.py),
[`test_service.py`](../../../docker/paddleocr/test_service.py)에서 확인한다.
