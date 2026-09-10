# Issue #544-B PaddleOCR 동일 corpus 비교 runbook

## 목적과 범위

이 runbook은 Issue #544의 canonical OCR corpus v2를 Tesseract와 trusted
PaddleOCR image에서 같은 순서로 실행하고, 품질·geometry·결과 상태·cold/warm
latency·throughput·RSS를 하나의 비교 receipt로 남기는 절차를 고정한다.

비교 runner는 `scripts/research/paddle_ocr_comparison.py`이며 production OCR API,
Paddle dependency, 모델 자동 다운로드, Kotlin public API를 추가하지 않는다. Paddle
실행은 Linux/amd64 hosted runner에서만 수행한다. macOS arm64 checkout의 Tesseract
실행은 parser와 baseline 사전 점검에 사용하고 Paddle 비교 결과로 승격하지 않는다.

## 고정 입력과 공급망 경계

| 항목 | 값 |
|---|---|
| corpus | `bench/ocr-v2/manifest.json` v2, 24 positive + 3 malformed negative |
| manifest SHA-256 | `99502a59751f68aff19634c33239d0f0e50931a17746621e7384ef169faaebb6` |
| Paddle image | `ghcr.io/bluetape4k/paddleocr-service@sha256:cc21ee6edc03c672d11cadaadda828ef83e4ecfdee0da8838b402a763fcdec46` |
| Paddle model pair | `sha256:d2f455ea0e0a34cfa8fac7bba38887cf136d62525e69cf9f433eac8009d9dd8f` |
| service network | Docker `--network none`, loopback request only |
| resource envelope | non-root `65532:65532`, read-only root, dropped capabilities, no-new-privileges, 1 GiB/2 CPU/128 PIDs |
| repetition | cold 1회, warm-up 3회, warm 3회 |

image와 model digest는 #609-E trusted artifact의 현재 값을 그대로 사용한다.
mutable tag, host model mount, 외부 network, implicit model download는 비교 입력이
될 수 없다.

## 실행

먼저 비교 branch의 exact head를 원격에 올린다. workflow dispatch는 그 head를 입력으로
받고 checkout 이후 `GITHUB_SHA`, `git rev-parse HEAD`, runner OS/architecture,
Docker platform을 모두 다시 확인한다.

```bash
HEAD_SHA="$(git rev-parse HEAD)"
gh workflow run paddleocr-comparison.yml \
  --ref feat/issue-544b-provider-comparison \
  -f expectedHead="$HEAD_SHA"
gh run list --workflow paddleocr-comparison.yml --limit 5
gh run watch <run-id> --exit-status
```

workflow는 Python contract test와 offline image preflight를 먼저 실행한 뒤 full-corpus
runner를 실행한다. 마지막으로 다음 Kotlin validator가 receipt와 run manifest의
manifest SHA, fixture 순서, provider identity, outcome payload, geometry bounds,
최소 warm 반복, raw output SHA-256, cleanup 증거를 재생한다.

```bash
./gradlew :bluetape4k-images-benchmark:validateOcrProviderComparisonReceipt \
  -Pocr.comparison.input="$RUN_ROOT/comparison-receipt.json" \
  -Pocr.comparison.runManifest="$RUN_ROOT/run-manifest.json" \
  --no-daemon --max-workers=1 --console=plain
```

artifact 이름은 `paddleocr-comparison-<run-id>.<attempt>`이며 receipt, run manifest,
provider raw response, preflight 자료를 포함한다. artifact의 receipt SHA-256과
workflow exact head를 Issue #544와 #609 댓글에 함께 기록한다.

## 판정 규칙

- `COMPARABLE`은 Tesseract와 PaddleOCR 두 provider가 모두 같은 manifest SHA와
  immutable model/image identity를 기록하고, 27 fixture row를 완주했을 때만 사용한다.
- `TEXT`는 text와 geometry를 함께, `EMPTY`는 둘 다 비어 있게, `ERROR`는 bounded
  error만 갖게 한다. 반복 실행에서 결과가 바뀌면 receipt를 만들지 않는다.
- CER/WER는 21개 expected `TEXT` fixture 평균으로, geometry는 ground-truth line
  box와 normalized text/IoU(`>= 0.5`)로, outcome accuracy는 전체 27개로 계산한다.
- throughput/latency/RSS는 두 provider의 동일 fixture envelope에서만 delta를 만든다.
  hosted comparison이 실패하거나 cleanup이 검증되지 않으면 `PASS` receipt를 만들지
  않고 #544와 #609를 `PENDING`으로 유지한다.
- 비교 수치만으로 #547의 `DEFER`를 자동으로 `ADOPT`로 바꾸지 않는다. 운영 채택은
  동일 결과의 재현성, resource/SLO, license·supply-chain, production boundary를
  별도 검토한 뒤 결정한다.

## 사전 검증

현재 branch에서 다음 검증은 실제 Paddle hosted 실행 전에 완료되어야 한다.

```bash
python3 -m unittest scripts/research/test_paddle_ocr_comparison.py \
  scripts/research/test_paddle_ocr_comparison_workflow.py
actionlint .github/workflows/paddleocr-comparison.yml
./gradlew :bluetape4k-images-benchmark:test \
  --tests 'io.bluetape4k.images.benchmark.OcrProviderComparisonReceiptTest' \
  --no-build-cache --rerun-tasks --max-workers=1 --console=plain
```

macOS에서 실행한 Tesseract 사전 점검은 27개 expected outcome이 `TEXT 21 / EMPTY 3 /
ERROR 3`인지 확인하는 용도다. 이 값은 Linux/amd64 Paddle 비교 receipt를 대신하지
않는다.

## Writer DoD

- `SPW-01`: PASS — 비교 runner 독자, 목적, 범위와 non-goal을 고정했다.
- `SPW-02`: PASS — exact input, 실행 명령, validator, artifact, 판정과 다음 gate를
  연결했다.
- `SPW-03`: PASS — 한국어 문장과 machine-required digest/command/token을 보존했다.
- `SPW-04`: PASS — #545 trusted image/model, #544 v2 manifest, #609-E, #547 DEFER를
  현재 비교 절차와 대조했다.
- `SPW-05`: PASS — hosted 실행 전후에 확인할 exact head, cleanup, receipt hash와
  `PENDING` 경계를 다시 읽을 수 있게 했다.

## 상태

`RUNNER_READY / HOSTED_COMPARISON_PENDING` — 계약·parser·validator·workflow는
검증했지만 Linux/amd64에서 생성한 실제 PaddleOCR 비교 receipt가 생기기 전에는
Issue #544-B 완료나 #547 채택 결론으로 승격하지 않는다.
