# Issue #545 PaddleOCR 오프라인 smoke preflight

| 항목 | 내용 |
| --- | --- |
| Issue | [#545](https://github.com/bluetape4k/bluetape4k-image/issues/545) |
| 상위 train | [#169](https://github.com/bluetape4k/bluetape4k-image/issues/169) PaddleOCR backend 평가 |
| 최종 gate | [#547](https://github.com/bluetape4k/bluetape4k-image/issues/547) 채택 여부 결정 |
| 유형 | Type-E 유지보수/검증 harness |
| 구현 범위 | digest·model·fixture·service 입력 preflight와 Docker 실행 계획 생성 |
| 상태 | `PREFLIGHT` 구현 완료, 실제 service smoke·receipt 실행은 `PENDING` |

## 결정

이번 slice는 PaddleOCR를 실행하거나 제품에 도입하지 않는다. 대신 실제 실행 전에
반드시 통과해야 하는 입력 검증과 Docker 격리 계획을 코드로 고정한다. 따라서
mutable image tag, model 자동 다운로드, shell 문자열, `0.0.0.0` bind, Docker
`bridge` network가 들어오면 실행 전에 거부한다.

`PREFLIGHT_PASS`는 digest-pinned image가 로컬 Docker에 존재하고 입력 manifest와
보안 옵션이 일관되다는 뜻이다. 이는 OCR 결과, 성능, SBOM 서명, 운영 보안 승인,
PaddleOCR 채택을 증명하지 않는다. 실제 service 실행에서 redacted log, cleanup,
SPDX SBOM, provenance/SBOM attestation을 생성한 뒤 기존
`paddle_ocr_receipt.py`의 `CONTRACT_ONLY` 계약으로 별도 검증해야 한다.

## 공식 기준과 적용

공식 [PaddleOCR v3.7.0 release](https://github.com/PaddlePaddle/PaddleOCR/releases/tag/v3.7.0)는
PP-OCRv6 계열과 tiny/small/medium 모델 계층을 제공한다. 공식
[PaddleX serving 문서](https://www.paddleocr.ai/v3.1.0/en/version3.x/deployment/serving.html)는
`paddlex --serve --pipeline OCR` 기본 serving이 `0.0.0.0:8080`에 bind하고 GPU가
없으면 CPU를 사용할 수 있다고 설명한다. 저장소 실행 계약은 이 기본값을 그대로
노출하지 않고 service config에서 `127.0.0.1`, 고정 port, `--network none`을
요구한다.

공식 [PP-OCRv6 tiny detector model card](https://huggingface.co/PaddlePaddle/PP-OCRv6_tiny_det)는
Apache-2.0 license와 tiny detector의 모델 identity를 명시한다. 코드에서는
모델 이름이나 `latest`를 identity로 사용하지 않고, immutable revision, 각 파일의
byte·SHA-256, 전체 tree digest, `NOTICE` 파일을 함께 검증한다. recognizer 등
추가 모델은 같은 manifest 계약으로 별도 항목을 고정해야 한다.

공식 문서의 serving 명령과 model card는 입력의 출처를 설명할 뿐, image digest,
offline egress 차단, daemon rootless 실행, 로그 redaction, attestation 검증을 보장하지
않는다. 이 저장소의 smoke harness가 그 운영 조건을 별도로 검사한다.

## 입력 계약

### Image

`--image`는 `<registry>/<name>@sha256:<64 lowercase hex>`만 허용한다. tag만 있는
값은 local preflight와 향후 CI에서 모두 거부한다. `run_preflight()`는 네트워크를
사용하지 않고 `docker image inspect`로 같은 digest가 로컬에 존재하는지 확인한다.

### Model manifest

model manifest는 다음 내용을 갖는 bounded JSON이다.

- `modelId`, immutable `revision`, allowlist된 upstream `source`
- `licenseSpdx=Apache-2.0`, `noticePath`, NOTICE byte·SHA-256
- pre-baked model tree의 각 relative path, byte 수, SHA-256
- sorted `(path, bytes, sha256)` canonical line으로 계산한 `treeSha256`
- 파일당 256 MiB와 model tree 전체 512 MiB aggregate budget
- `offline=true`

manifest가 가리키는 model root는 regular directory여야 하며, 상대 경로의 각
component와 파일은 `O_NOFOLLOW` directory-FD 방식으로 연다. manifest에 기록한
파일과 `NOTICE` 외의 regular file·symlink·특수 파일이 root에 남아 있으면
fail-closed 된다. 파일 크기와 SHA-256은 안정된 file descriptor에서 bounded read로
다시 계산하고, 검증한 바이트를 별도의 임시 불변 model staging 디렉터리에 복사한
뒤 marker·tree digest·파일 hash를 다시 확인한다. Docker 계획은 raw model root가
아니라 이 staging 디렉터리만 mount한다. 임시 디렉터리 바깥의 host ownership과
실제 service 실행 환경은 이 slice의 증거 범위가 아니다.

### Fixture manifest

fixture manifest도 schema version, unique fixture id, safe relative path, byte 수,
SHA-256을 요구한다. manifest가 놓인 디렉터리 밖의 입력, traversal, symlink,
oversized fixture는 실행 전에 거부한다. 이 hash는 이후 immutable receipt tuple의
`fixtureManifestSha256` 입력으로 사용한다.

### Service config

service config은 shell command가 아닌 argv 배열이어야 하며 다음을 고정한다.

- `paddlex --serve --pipeline OCR --host 127.0.0.1 --port <fixed>` 시작 형태
- `network=none`, model mount `/models`, output mount `/out`
- request/response byte limit과 최대 readiness timeout
- shell metacharacter, URL command, `0.0.0.0`, 임의 network selector 거부

실행 output root는 비어 있고 symlink가 없어야 한다. 이전 실행의 로그나 임시
파일을 새 receipt에 섞지 않도록 preflight에서 조기 거부한다.

## 생성되는 Docker 계획

검증에 성공하면 다음 보안 옵션을 포함한 argv 계획을 생성한다. 실제 service를 아직
시작하지 않으므로 계획에는 host의 model/output 경로를 넣지 않고 placeholder로
redact한다.

```text
docker run --rm --network none --read-only --cap-drop ALL
  --security-opt no-new-privileges:true --user 65532:65532
  --pids-limit 128 --memory 1g --cpus 2
  --tmpfs /tmp:rw,noexec,nosuid,size=64m
  --volume <MODEL_ROOT>:/models:ro --volume <OUTPUT_ROOT>:/out:rw
  <image@sha256:digest>
  paddlex --serve --pipeline OCR --host 127.0.0.1 --port <port>
```

`--volume` 값은 subprocess argv로만 전달하며 shell을 거치지 않는다. 이 계획은
검증한 mutable source root를 그대로 mount하는 실행 adapter를 허용하지 않는다.
`--network none`에서는 host port
publishing을 사용하지 않는다. service readiness와 OCR 요청은
향후 별도 container 내부 client/IPC 단계에서 수행해야 하며, host HTTP client가
자동으로 접근된다고 가정하지 않는다. PaddleX가 자체적으로 외부 URL/model cache를
읽지 않도록 pre-baked model과 `offline=true`를 함께 요구한다. 실행 단계에서는
health/readiness, OCR request limit, redacted log, cleanup, SBOM/attestation을
추가로 기록해야 한다.

계획 결과에는 `requestMaxBytes`, `responseMaxBytes`,
`readinessTimeoutSeconds`를 기록한다. 이는 config 값의 bounded validation과
전달 계획만 증명하며, 실제 HTTP adapter의 enforcement는 service 실행 단계의
별도 증거다.

## 현재 환경과 실행 보류

현재 macOS ARM64 Colima/Docker 환경에서 Docker CLI와 daemon은 정상 확인했지만,
로컬 image 목록에는 digest-pinned PaddleX/PaddleOCR image가 없고 pre-baked model
directory도 없다. 따라서 이번 slice에서는 `docker pull`, model download, 외부
network를 실행하지 않았다. `--execute`를 사용하면 image inspect가 실패하고
`digest-pinned image is not available offline`로 종료한다.

이 실패는 harness 결함이 아니라 오프라인 계약의 의도된 fail-closed 결과다. 실제
실행을 시작하려면 다음 입력을 별도 trusted build에서 준비해야 한다.

1. target architecture에 맞는 digest-pinned CPU image와 exact package lock
2. PP-OCRv6 tiny detector/recognizer의 pre-baked model tree와 Apache-2.0 NOTICE
3. 동일 image digest를 subject로 하는 SPDX SBOM·provenance/SBOM attestation
4. #544 동일 corpus fixture와 OCR request/response adapter 실행 절차

## 검증 결과와 다음 gate

`scripts/research/test_paddle_ocr_smoke.py`는 다음 fail-closed 경계를 검증한다.

- digest 없는 image tag 거부
- model 파일 변경·symlink·tree hash 불일치 거부
- fixture traversal 거부
- `bridge` network, public bind, shell syntax 거부
- local image digest 불일치·미존재 preflight 거부
- 보안 옵션과 host path를 redacted plan으로 기록

현재 이 문서와 harness가 증명하는 범위는 다음과 같다.

| 항목 | 상태 | 근거 |
| --- | --- | --- |
| 입력 manifest·파일 무결성 | PASS | `paddle_ocr_smoke.py`, 단위 테스트 |
| mutable tag·public bind·network egress 조기 거부 | PASS | config/image 회귀 테스트 |
| Docker 실행 argv 보안 계획 | PASS (계획 범위) | 각 격리 flag·redaction·runtime limit 회귀 테스트 |
| 불변 model staging 생성·hash 재검증 | PASS (preflight 범위) | 임시 staging marker·directory-FD hash 회귀 테스트 |
| 실제 staging mount·service 실행 | PENDING | trusted image와 OCR execution adapter 필요 |
| 실제 Paddle readiness/OCR smoke | PENDING | image/model 부재로 실행하지 않음 |
| redacted log·cleanup receipt | PENDING | service 실행 slice 필요 |
| SPDX SBOM·provenance/SBOM attestation | PENDING | trusted build artifact 필요 |
| #544 동일 corpus 비교 | PENDING | benchmark 실행 필요 |
| #547 ADOPT/DEFER/REJECT | PENDING | 위 입력을 모두 수집한 뒤 결정 |

`PREFLIGHT_PASS`를 기존 receipt의 `status=PASS`나 `validationScope=ACCEPTANCE`로
변환하지 않는다. 실제 acceptance verifier와 #547 gate가 닫히기 전까지
PaddleOCR provider, model dependency, public JVM API, HTTP adapter는 추가하지
않는다.

## 재사용 가능한 교훈

- **상황:** 공식 serving 명령은 실행 방법을 설명하지만 image·model의 불변성이나
  egress 차단까지 보장하지 않는다.
- **결정:** 제품 코드에 들어가기 전에 digest, model tree, fixture, loopback,
  `network none`, argv를 하나의 preflight로 검사한다.
- **결과:** 입력이 없거나 mutable하면 조용히 보정하지 않고 실행 전에 중단한다.
  local Docker에 Paddle image가 없었던 이번 환경도 같은 경로로 `PENDING`을
  유지했다.
- **놓치기 쉬운 점:** preflight 통과를 실제 OCR 품질·보안 승인·채택 판정으로
  해석하면 안 된다. 서비스 실행과 receipt 검증은 별도 단계다.
- **다음 방어선:** trusted build가 image digest, pre-baked model, SBOM/attestation을
  준비한 뒤에만 service 실행 lane을 열고, acceptance verifier에서 input root를
  immutable 또는 directory-fd 기반으로 읽는다.

## Writer DoD

- [x] SPW-01: Issue #545 대상, reader, 공식 source, 미확인 실행 결과를 고정했다.
- [x] SPW-02: preflight 입력·실행 경계·실패 모드·acceptance 후속 조건을 문서화했다.
- [x] SPW-03: 한국어 기술 문체와 원문 API/command/URL을 유지했다.
- [x] SPW-04: 공식 serving 기본값과 저장소의 fail-closed 차이를 대조했다.
- [x] SPW-05: 문서 전체를 다시 읽고 PASS/PENDING 경계를 확인했다.

최종 상태: `PREFLIGHT IMPLEMENTED / SERVICE EXECUTION PENDING`
