# Issue #545 PaddleOCR 서비스·컨테이너·공급망·보안·CI 연구

| 항목 | 내용 |
| --- | --- |
| Issue | [#545](https://github.com/bluetape4k/bluetape4k-image/issues/545) |
| 상위 연구 | [#169](https://github.com/bluetape4k/bluetape4k-image/issues/169) PaddleOCR backend 평가 |
| 선행 benchmark | [#544](https://github.com/bluetape4k/bluetape4k-image/issues/544) OCR 비교 corpus·metric·artifact 계약 |
| Main epic | [#513](https://github.com/bluetape4k/bluetape4k-image/issues/513) AI/ML backend 연구 train |
| Train 단계 | RESEARCH-2: 서비스·컨테이너·공급망·보안·CI 계약 |
| 조사일 | 2026-08-19 |
| 문서 유형 | Type E 연구 문서 |
| 결정 | **PaddleOCR provider는 DEFER, self-hosted HTTP service는 조건부 채택 후보** |
| 구현 상태 | dependency·model·container·runtime·public API 변경 없음; 구현은 별도 Type-A 승인 필요 |

이 문서는 PaddleOCR를 JVM 라이브러리에 즉시 추가하는 구현 계획이 아니다. 공식
PaddleOCR/PaddleX의 serving·runtime 동작과 모델·컨테이너 공급망을 저장소의 OCR
baseline 및 #544 benchmark 계약에 대조하여, 채택 가능 조건과 잔여 위험을 고정하는
연구 기록이다. 조사 시점에 실제 모델 실행, CPU/GPU benchmark, SBOM 생성, container
attestation, production HTTP adapter는 수행하지 않았다. 따라서 아래 acceptance
결과는 `PENDING`으로 남겨야 한다.

## 결정 요약

0.5.0 기본 OCR provider는 기존 Tesseract/Tess4J로 유지한다. PaddleOCR 자체의 품질을
부정하는 것이 아니라, 현재 저장소가 재현 가능한 동일 corpus·동일 자원·동일 보안
경계를 가진 비교 결과를 아직 보유하지 않았기 때문이다.

| 판단 대상 | 판정 | 결정 |
| --- | --- | --- |
| Tesseract/Tess4J baseline | **유지** | 기존 JVM/native/container gate와 운영 경계를 기본선으로 사용 |
| PaddleOCR Python/Paddle runtime을 JVM 안에 삽입 | **거부** | Python ABI, native allocator, model cache와 JVM lifecycle을 강결합하지 않음 |
| 호출마다 CLI subprocess | **거부** | process cold-start·model reload·timeout·stderr 정제가 요청 경계가 됨 |
| PaddleOCR persistent local process | **보류** | 프로토타입은 가능하지만 배포·인증·패치 경계가 별도 필요 |
| PaddleX basic self-hosted HTTP | **조건부 채택 후보** | 언어 중립 경계로 provider runtime을 JVM 밖에 격리할 수 있음 |
| PaddleX/Triton high-stability serving | **후속 평가** | Linux·GPU/운영 비용과 별도 endpoint 계약을 먼저 검증해야 함 |
| hosted Paddle API | **기본 거부** | 민감 이미지 외부 전송, egress·quota·data residency와 맞지 않음 |
| gRPC 전용 adapter | **현재 거부** | 조사한 공식 3.x 기본 계약은 HTTP이며 독립적인 gRPC 안정성 근거가 없음 |
| Paddle-to-ONNX 후 JVM direct | **별도 연구** | 변환 fidelity·pre/postprocess·custom op·license를 새로 검증해야 함 |

조건부 채택은 다음 순서를 뜻한다. 먼저 #544의 고정 corpus로 Tesseract와 비교하고,
선택 모델·container·runtime을 해시로 고정한 offline CPU smoke와 보안 검증을 통과한
뒤, provider-neutral API(#546)와 별도 Type-A 구현 issue에서 HTTP adapter를 설계한다.
하나라도 gate를 통과하지 못하면 0.5.0 이후로 계속 `DEFER`한다.

## 범위와 비범위

### 이번 연구 범위

- 공식 PaddleOCR/PaddleX basic·high-stability serving의 runtime 및 endpoint 경계
- Python/Paddle/PaddleX/inference engine과 CPU·GPU·OS matrix
- model source, cache, offline startup, model·container provenance
- digest, SBOM, provenance, license·NOTICE, artifact attestation 정책
- Base64/URL 입력의 SSRF·payload inflation, 인증·TLS·egress·로그 경계
- body/page/pixel/time/concurrency 제한, timeout·retry·circuit breaker
- PR/scheduled/nightly/manual CI tier와 Tesseract failure isolation
- #544 benchmark의 corpus·metric·artifact ledger와 서비스 실행의 연결
- 채택·보류·거부 판단 및 후속 Type-A acceptance gate

### 이번 연구에서 하지 않는 일

- `paddleocr`, PaddleX, Paddle runtime 또는 pretrained model을 Gradle/Python
  dependency로 추가
- JVM public API, `OcrEngine`, `OcrOptions`, Spring/Ktor route 변경
- Python embedding, JNI binding, 호출별 CLI, hosted API client 구현
- 실제 container image build·model download·GPU 실행·SBOM publish
- license를 확인하지 않은 공개 dataset/model을 저장소에 복사
- 이 문서의 계획을 실제 품질 우위나 운영 SLO 달성으로 표현

## 저장소 경계와 선행 train

현재 OCR 모듈은 provider-neutral한 모양을 일부 갖지만 실제 옵션은 Tesseract에
결합되어 있다.

| 경로 | 현재 계약 | 새 provider에 대한 의미 |
| --- | --- | --- |
| `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/OcrEngine.kt` | `ImmutableImage`와 `OcrOptions`를 받는 OCR 진입점 | 이름만으로 provider 교체 가능한 API라고 가정하지 않음 |
| `images-ocr/src/main/kotlin/io/bluetape4k/images/ocr/OcrOptions.kt` | Tess4J `ITessAPI`, `tessdataPath`, engine/page mode 등 노출 | HTTP provider는 이 타입을 public 계약으로 재사용할 수 없음 |
| `images-ocr/build.gradle.kts` | Tess4J implementation dependency | Paddle Python/runtime을 core에 넣으면 provider 경계가 무너짐 |
| `docs/manual/ko/modules/bluetape4k-images-ocr.md` | 다른 provider가 `OcrEngine`을 구현할 수 있다고 설명 | 설명과 실제 options coupling을 함께 해소해야 함 |
| `.github/workflows/ci.yml` | host/container OCR을 명시적 property와 retry로 gate | Paddle full model을 일반 required lane에 바로 넣지 않음 |

따라서 #545 결과는 #546의 provider-neutral API 설계 입력이지, 현재
`OcrEngine` 구현체를 바로 늘리라는 지시가 아니다. 채택 시 최소 topology는
`images-ocr-api`(공통 request/result/geometry), `images-ocr-tesseract`(기존
Tesseract), `images-ocr-paddle-http`(HTTP client/adapter)로 분리하고, runtime과
model은 JVM artifact 밖에 둔다.

선행·후속 관계는 다음과 같다.

1. #543: model/cache/license/CI 공통 정책
2. #544: 동일 corpus, metric, geometry, resource, artifact ledger
3. #545: Paddle service/container/security/CI 조건(이 문서)
4. #546: provider-neutral OCR API와 HTTP adapter 설계
5. #547: 위 결과를 합친 PaddleOCR adoption gate

#169는 #3의 compile dependency가 아니다. 다만 #543~#545에서 고정하는 model
provenance·offline·native CI 정책이 이후 classification train에도 재사용되는
research ordering dependency이다.

## 공식 upstream 확인 결과

아래 값은 2026-08-18~19에 공식 primary source에서 확인한 조사 기준이다. 실제
구현 issue를 시작하기 직전에 release·digest·model card를 다시 확인해야 하며,
이 표를 자동 업데이트되는 “최신” 선언으로 사용하지 않는다.

| 항목 | 조사 기준 | 저장소 판단 |
| --- | --- | --- |
| PaddleOCR release | `v3.7.0`, 2026-06-11 | 버전과 pyproject를 lock하고 구현 직전 재검증 |
| Python package | Python `>=3.8`, `paddlex[ocr-core]>=3.7.0,<3.8.0`, `requests`, `aiohttp` 등 | facade wheel 크기와 실제 inference runtime 비용을 분리 |
| inference engine | Paddle/Paddle static·dynamic, OpenVINO, ONNX Runtime, TensorRT 계열 선택 | 한 image에 engine을 혼합하지 않고 하나를 고정 |
| basic serving | `paddlex --install serving`; `paddlex --serve --pipeline OCR` | JVM과 언어 중립 HTTP 경계로만 검토 |
| basic default | Uvicorn `0.0.0.0:8080`, GPU가 있으면 GPU, 아니면 CPU | public bind·자동 device 선택을 제품 기본값으로 노출하지 않음 |
| high-stability serving | Triton 기반; Linux 중심, HTTP 8000/gRPC 8001/metrics 8002 | 별도 운영·GPU lane; basic과 성능을 동일하다고 가정하지 않음 |
| HPI CPU/GPU | Linux x86-64, Python 3.8–3.12 중심; GPU CUDA 11.8/cuDNN 8.9 또는 CUDA 12.6/cuDNN 9.5 | macOS ARM64와 JVM consumer를 자동 보장하지 않음 |
| model source/cache | source connectivity probe, `paddlex.pretrain_dir`, offline probe disable 환경변수 | name-based download와 first-use network를 금지 |
| PP-OCRv6 medium | detector 약 59.4MB, recognizer 약 73.3MB; tiny는 약 1.9MB/4.4MB | model·engine cache와 Base64 payload를 RSS 예산에 포함 |
| 평가 지표 | v6와 v5/v4 문서 수치는 평가셋이 달라 직접 비교 불가 | vendor 수치로 Tesseract 우위를 주장하지 않음 |
| repository/model license | PaddleOCR repository와 확인한 PP-OCRv6 medium cards는 Apache-2.0 | catalog의 모든 model·dataset·extra dependency에 전이하지 않음 |

### 설치와 runtime의 차이

`paddleocr==3.7.0`을 설치하는 것은 Python facade를 얻는 단계일 뿐이다. 실제
요청을 처리하려면 PaddleX extra, 하나의 inference engine, CPU/GPU native library,
detector/recognizer와 선택 orientation·unwarp model, writable cache가 모두 호환되어야
한다. clean environment를 권장하는 공식 설치 지침은 이런 dependency 충돌 위험을
보여주는 것이지 JVM module에 추가해야 한다는 근거가 아니다.

HPI는 정적 graph/ONNX/TensorRT 등을 자동 선택·변환할 수 있지만 첫 실행에 engine
build 시간이 늘고 model directory에 cache가 생긴다. 따라서 “재시작 때도 동일한
latency”라고 가정하지 말고 cold/warm을 별도 ledger로 보존한다.

### Serving endpoint의 실제 경계

basic OCR pipeline은 JSON POST를 사용하고 image `file`에 URL 또는 Base64를 허용할
수 있다. 기본 binary 응답도 inline Base64여서 큰 image·multi-page PDF에서 요청·응답
크기가 커진다. high-stability Triton endpoint는 별도 `inputs`/`outputs` JSON 구조와
HTTP 8000/gRPC 8001을 사용한다. 두 protocol을 하나의 장기 public API로 합치지 않고
adapter 내부 version으로 구분한다.

기본 URL 입력은 SSRF와 server-side egress를 만들 수 있으므로 이 저장소의 향후
adapter에서는 허용하지 않는다. 요청 body의 bounded Base64 또는 내부 mount에서
검증된 local file만 허용하고, URL 기능이 필요하면 별도 명시적 allowlist·DNS·egress
정책을 추가한다.

## 배포 선택지 비교

| 방식 | 장점 | 단점·위험 | 판정 |
| --- | --- | --- | --- |
| JVM 내부 Python embedding | 호출 경계가 짧고 단일 API처럼 보임 | Python ABI/GIL, native allocator, shutdown, cache와 JVM lifecycle 충돌 | 거부 |
| JNI 또는 직접 native binding | process hop 없음 | 공식 안정 JVM ABI 근거 부족, OS·arch matrix와 crash 격리 부담 | 거부 |
| 호출별 CLI subprocess | 강제 kill·process 격리 가능 | cold-start/model reload, temp file, stdout/stderr protocol과 tail latency | 거부 |
| 장기 실행 Python process | model warm-up과 local IPC 가능 | lifecycle, patching, auth/TLS와 안정 IPC 계약을 직접 운영 | 보류 |
| self-hosted HTTP container | JVM/provider 분리, warm model, rollout·rollback 독립 | network backpressure, payload·auth/TLS, image·model 공급망 추가 | 조건부 채택 후보 |
| Triton high-stability | 표준 inference endpoint와 metrics | Linux/GPU/driver/서버 비용, basic과 다른 schema와 성능 | 후속 평가 |
| hosted API | 운영 setup이 작음 | 이미지 외부 전송, token/quota/egress, 재현성과 data residency 문제 | 기본 거부 |
| Paddle-to-ONNX 후 JVM | sidecar 없이 direct 실행 가능성 | 변환 fidelity, custom op, pre/postprocess·license drift | 별도 연구 |

조건부 service 채택 때도 Kotlin 쪽은 HTTP client와 provider-neutral result만
소유한다. Python/Paddle/model lifecycle은 versioned image가 소유하고, service
failure는 Tesseract fallback이 아니라 명시적 provider failure로 기록하여 조용한
품질 혼합을 막는다.

## Runtime·model·container 공급망 계약

### 고정해야 하는 manifest

모델 이름이나 mutable tag만 설정에 쓰지 않는다. 다음 manifest는 구현 시 JSON 또는
동등한 signed artifact로 고정하고, parser가 검증한 동일 bytes를 loader에 전달해야
한다.

```json
{
  "schemaVersion": 1,
  "provider": "paddleocr-http",
  "pipeline": "OCR",
  "paddleocr": "3.7.0",
  "paddlex": "3.7.x",
  "inferenceEngine": "<one locked engine>",
  "container": {
    "image": "<registry/name>",
    "digest": "sha256:<64 lowercase hex>",
    "architecture": "linux/amd64",
    "python": "<locked version>",
    "paddle": "<locked version>"
  },
  "models": [
    {
      "role": "detector",
      "id": "<exact model id and revision>",
      "source": "<allowlisted artifact URL or repository ref>",
      "bytes": 0,
      "sha256": "<64 lowercase hex>",
      "licenseSpdx": "Apache-2.0",
      "noticePath": "<checked-in or receipt path>"
    }
  ],
  "preprocessSha256": "<repo-owned config hash>",
  "postprocessSha256": "<repo-owned config hash>",
  "cacheKey": "<model identity + sha256 + engine + arch>",
  "offline": true,
  "sbom": { "format": "SPDX-JSON", "sha256": "<64 lowercase hex>" },
  "provenance": { "sha256": "<64 lowercase hex>" }
}
```

필수 의미는 다음과 같다.

- `source`는 allowlist된 mirror 또는 release이며 요청 시 임의 URL resolver가 아니다.
- `bytes`와 SHA-256은 다운로드 후 다시 연 파일이 아니라 실제 loader에 전달한 verified
  bytes에 대해 계산한다. 검증 뒤 경로를 재개방하여 TOCTOU를 만들지 않는다.
- det/rec/orientation/unwarp model은 각각 license와 hash를 가진 독립 항목이다.
- `preprocessSha256`/`postprocessSha256`는 resize, normalization, threshold, 좌표
  복원과 label mapping을 repo-owned 계약으로 묶는다.
- portable model cache와 native engine cache는 identity와 eviction을 분리한다.
- `cacheKey`는 filename, mutable URL, 단순 model name이 아니라 model hash·engine·arch를
  포함한다.
- `offline=true`는 startup 동안 network source probe와 first-use download를 모두
  금지한다. 실패하면 readiness가 되지 않고 provider가 조용히 기본 모델로 바뀌지
  않는다.

### Image와 SBOM 정책

upstream 문서의 Docker image tag와 model source tag는 immutable 보장이 아니다.
실제 배포에서는 다음을 모두 보존한다.

1. base image와 PaddleX image의 `sha256` digest
2. lock된 Python/Paddle/PaddleX/inference engine 버전
3. model artifact별 size/SHA-256/license/NOTICE
4. BuildKit SPDX SBOM과 provenance attestation
5. builder, source commit, build timestamp, CVE scan 시점
6. 가능하면 GitHub artifact/container attestation과 검증 결과

Docker BuildKit의 `--sbom`/`--provenance`와 GitHub `actions/attest@v4`는 채택 시
사용할 수 있는 수단이지 현재 저장소가 이미 증명한 기능이 아니다. SBOM이 없거나
digest·provenance가 서로 맞지 않으면 image를 publish하지 않는다. CVE 숫자 하나를
자동으로 “안전” 판정으로 사용하지 말고, critical/high 예외·수정 기한·runtime
exposure를 함께 기록한다.

### License와 NOTICE 경계

PaddleOCR repository가 Apache-2.0이어도 모든 외부 model, dataset, optional extra,
transitive native library가 같은 license인 것은 아니다. 선택한 detector·recognizer
model card, inference engine, base image package, fixture dataset, font와 NOTICE를
각각 검토한다. 코드 license·model license·data license를 하나의 `licenseSpdx`
값으로 합치지 말고, compound model은 `models[]` 각 항목으로 보존한다.

## 위협 모델과 완화 계약

| 위협 | 실패 경계 | 필요한 완화 |
| --- | --- | --- |
| first-use remote model download/telemetry | startup이 외부 source에 의존 | image/artifact에 model bake, source probe disable, egress deny |
| `file` URL SSRF·cloud metadata 접근 | service가 임의 URL을 fetch | URL 입력 거부; bounded Base64/body 또는 allowlisted local mount만 허용 |
| 0.0.0.0 무단 OCR | 내부 service가 사설망 전체에 노출 | loopback/private bind, reverse proxy auth, mTLS, network policy |
| image/PDF memory·CPU bomb | decode와 Base64 응답이 제한 없이 증가 | max body/pages/pixels, timeout, concurrency, cgroup memory/CPU |
| Base64 응답 inflation | 큰 결과가 proxy·heap을 포화 | response byte cap, page cap, streaming/encoding budget, 413/429 contract |
| OCR text·image·path 로그 유출 | request/debug/exception에 민감정보 포함 | no-log 기본, redacted reason code, path/native cause 내부 로그 전용 |
| model/cache traversal·symlink | writable cache 밖 파일 교체 | managed root, no-follow, owner/permission, verified bytes 단일 open |
| 악성·교체된 model/image | mutable tag 또는 mirror 변경 | digest/size/hash/signature/SBOM/provenance fail-closed |
| retry storm/provider outage | upstream timeout이 요청 폭주로 확대 | bounded retry budget, jitter, circuit breaker, backpressure |
| native/GPU crash | service process가 죽고 요청이 hanging | readiness/liveness, supervisor restart, bounded caller timeout, failure isolation |
| secret 노출 | BOS/cloud credential·proxy secret이 env/log에 노출 | secret manager/file injection, log redaction, URL mode 기본 거부 |

### 입력·출력 제한

향후 HTTP adapter와 service 모두 아래 제한을 명시하고, 각 제한을 넘으면 stable
reason code와 HTTP status를 반환한다. 실제 수치는 #544 corpus와 운영 SLO를 측정한
뒤 정한다.

- encoded body bytes와 decoded image bytes
- page 수와 page당 width/height/pixel 및 전체 pixel 합계
- request wall-clock와 upstream read/connect timeout
- 동시 요청·queue length·per-client rate
- response bytes, OCR text characters, entry/box count
- native RSS·container memory/CPU quota

입력 제한을 초과한 payload는 decoder/engine에 전달하지 않는다. cancellation은
caller가 timeout을 취소한 뒤에도 native call이 즉시 kill된다고 주장하지 않으며,
service-side timeout과 process isolation을 별도 acceptance로 검증한다.

### 오류와 관찰성

외부 응답에는 `reason`, `requestId`, `pageIndex` 등 안정적인 필드만 남긴다. Python
traceback, local path, model cache path, credential, upstream response body와 raw
OCR text는 공개 exception/cause/suppressed에 넣지 않는다. 내부 redacted log에는
provider version, image digest, model identity, timeout stage와 correlation id만
남기며 이미지·원문·secret을 남기지 않는다.

health/readiness endpoint는 image를 처리하지 않고, verified model identity와
runtime readiness만 반환한다. metrics label에 OCR text나 filename을 사용하지 않는다.

잔여 위험은 다음과 같다. upstream model이 동일 이름으로 preprocessing을 바꿀 수
있고, native engine/GPU driver CVE와 driver drift가 존재하며, DoS payload가 정상
한계 안에서도 높은 비용을 만들 수 있다. digest·corpus·resource ledger와 운영
rollback으로 완화하되 “위험이 0”이라고 표현하지 않는다.

## 향후 provider-neutral service 계약

이 연구에서는 public API를 추가하지 않는다. 다만 #546 설계가 다음 경계를 가져야
한다.

```text
Kotlin provider-neutral request
  -> bounded HTTP adapter (auth, timeout, limit, retry budget)
  -> versioned PaddleX service endpoint
  -> normalized OCR result (text + geometry + page index)
```

adapter의 최소 책임은 다음과 같다.

1. `ImmutableImage` 또는 bounded bytes를 provider request로 변환한다.
2. URL 입력을 만들지 않고, request body/page/pixel budget을 먼저 확인한다.
3. connect/read/overall timeout과 cancellation boundary를 적용한다.
4. upstream JSON을 versioned decoder로 읽고, unknown field는 보존하지 않되 unknown
   schema/version은 fail-closed 한다.
5. geometry coordinate space, page order, text normalization을 #544 계약에 맞춘다.
6. upstream error/path/raw body를 sanitized reason으로 매핑한다.
7. retry는 idempotent request와 명시된 budget에서만 수행하고 circuit breaker와
   backpressure를 적용한다.

Paddle basic response와 Triton high-stability response를 하나의 JSON decoder에 섞지
않는다. service image/pipeline/version이 바뀌면 adapter contract fixture와 model
manifest가 함께 바뀌어야 한다. provider failure를 Tesseract 성공으로 합성하는
silent fallback은 하지 않고, fallback 정책이 필요하면 호출자에게 명시적인
provider selection과 결과 provenance를 반환한다.

## CI·검증 계층

PaddleOCR full model을 일반 PR required build에 넣으면 모델 download, native ABI,
GPU availability와 긴 cold-start가 기존 Tesseract 변경을 막을 수 있다. 따라서
실패 격리와 증거 목적에 따라 다음 tier를 분리한다.

| 계층 | 검증 내용 | 네트워크·하드웨어 정책 | merge 영향 |
| --- | --- | --- | --- |
| PR required | fake HTTP contract, schema/error mapping, limit/cancellation, no-network assertion | model/container 없음 | adapter 변경의 결정적 gate |
| scheduled/manual CPU | digest-pinned tiny/small image, pre-baked model, checksum, readiness·OCR smoke | Linux x86-64 CPU | 연구 증거; Tesseract PR gate와 분리 |
| nightly CPU benchmark | #544 동일 corpus, CER/WER/geometry, cold/warm p95, RSS, payload limit | medium model, 사전 준비 cache | adoption gate 입력 |
| GPU/manual | CUDA/cuDNN·Triton 또는 HPI 선택과 결과 비교 | self-hosted pinned runner | required CI 아님 |
| supply-chain | image digest, SPDX SBOM, provenance/attestation, license/NOTICE, CVE policy | no mutable tag | publish 전 gate |

### 실행 acceptance

구체 명령은 implementation issue에서 image·digest를 고정한 뒤 채운다. 현재는
다음 명령 형태와 증거를 요구한다.

```bash
# PR: fake service, external network가 없는 contract test
./gradlew :bluetape4k-images-ocr-api:test :bluetape4k-images-ocr-paddle-http:test

# CPU smoke: digest와 model manifest가 이미 workspace에 존재해야 함
docker run --rm --network none --read-only \
  --mount type=bind,src="$MODEL_DIR",dst=/models,ro \
  <paddle-image>@sha256:<pinned-digest> \
  paddlex --serve --pipeline OCR

# supply-chain evidence: 실제 build 단계에서 digest/attestation 확인
docker buildx build --sbom=true --provenance=true \
  --tag <registry/name>@sha256:<pinned-digest> --push .
```

위 예시의 `<...>` 값은 아직 선택하지 않았으며 실행 결과를 의미하지 않는다. CPU
smoke는 외부 egress가 차단된 상태에서 model source probe 없이 readiness에 도달해야
하고, manifest hash와 loader bytes가 일치해야 한다. model checksum이 맞지 않으면
server가 준비되지 않아야 한다.

각 run은 #544의 `runId`, fixture id, provider/model/container identity, request
limit, host architecture, Java/Python/Paddle version, raw/normalized output path와
hash를 포함한 ledger를 저장한다. 서로 다른 attempt가 같은 파일을 덮어쓰지 않도록
`raw/<fixture-id>/<attempt-id>.json` 또는 동등한 JSONL cardinality를 사용한다.

Tesseract job이 계속 통과하는지와 별개로 Paddle service failure를 별도 job으로
분류한다. native/container failure가 기존 Tesseract baseline을 차단하지 않아야
하지만, Paddle adoption PR에서는 해당 conditional gate가 `N/A`인지 `FAIL`인지
명시적으로 집계한다.

## 운영·성능 측정 계약

서비스를 채택할 때는 다음을 동일 workload와 동일 resource envelope에서 측정한다.

| 단계 | 측정값 | 보존할 증거 |
| --- | --- | --- |
| image pull/build | image digest, layer bytes, SBOM, provenance | receipt와 attestation |
| process cold | container start→model-ready 시간, first request latency | attempt별 timestamp |
| model warm | p50/p95/p99 latency, throughput, concurrent queue | run ledger와 raw output hash |
| memory | Python/native RSS, peak, model/cache bytes | host/container metrics |
| quality | CER/WER, exact match, geometry precision/recall, ordering | #544 normalized report |
| failure | timeout, 413/429, malformed response, checksum, network deny | stable reason matrix |

CPU tiny/small/medium은 모델 크기·품질·cold-start trade-off를 보여주는 별도 행으로
기록한다. vendor가 제공한 v6 metric을 repository baseline으로 복사하지 않고,
한국어·영어·일본어와 noisy/rotated/table fixture에서 실제 측정한다.

## 대안과 trade-off

| 선택 | 장점 | 단점 | 결론 |
| --- | --- | --- | --- |
| Tesseract baseline 유지 | 현재 API·CI·native gate가 안정적이고 JVM 배포가 단순 | 복잡한 layout·일부 언어 품질 개선 여지 | 0.5.0 기본 |
| Paddle self-hosted HTTP | model warm-up, runtime 격리, JVM provider 확장 | image·service·auth/TLS·version skew·SBOM 운영 | 모든 gate 통과 시 조건부 |
| Paddle in-process | 네트워크 hop 없음 | ABI/GIL/allocator/cache/crash 격리 위험 | 거부 |
| 호출별 CLI | 강제 process 종료 가능 | cold-start, model reload, protocol/temp-file 관리 | 거부 |
| hosted API | 빠른 기능 확인 | 개인정보·egress·quota·재현성 문제 | 기본 거부 |
| Triton high-stability | 표준 HTTP/gRPC·metrics와 GPU 운영 | Linux/GPU 비용, 별도 schema와 driver matrix | 후속 평가 |
| Paddle-to-ONNX | Python service 없이 direct 실행 가능성 | 변환·custom op·pre/postprocess drift | 별도 연구 |

## 재평가 및 채택 gate

다음 항목을 모두 충족한 뒤에만 `ADOPT` 또는 Type-A 구현 issue를 열 수 있다.

- [ ] 정확한 PaddleOCR/PaddleX/Paddle/inference engine/Python 버전과 immutable
  container digest가 고정됨
- [ ] 선택한 detector·recognizer·orientation·unwarp model별 URL/revision, size,
  SHA-256, license, NOTICE, provenance가 고정됨
- [ ] verified bytes와 loader bytes가 동일하고 parser 재개방/TOCTOU가 없음
- [ ] model source probe·first-use download가 없어도 offline startup이 성공함
- [ ] portable model cache와 native engine cache identity·권한·eviction이 분리됨
- [ ] #544 동일 corpus에서 CER/WER·geometry·ordering이 Tesseract 대비 명확한 개선
  또는 명시된 사용사례의 최소 품질을 보임
- [ ] cold/warm p50/p95/p99, RSS/native memory, throughput, concurrency SLO가
  고정 자원 예산 안에 들어옴
- [ ] body/page/pixel/text/entry/time/concurrency/response limits가 fail-closed로
  동작하고 413/429/timeout/error mapping이 결정적임
- [ ] URL SSRF 차단, auth/mTLS, private bind, egress deny, no-log, secret redaction,
  non-root/read-only, cgroup 정책이 실제 smoke로 증명됨
- [ ] PR fake contract와 scheduled CPU smoke가 network-independent·deterministic하고
  Tesseract baseline failure를 차단하지 않음
- [ ] image digest, SPDX SBOM, provenance/attestation, CVE exception, license/NOTICE
  receipt가 publish gate에 포함됨
- [ ] #546 provider-neutral API와 migration/rollback/observability 설계가 승인됨

하나라도 미충족이면 판정은 `DEFER`이며, 미확인 항목을 `PASS` 또는 `N/A`로 축약하지
않는다.

## 조사 근거와 source-to-claim ledger

| 주장 | 공식 근거 | 저장소 영향 |
| --- | --- | --- |
| v3.7.0 release와 Python/PaddleX 요구사항 | [release](https://github.com/PaddlePaddle/PaddleOCR/releases/tag/v3.7.0), [pyproject](https://github.com/PaddlePaddle/PaddleOCR/blob/v3.7.0/pyproject.toml) | exact version lock, clean env |
| basic/high-stability serving 및 Base64/URL 동작 | [PaddleOCR serving](https://www.paddleocr.ai/main/en/version3.x/inference_deployment/serving/serving.html), [PaddleX serving](https://paddlepaddle.github.io/PaddleX/latest/en/pipeline_deploy/serving.html) | HTTP boundary, URL 거부, endpoint version |
| CPU/GPU/HPI와 cache | [HPI](https://www.paddleocr.ai/main/en/version3.x/inference_deployment/local_inference/high_performance_inference.html), [engine](https://www.paddleocr.ai/main/en/version3.x/inference_deployment/local_inference/inference_engine.html) | platform matrix와 cold/warm 측정 |
| model source probe와 cache | [PaddleX FAQ](https://paddlepaddle.github.io/PaddleX/3.7/FAQ.html), [update](https://www.paddleocr.ai/latest/en/update/update.html) | offline startup, no first-use download |
| model size와 metric 비교 주의 | [OCR pipeline](https://www.paddleocr.ai/main/en/version3.x/pipeline_usage/OCR.html) | RSS/payload budget, vendor 수치 재사용 금지 |
| code/model license | [PaddleOCR LICENSE](https://github.com/PaddlePaddle/PaddleOCR/blob/v3.7.0/LICENSE), [detector card](https://huggingface.co/PaddlePaddle/PP-OCRv6_medium_det_safetensors), [recognizer card](https://huggingface.co/PaddlePaddle/PP-OCRv6_medium_rec_safetensors) | per-artifact license/NOTICE |
| immutable image digest | [Docker digest concepts](https://docs.docker.com/dhi/explore/security-concepts/digests/) | mutable tag 금지 |
| SBOM/provenance | [Docker SBOM attestations](https://docs.docker.com/build/metadata/attestations/sbom/), [Docker attestations](https://docs.docker.com/build/metadata/attestations/), [GitHub artifact attestations](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations) | supply-chain publish gate |
| 현재 저장소 corpus·artifact 계약 | [#544 연구 문서](2026-08-19-issue-544-ocr-benchmark-corpus.md) | 동일 runId/ledger 재사용 |
| 현재 Paddle 평가 범위 | [#169 연구 문서](2026-08-18-issue-169-paddleocr-backend-evaluation.md) | provider DEFER와 HTTP 후보 계승 |

공식 source는 기능을 설명하지만 auth/TLS/no-log/egress deny를 자동으로 보장하지
않는다. 그 항목들은 공식 동작에서 도출한 이 저장소의 architecture requirement이며,
구현 시 실제 reverse proxy·network policy·container hardening 증거가 필요하다.

## 연구 DoD

- [x] #513, #169, #544, #545 live issue와 stacked train 관계 확인
- [x] 현재 `OcrEngine`/`OcrOptions`/Tess4J/CI 경계와 #544 contract 대조
- [x] PaddleOCR v3.7.0, PaddleX serving, HPI, source/cache 공식 문서 확인
- [x] deployment 대안, runtime/platform matrix, model size·license 위험 비교
- [x] digest/SBOM/provenance/NOTICE 및 verified-bytes 공급망 계약 정의
- [x] SSRF, egress, auth/TLS, no-log, limits, timeout/retry/circuit-breaker 위협 모델 정의
- [x] PR/scheduled/nightly/GPU CI tier와 Tesseract failure isolation 정의
- [x] 동일 corpus·resource·artifact ledger를 통한 재평가 gate 연결
- [ ] 실제 PaddleOCR model/container CPU smoke 실행
- [ ] 선택 model의 최종 provenance/license/SBOM/attestation receipt 생성
- [ ] #544 corpus에서 Tesseract/PaddleOCR 품질·성능·RSS 비교 실행
- [ ] provider-neutral API와 HTTP adapter Type-A 구현 승인

**최종 상태: `DONE — RESEARCH-2 SERVICE/CONTAINER CONTRACT`**

**판정: `PaddleOCR provider DEFER / self-hosted HTTP service CONDITIONAL / 구현
PENDING`**

