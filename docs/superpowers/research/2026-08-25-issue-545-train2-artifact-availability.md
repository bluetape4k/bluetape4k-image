# Issue #545 Train 2 trusted artifact 가용성·실행 경계 연구

## 조사 메타데이터

| 항목 | 값 |
| --- | --- |
| Issue | [#545 PaddleOCR service/container 공급망·보안·CI 검증](https://github.com/bluetape4k/bluetape4k-image/issues/545) |
| 상위 Epic | [#169](https://github.com/bluetape4k/bluetape4k-image/issues/169) |
| Main epic | [#513](https://github.com/bluetape4k/bluetape4k-image/issues/513) |
| 선행 Train | [#544 corpus v2 기준선](https://github.com/bluetape4k/bluetape4k-image/blob/develop/docs/superpowers/research/2026-08-25-issue-544-corpus-v2-baseline-reconciliation.md), [receipt contract](https://github.com/bluetape4k/bluetape4k-image/blob/develop/docs/superpowers/research/2026-08-24-issue-545-receipt-contract.md), [smoke preflight](https://github.com/bluetape4k/bluetape4k-image/blob/develop/docs/superpowers/research/2026-08-24-issue-545-smoke-harness.md) |
| 기준 base | `develop` @ `5add7facf71cea0b1c0e2bfbbfdb4b29be16a998` |
| 조사일 | 2026-08-25 (Asia/Seoul) |
| 유형 | Type-E 유지보수·연구·검증 문서 |
| 저장 범위 | 연구 문서, lesson, 7-Tier review, 위키 보존 |
| 생산 변경 | Kotlin/API/dependency/model/service/runtime 설정 변경 없음 |

## 결론

이번 Train 2의 결과는 `ARTIFACT_AVAILABILITY_PENDING`이다. 현재 실행 환경에서
digest-pinned Paddle image를 오프라인으로 사용할 수 없고, 확인 가능한 Docker Hub
후보는 `linux/amd64` 단일 manifest인 반면 Colima/Docker host는 `linux/arm64`이다.
공식 Baidu registry manifest는 인증 없이 `401`을 반환했으므로, 접근 권한이 없는
registry 응답을 trusted provenance 또는 architecture 증거로 승격하지 않았다.

따라서 [#547 adoption gate](https://github.com/bluetape4k/bluetape4k-image/issues/547)의
기존 `DEFER`를 유지한다. 이번 slice에서는 image pull/build, model download,
PaddleOCR 실행, SBOM 서명, production API·dependency를 수행하거나 추가하지 않는다.
모델 identity를 확인한 것과 model bytes/tree·NOTICE를 검증한 것은 별개의 단계로
취급한다.

## 범위와 비범위

### 포함

- 공식 serving/runtime 문서와 source 파일의 현재 내용을 다시 조회하고 SHA-256을
  기록한다.
- Docker registry 후보의 digest·manifest architecture·로컬 offline availability를
  확인한다.
- 기존 `paddle_ocr_smoke.py`에 immutable digest와 offline 설정을 넣어 fail-closed
  preflight 결과를 재현한다.
- 실제 실행을 재개하기 위한 image·model·SBOM·attestation gate를 명시한다.
- 조사 결과를 [bluetape4k-wiki research note](https://github.com/bluetape4k/bluetape4k-wiki/blob/ea70b8b7374c8cc1aa70e82fe0129f1c128ed1e6/research/2026-08-25-issue-545-paddleocr-artifact-availability.md)로 보존한다.

### 제외

- Paddle/PaddleX dependency, JVM API, HTTP adapter, model 파일 또는 Dockerfile 추가
- mutable tag pull, first-use model download, 외부 egress가 있는 서비스 실행
- 실제 OCR 품질·성능 비교, SBOM/provenance 서명 검증, production readiness 승인
- [#544](https://github.com/bluetape4k/bluetape4k-image/issues/544) v2 결과를 Paddle
  결과로 대체하거나 [#547](https://github.com/bluetape4k/bluetape4k-image/issues/547)
  판정을 변경하는 것

## 공식 source-to-claim ledger

2026-08-25에 아래 source를 다시 조회했다. 저장한 값은 source 기준 데이터의
byte-length와 SHA-256이며, 이는 source가 현재 무엇을 말하는지 추적하기 위한
증거이지 artifact의 서명 검증을 대신하지 않는다.

| 주장 | source와 fresh 확인값 | 적용 및 한계 |
| --- | --- | --- |
| serving 명령과 기본 bind | [PaddleOCR serving](https://www.paddleocr.ai/main/en/version3.x/inference_deployment/serving/serving.html), HTTP `200`, `118245` bytes, SHA-256 `1b30c3ba19ac53a0fd02224e03583a3a431aa157fc03bd18c1294fd684354bd9` | `paddlex --install serving`, `paddlex --serve --pipeline OCR`를 설명한다. 문서 기본 `0.0.0.0:8080`은 저장소의 loopback·offline 계약을 자동으로 충족하지 않는다. |
| Python/runtime 범위 | [PaddleOCR v3.7.0 `pyproject.toml`](https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/v3.7.0/pyproject.toml), HTTP `200`, `2707` bytes, SHA-256 `63e8bfa19e197e47649a95dad422bcb09c1d2959367cafd40b0a7f19e4452412` | `requires-python >=3.8`, `paddlex[ocr-core]>=3.7.0,<3.8.0`을 확인했다. 이것은 lock·image digest·SBOM subject가 아니다. |
| model identity | [PP-OCRv6 tiny detector API](https://huggingface.co/api/models/PaddlePaddle/PP-OCRv6_tiny_det), revision `d3177d4e5551463292a61e27cfca2b53e7c3fe9d`, license `apache-2.0`, 5 files | identity와 license metadata만 확인했다. 실제 bytes, 전체 tree digest, NOTICE, recognizer pair는 아직 없다. |
| 공식 Docker 안내 | [PaddleOCR PaddlePaddle 설치 문서](https://github.com/PaddlePaddle/PaddleOCR/blob/main/docs/version3.x/paddlepaddle_installation.en.md) | 공식 문서가 가리키는 base image 설치 경로와 digest-pinned PaddleOCR service image는 동일하지 않다. service acceptance의 producer digest로 사용하지 않는다. |
| Docker Hub 후보 | [PaddlePaddle `3.0.0` tag](https://hub.docker.com/r/paddlepaddle/paddle/tags?name=3.0.0), registry `GET /v2/paddlepaddle/paddle/manifests/3.0.0`, HTTP `200`, `application/vnd.docker.distribution.manifest.v2+json`, manifest digest `sha256:854aa259b6ced0b9c8f2eabb4e0f1314d7dff3e6ddd57cb018035c39eb2c86ce`, config digest `sha256:a5d1b6184e99f5dbcc93e005fa4fbe78bf918c225777f4f0e9424bdfac1104f2`, config `15857` bytes, `linux/amd64` | tag는 조회 편의를 위한 후보명일 뿐 실행 입력으로 사용하지 않았다. 단일 amd64 config이므로 현재 arm64 host의 offline acceptance 입력으로 채택하지 않는다. |

## 실행 환경과 availability 결과

| 검사 | 결과 | 증거 및 해석 |
| --- | --- | --- |
| Docker/Colima runtime | PASS | Docker `29.2.1/linux/arm64`; Colima `aarch64`, Docker runtime, healthy context |
| local Paddle image | FAIL/PENDING | `docker image inspect paddlepaddle/paddle@sha256:854aa259b6ced0b9c8f2eabb4e0f1314d7dff3e6ddd57cb018035c39eb2c86ce`가 `No such image`, exit `1` |
| Docker Hub candidate | PENDING | manifest digest는 확인했지만 config가 `linux/amd64`이며 local image가 없다 |
| Baidu registry | BLOCKED | manifest API가 HTTP `401`; 인증·scope·digest를 확인할 수 없으므로 trusted input 아님 |
| model identity | PASS (provenance only) | revision·license metadata만 확인 |
| model bytes/tree/NOTICE | PENDING | download, file hash, tree hash, NOTICE receipt를 만들지 않음 |
| service readiness/OCR | BLOCKED | trusted image와 pre-baked model이 없어 실행하지 않음 |
| SBOM/provenance/attestation | BLOCKED | 동일 producer digest와 서명된 subject가 없음 |
| #544 동일 corpus 비교 | PENDING | Paddle 결과가 없어 Tesseract v2 baseline과 비교하지 않음 |

### Deterministic preflight receipt

기존 `scripts/research/paddle_ocr_smoke.py`의 `run_preflight`를 immutable candidate와
offline 설정으로 호출했다. 입력 구조 검증은 통과했으나 Docker local inspect가
실패하여 다음과 같이 fail-closed 종료했다.

```text
image=paddlepaddle/paddle@sha256:854aa259b6ced0b9c8f2eabb4e0f1314d7dff3e6ddd57cb018035c39eb2c86ce
hostArchitecture=linux/arm64
preflightError=digest-pinned image is not available offline (docker exit 1)
```

이 receipt는 availability-only synthetic input이다. OCR 성공, model quality, startup,
license, SBOM, attestation 또는 security approval을 증명하지 않는다. 실제 trusted
artifact가 준비되면 같은 receipt contract의 immutable tuple을 새로 생성해야 한다.

#### 실행 명령과 원본 receipt 지문

아래 명령을 2026-08-25에 실행했다. `/tmp/issue545-train2-inputs`와 표준출력·표준
오류 파일은 실행 후 보존하지 않고, 입력과 결과의 SHA-256만 이 문서에 남겼다.

```bash
python3 scripts/research/paddle_ocr_smoke.py \
  --image 'paddlepaddle/paddle@sha256:854aa259b6ced0b9c8f2eabb4e0f1314d7dff3e6ddd57cb018035c39eb2c86ce' \
  --model-manifest /tmp/issue545-train2-inputs/model-manifest.json \
  --model-root /tmp/issue545-train2-inputs/model \
  --fixture-manifest /tmp/issue545-train2-inputs/fixture-manifest.json \
  --config /tmp/issue545-train2-inputs/config.json \
  --output-root /tmp/issue545-train2-inputs/out \
  --execute
```

| 입력/결과 | SHA-256 또는 값 |
| --- | --- |
| `model-manifest.json` | `f2b98046348b816a6989c650c2e0984b6e3164632617d741fb3b88597f012016` |
| `fixture-manifest.json` | `1bee19f7b43d7d8ae4cfc857286ff1388423f25199d1bae62337f33b7dfab429` |
| `config.json` | `b9792fd79a0370511f394b5a4a5ee54f1028dff0d6cee79c36bb20c9ff452bd6` |
| `preflight.stderr` | `055e8e9f5065c05fac3fb8ffe70b9efa86e35d8ee7ceb64022f104dc39c5dbf8` |
| `preflight.stdout` | 빈 파일, `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| exit code | `1` |

`hostArchitecture=linux/arm64`는 같은 시각의 Docker server/Colima 환경
(`server=29.2.1`, `os=linux`, `arch=arm64`, Colima `aarch64`)을 함께 기록한 수동
환경 필드다. `run_preflight()`가 반환하는 acceptance field로 해석하지 않는다.

registry 조회도 실행 명령과 요청 정보를 보존했다. Docker Hub는 `GET
https://registry-1.docker.io/v2/paddlepaddle/paddle/manifests/3.0.0`에
`Accept: application/vnd.docker.distribution.manifest.v2+json,
application/vnd.oci.image.manifest.v1+json`을 사용했고, 2026-08-25T02:24:23Z에
HTTP `200`과 위 manifest/config digest를 반환했다. Baidu 경로는 인증 없이
`GET https://ccr-2vdh3abv-pub.cnc.bj.baidubce.com/v2/paddlepaddle/paddle/manifests/3.0.0`
을 같은 Accept header로 조회했으며, 2026-08-25T02:24:36Z에 HTTP `401`과
`WWW-Authenticate: Bearer realm="https://ccr-auth.bj.baidubce.com/service/token"`
을 반환했다. 이는 “artifact 부재”가 아니라 현재 권한으로 trusted manifest를
확인할 수 없다는 뜻이다.

## 선택지·위험·대안

| 선택지 | 장점 | 위험·중단 조건 | 판정 |
| --- | --- | --- | --- |
| `linux/amd64` trusted CI runner | 확인된 Docker Hub candidate와 architecture가 일치하고 CPU smoke를 재현할 수 있음 | runner 비용, cross-host 차이, model 공급망과 SBOM subject를 별도 고정해야 함 | 후속 후보 |
| `linux/arm64` 자체 build | 현재 Colima와 일치하여 local smoke 가능 | Paddle/PaddleX native wheel·base image 지원, producer digest, attestation을 새로 증명해야 함 | 후속 연구 |
| 인증된 Baidu registry artifact | 공식 문서가 가리키는 runtime 경로를 사용할 수 있음 | credential scope, manifest digest, provenance, SBOM subject를 trusted workflow에서 확인해야 함 | 보류 |
| mutable tag 또는 first-use model download | 탐색 시작이 빠름 | offline·재현성·egress·supply-chain 계약 위반 | 거부 |

현재 가장 낮은 위험의 재개 경로는 `linux/amd64` 또는 검증된 `linux/arm64` 중 하나를
먼저 선택하고, 그 architecture에 맞는 digest-pinned CPU service image를 trusted
workflow에서 생산하는 것이다. 단순히 tag를 pull하거나 모델 이름을 설정에 넣는
것은 대안이 아니다.

## 재개 gate

다음 항목을 모두 충족하기 전에는 service execution lane을 열지 않는다.

1. target architecture의 digest-pinned CPU service image와 exact package lock을
   trusted producer가 생성하고, image manifest/config digest를 보존한다.
2. PP-OCRv6 tiny detector/recognizer의 pre-baked model tree, immutable revision,
   파일별 SHA-256, tree digest, Apache-2.0 license/NOTICE를 확보한다.
3. network egress가 없는 clean environment에서 readiness, OCR smoke, request/resource
   limits, redacted log, cleanup을 실행하고 기존 receipt contract tuple을 생성한다.
4. SPDX SBOM, provenance, SBOM attestation의 subject가 동일 image digest인지 독립
   verifier로 확인하고 signature/issuer/workflow를 기록한다.
5. [#544 v2](https://github.com/bluetape4k/bluetape4k-image/issues/544)와 동일한
   corpus로 품질·성능·오류를 비교한 뒤에만 [#547](https://github.com/bluetape4k/bluetape4k-image/issues/547)
   adoption decision을 새 receipt에 연결한다.

## 7-Tier·writer 경계

- **Type-E**: 연구/문서/검증 자료만 변경하므로 Type-A 구현·Type-B API 확장·release
  publication gate를 열지 않는다.
- **Kotlin patterns**: Kotlin source, test, Spring, Exposed, coroutine, API를 한 줄도
  변경하지 않았으므로 `$bluetape-kotlin-patterns` 적용 대상은 `N/A (0 Kotlin files
  touched)`이다. 이 N/A를 Kotlin 품질 PASS로 오해하지 않는다.
- **SPW-01~05**: 대상·독자·공식 source·결정 경계, 실행/실패/재개 조건, 한국어 문체와
  machine token, source read-back, 최종 PASS/PENDING 분리를 이 문서와 lesson에
  기록한다.
- **독립 reviewer**: 별도 read-only lane 결과를 `docs/superpowers/reviews/` 문서에
  기록하고, timeout이면 timeout과 inline fallback을 분리한다. 독립 PASS를 추정하지
  않는다.

## 현재 상태

`ARTIFACT_AVAILABILITY_PENDING` — trusted image·model·SBOM·attestation이 준비되기
전에는 PaddleOCR provider, service API, production dependency를 추가하지 않는다.

## DoD

- [x] 공식 source를 fresh 조회하고 URL·byte length·SHA-256·적용 한계를 기록했다.
- [x] Docker manifest digest와 host architecture를 대조했다.
- [x] local offline preflight 실패를 immutable candidate로 재현했다.
- [x] mutable tag/model 자동 다운로드를 실행하지 않고 보류 근거를 고정했다.
- [x] 위키에 decision-relevant evidence를 보존하고 링크했다.
- [x] 실제 Paddle service/model/SBOM/attestation을 실행하지 않은 사실과 재개 gate를 명시했다.
- [ ] #544 동일 corpus Paddle 비교
- [ ] #547 최종 adoption decision

최종 판정: `ARTIFACT_AVAILABILITY_PENDING / PaddleOCR provider DEFER 유지`
