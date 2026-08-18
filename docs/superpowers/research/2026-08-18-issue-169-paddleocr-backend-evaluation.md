# Issue #169 PaddleOCR backend 평가 연구

- Epic: #513 AI/ML backend 연구 train
- Train 단계: RESEARCH-1
- 조사일: 2026-08-18
- 범위: PaddleOCR runtime, 모델, packaging, deployment, license, security, CI
- 결정: **DEFER**
- 구현 상태: 이 문서는 조사 결과만 고정하며 dependency 추가와 production code 변경을 승인하지 않는다.

## 결정 요약

현재 0.5.0 기본 OCR provider는 Tesseract/Tess4J로 유지한다. PaddleOCR는 JVM 안에 Python runtime이나 JNI를 직접 끼워 넣지 않고, 향후 benchmark와 공급망 검증을 통과한 경우에만 별도 self-hosted HTTP service adapter로 재평가한다.

| 대안 | 판정 | 이유 |
| --- | --- | --- |
| Python/Paddle runtime in-process | 거부 | Python/PaddleX native matrix와 모델 cache가 JVM artifact·수명주기 경계를 침범한다. |
| 호출마다 CLI subprocess | 거부 | process cold-start, model reload, timeout/cleanup, stderr 정보 노출이 요청 latency와 운영 안정성을 악화시킨다. |
| JVM JNI binding | 거부 | 안정적인 공식 JVM ABI와 현재 OCR 의미 계약이 없으며 플랫폼별 native 배포 부담이 가장 크다. |
| 장기 실행 HTTP service | 조건부 채택 후보 | 언어·runtime 경계를 분리하고 model lifecycle을 amortize할 수 있지만 auth/TLS와 limits를 adapter perimeter에서 강제해야 한다. |
| Paddle hosted API | 기본값 거부 | 외부 전송·토큰·quota·egress가 offline/self-hosted OCR 요구와 맞지 않는다. |
| gRPC 전용 계약 | 거부 | 확인한 공식 serving 계약은 HTTP이며 별도 gRPC 안정성 근거가 없다. |

DEFER는 PaddleOCR의 품질을 부정하는 판정이 아니다. 현재 저장소에서 재현 가능한 품질·운영·공급망 증거가 없고, 기존 OCR API가 Tess4J에 결합되어 있어 즉시 provider를 추가하는 것이 범위를 넘는다는 의미다.

## 저장소 경계와 현재 baseline

| 경로/계약 | 현재 역할 | PaddleOCR 영향 |
| --- | --- | --- |
| images-ocr | Tess4J/Tesseract 구현과 public OCR API를 함께 제공 | 새 provider가 사용할 독립 API가 아님 |
| OcrEngine | ImmutableImage를 받아 OCR 결과를 반환하는 공통처럼 보이는 진입점 | provider 교체 가능성을 암시하지만 실제 options가 Tesseract에 결합 |
| OcrOptions | ITessAPI, tessdata 경로, Tesseract engine/page mode 노출 | provider-neutral request로 추출해야 함 |
| images-ocr/build.gradle.kts | Tess4J implementation dependency | Paddle runtime을 여기에 추가하면 core/provider 경계가 무너짐 |
| docs/manual/ko/modules/bluetape4k-images-ocr.md | 다른 provider가 OcrEngine을 구현할 수 있다고 설명 | 설명과 실제 options coupling을 함께 정리해야 함 |
| CI | host OCR은 -Docr.enabled=true, container OCR은 -Docr.container.enabled=true로 명시적 gate | Paddle full model은 일반 PR required job에 넣지 않는 편이 안전 |

향후 채택 전 선행 Type-A 이슈는 images-ocr-api의 provider-neutral image/result/options 추출과 images-ocr-tesseract provider 분리를 먼저 다뤄야 한다. Paddle adapter가 Tesseract 전용 ITessAPI 타입을 public API에서 재사용해서는 안 된다.

기존 baseline은 Tesseract 5.5.2와 별도 traineddata 파일이다. Tesseract는 이미 JVM 호출, native/host gate, container smoke가 저장소 운영 경계에 맞춰져 있다. PaddleOCR 채택이 baseline을 대체한다고 가정하지 말고 동일 corpus에서 우월함을 증명해야 한다.

## 공식 upstream 조사

모든 외부 자료는 2026-08-18에 확인했다.

| 항목 | 확인 결과 | 저장소 판단 |
| --- | --- | --- |
| PaddleOCR release | v3.7.0, 2026-06-11. PP-OCRv6와 50개 언어 계열 제공 | 조사 기준 버전은 고정하되 구현 시 다시 release 검증 |
| Python package | paddleocr==3.7.0은 Python >=3.8 및 paddlex[ocr-core]>=3.7.0,<3.8.0 계열 요구 | facade wheel 크기만으로 runtime portability를 주장하지 않음 |
| Inference engine | paddle, paddle_static, paddle_dynamic, transformers, onnxruntime 선택지 제공; 한 환경에는 하나의 engine 권고 | engine 선택은 별도 container image와 lock으로 고정 |
| 공식 serving | paddlex --serve --pipeline OCR, Uvicorn 기본 bind 0.0.0.0:8080, HTTP 요청/JSON 응답 | 기본 bind를 그대로 외부에 노출하지 않음 |
| High Performance Inference | Linux x86-64 및 Python 3.8–3.12 중심, CUDA/cuDNN 조합과 Docker/WSL 조건 별도 | py3-none-any package와 실제 runtime 지원을 혼동하지 않음 |
| model source | 3.0.2부터 기본 source가 Hugging Face이며 PADDLE_PDX_MODEL_SOURCE=BOS로 변경 가능; source connectivity probe와 cache 설정 존재 | build/CI에서 remote probe와 first-use download 금지 |
| PP-OCRv6 model | medium detection/recognition 약 59.4MB/73.3MB, tiny 약 1.9MB/4.4MB; orientation/unwarp model 추가 가능 | model을 JAR에 번들링하지 않고 artifact manifest로 관리 |
| license | PaddleOCR repository와 확인한 PP-OCRv6 model card는 Apache-2.0 | 모든 catalog model/dataset/extra dependency에 license를 전이하지 않음 |
| 평가 수치 | 공식 문서가 v6와 v5/v4 수치는 평가셋이 달라 직접 비교할 수 없다고 경고 | vendor 수치만으로 Tesseract 우위를 주장하지 않음 |

### 설치·runtime의 실제 의미

PaddleOCR Python facade를 설치하는 것과 inference runtime이 준비되는 것은 다르다. PaddleX extra, Paddle/Paddle inference engine, 모델 파일, cache directory, CPU/GPU native library가 함께 맞아야 한다. HPI 문서의 Linux x86-64와 CUDA 조합은 지원 matrix의 일부일 뿐이며 macOS ARM64 또는 Windows native JVM 소비자를 자동으로 보장하지 않는다.

공식 serving은 언어 중립적인 HTTP 경계라는 장점이 있지만 기본 동작을 그대로 product contract로 삼을 수 없다.

- 기본 0.0.0.0:8080 bind는 내부 loopback 또는 private network bind로 제한한다.
- 인증·TLS는 serving 명령 자체가 보장하는 경계가 아니므로 reverse proxy 또는 service mesh가 책임진다.
- binary image/PDF와 URL 입력은 body/page/pixel/time/concurrency limit을 별도로 둔다.
- OCR text를 request log나 model debug log에 남기지 않는다.
- model download와 source probe를 끄고 image가 외부로 나가지 않는 self-hosted/offline 모드를 기본으로 한다.

## 후보 deployment 비교

| 방식 | 장점 | 단점/위험 | 이번 판정 |
| --- | --- | --- | --- |
| JVM에서 Python embedding | 호출 경계가 짧고 API가 단순해 보임 | Python ABI, GIL, native allocator, shutdown, process-wide cache가 JVM lifecycle과 충돌 | 거부 |
| 요청별 subprocess | 격리와 강제 kill 가능 | cold-start와 model reload, process 폭증, stderr/path 노출, 높은 tail latency | 거부 |
| persistent local Python service | model warm-up, concurrency, health check, rollout 분리 | 별도 image와 patching, auth/TLS, network backpressure, observability 필요 | 조건부 |
| JVM HTTP client + sidecar/container | Kotlin/Ktor/Spring에 자연스러운 boundary, provider 교체 가능 | 서비스 배포와 version skew, payload serialization, timeout/circuit-breaker 필요 | 조건부 |
| hosted API | 운영 setup이 작음 | 이미지 외부 전송, token/quota/egress, data residency와 재현성 문제 | 기본 거부 |
| Paddle-to-ONNX 변환 후 JVM | Python server 없이 배포 가능 | 변환 fidelity, unsupported op, preprocessing/postprocessing drift, model license 확인 필요 | 별도 연구 |

채택된다면 JVM 쪽에는 provider-neutral client/adapter만 두고 Paddle runtime은 versioned container/service로 격리한다.

1. service image에 Python, PaddleX, inference engine, model을 lock한다.
2. model과 container digest를 배포 artifact에 기록한다.
3. service는 startup 시 local model manifest와 checksum을 검증하고 network source probe를 하지 않는다.
4. adapter는 loopback/private endpoint, auth/mTLS, connect/read/overall timeout, max body/pages/pixels, bounded concurrency를 강제한다.
5. response는 OCR text와 geometry만 반환하며 raw stack trace, filesystem path, credentials를 외부로 내보내지 않는다.
6. health check는 model identity와 readiness만 노출하고 입력 이미지를 전송하지 않는다.

## 모델·cache·공급망 정책

모델 이름만 지정하는 설정은 재현 가능한 dependency가 아니다. 다음 manifest를 repository 또는 배포 catalog에서 관리해야 한다.

| 필드 | 필수 의미 |
| --- | --- |
| model id/version | 어떤 detector/recognizer/orientation 조합인지 |
| source URL/publisher | provenance와 mirror 추적 |
| SHA-256/byte size | 다운로드·cache integrity |
| model/data/license | 코드 license와 별도로 확인 |
| container digest | Python/Paddle native runtime 고정 |
| supported language/corpus | 품질 범위와 fallback |
| preprocess/postprocess | resize, normalization, threshold, geometry transform |
| cache root | writable scope와 eviction 정책 |
| offline flag | source connectivity와 first-use network 차단 여부 |

운영 기본값은 다음과 같아야 한다.

- library JAR에는 medium/tiny pretrained weight를 번들링하지 않는다.
- model name만으로 background download를 수행하지 않는다.
- cache key를 filename/URL이 아니라 checksum과 model identity 조합으로 만든다.
- cache root 밖의 symlink, traversal, world-writable directory를 fail-closed 한다.
- 모델 artifact, container SBOM, NOTICE, license text를 release evidence에 보존한다.
- 모델 업데이트는 코드 dependency update와 분리하고 digest·benchmark·rollback plan을 함께 검토한다.
- source connectivity probe를 끈 offline mode를 CI와 production 기본 경로로 삼는다.

PaddleOCR repository가 Apache-2.0이어도 catalog의 모든 model, dataset, third-party extra가 동일 license라는 뜻은 아니다. 선택한 det/rec 모델 card와 transitive dependency마다 license와 provenance를 확인해야 한다.

## 품질·성능·운영 검증 계획

PaddleOCR를 채택하려면 Tesseract와 같은 입력 corpus로 다음 결과를 보존해야 한다.

### 정확성

- 한국어·영어·일본어 printed/dense/mixed 문서
- 저해상도, 기울기, background noise, 표와 다단 편집
- CER/WER와 문장 단위 exact match
- bounding box 좌표의 pixel-space 변환 정확성
- 빈 결과, malformed response, partial page, multi-line ordering
- Tesseract와 같은 OcrOptions 의미를 사용하지 않는 provider-neutral fixture

공식 model card benchmark는 corpus와 metric이 다르므로 저장소 품질 결론으로 재사용하지 않는다.

### 성능/자원

- cold-start: container start부터 model-ready까지
- warm latency: 1장, multi-page, max-size input의 p50/p95/p99
- CPU throughput과 bounded concurrency
- RSS/native memory 및 model cache 크기
- timeout, cancellation, retry, circuit-breaker가 만드는 tail latency
- tiny/small/medium model별 품질과 운영 비용

현재 OCR Testcontainers 검증도 PR timeout/retry와 nightly 긴 timeout을 사용한다. full Paddle model을 일반 PR required path에 추가하면 build 시간과 flaky native failure가 커지므로 tier를 분리한다.

| CI tier | 내용 | 정책 |
| --- | --- | --- |
| PR required | fake HTTP contract, schema/error mapping, limits, no-network assertion | 빠르고 deterministic |
| scheduled/manual CPU | pinned tiny/small container와 checksum, 대표 corpus smoke | model cache 사전 준비 |
| nightly benchmark | medium model CER/WER, p95, RSS, cold-start | 결과 artifact와 기준선 비교 |
| GPU/manual | CUDA image와 provider 선택 확인 | self-hosted hardware, required CI 아님 |

## 보안·개인정보 위협 모델

| 위협 | 방어 |
| --- | --- |
| 첫 실행 remote model download/telemetry | model을 image/artifact에 bake하고 source probe를 끈 offline mode |
| 0.0.0.0:8080 무단 OCR | loopback/private bind, reverse proxy auth/mTLS, network policy |
| 대형 image/PDF memory/time bomb | body/page/pixel/timeout/concurrency limits와 bounded queue |
| OCR text/path/native traceback 유출 | stable reason code와 redacted error만 외부 응답 |
| cache directory traversal/symlink | managed root, no-follow, ownership/permission 검증 |
| 모델 교체/악성 artifact | digest/size/license/SBOM/signature(가능한 경우) 검증 |
| 민감 이미지 log/telemetry 유출 | image bytes와 OCR text를 log/metrics label에 넣지 않음 |
| provider failure retry 폭주 | retry budget, circuit breaker, idempotency와 backpressure |

HTTP 자체는 trust boundary가 아니다. service가 내부망에 있어도 인증, TLS, request limit, secret injection, non-root/read-only container를 별도 contract로 고정해야 한다.

## #513 stacked train과 #3의 관계

#513은 #169 → #3 순서의 research train을 요구하지만 #169는 #3의 code compile dependency가 아니다. #169에서 먼저 확정해야 하는 공통 정책은 다음과 같다.

- model provenance/license/checksum
- model download/cache/offline 동작
- CPU/GPU 및 native runtime CI tier
- 큰 runtime/model을 core artifact 밖에 두는 원칙

PaddleOCR가 HTTP service로 결론나더라도 ORT 이미지 분류를 자동으로 service화해서는 안 된다. 각 provider의 runtime 경계와 품질 계약은 독립적으로 판단하되 위 공통 정책은 재사용한다.

## 최종 판정과 재평가 gate

**판정: DEFER**

Tesseract/Tess4J baseline을 유지하고 PaddleOCR는 다음 모든 gate를 만족할 때만 별도 Type-A 구현 issue로 재평가한다.

- [ ] paddleocr==3.7.0 및 paddlex<3.8와 실제 inference engine/container digest 고정
- [ ] 선택 detector/recognizer/orientation model의 URL, SHA-256, size, license, SBOM/NOTICE 고정
- [ ] offline startup과 no-download/no-source-probe 증거
- [ ] 동일 multilingual/noisy/rotated/table corpus에서 CER/WER와 geometry correctness가 Tesseract 대비 명확히 우수
- [ ] CPU cold/warm latency, p95/p99, RSS/native memory, concurrency SLO 충족
- [ ] auth/TLS, no-log, egress deny, body/page/pixel/time limits, redacted error 검증
- [ ] PR contract test와 scheduled CPU container smoke가 deterministic하게 통과
- [ ] provider-neutral OCR API 분리와 migration 문서 승인

하나라도 충족하지 못하면 0.5.0 이후로 계속 보류한다.

## 장점·단점·대안 요약

| 선택 | 장점 | 단점 |
| --- | --- | --- |
| Tesseract baseline 유지 | 현재 JVM/native/container gate와 문서가 재현 가능 | 특정 document layout/언어 품질 개선 여지 |
| Paddle self-hosted HTTP 조건부 | Paddle model/runtime 격리, warm model, JVM provider 확장 | service 운영, auth/TLS, version skew와 image 비용 |
| Paddle in-process | 호출 overhead가 작음 | ABI/GC/GIL/cache/patching 경계가 가장 위험 |
| Paddle hosted API | 빠른 시도 | 개인정보·egress·quota·재현성 문제 |
| Paddle-to-ONNX 후속 | JVM direct 가능성 | 변환 fidelity와 preprocessing drift를 별도 증명 |

## 조사 근거

- [PaddleOCR v3.7.0 release](https://github.com/PaddlePaddle/PaddleOCR/releases/tag/v3.7.0)
- [PaddleOCR v3.7.0 pyproject](https://github.com/PaddlePaddle/PaddleOCR/blob/v3.7.0/pyproject.toml)
- [PaddleOCR installation](https://www.paddleocr.ai/main/en/version3.x/installation.html)
- [Inference engine selection](https://www.paddleocr.ai/main/en/version3.x/inference_deployment/local_inference/inference_engine.html)
- [Serving](https://www.paddleocr.ai/main/en/version3.x/inference_deployment/serving/serving.html)
- [High Performance Inference](https://www.paddleocr.ai/main/en/version3.x/inference_deployment/local_inference/high_performance_inference.html)
- [OCR pipeline and model sizes](https://www.paddleocr.ai/main/en/version3.x/pipeline_usage/OCR.html)
- [Model source update](https://www.paddleocr.ai/latest/en/update/update.html)
- [PaddleX FAQ](https://paddlepaddle.github.io/PaddleX/3.7/FAQ.html)
- [PaddleOCR license](https://github.com/PaddlePaddle/PaddleOCR/blob/v3.7.0/LICENSE)
- [PP-OCRv6 medium detector model card](https://huggingface.co/PaddlePaddle/PP-OCRv6_medium_det_safetensors)
- [PP-OCRv6 medium recognizer model card](https://huggingface.co/PaddlePaddle/PP-OCRv6_medium_rec_safetensors)
- [Tesseract 5.5.2 release](https://github.com/tesseract-ocr/tesseract/releases/tag/5.5.2)
- [Tesseract installation](https://tesseract-ocr.github.io/tessdoc/Installation.html)
- [tessdata repository](https://github.com/tesseract-ocr/tessdata)
- [Issue #169](https://github.com/bluetape4k/bluetape4k-image/issues/169)
- [Epic #513](https://github.com/bluetape4k/bluetape4k-image/issues/513)
- docs/superpowers/research/2026-06-05-issue-1-ocr-research-refresh.md
- docs/superpowers/research/2026-05-29-issue-83-ocr-dependency-model-packaging-research.md
- docs/lessons/2026-05-29-image-ai-research-gates.md

## Research DoD

- [x] Issue #169와 Epic #513을 live 상태로 확인
- [x] 현재 OCR API, Tess4J dependency, CI gate, manual 경계 확인
- [x] 공식 primary source에서 release, runtime, serving, model, cache, license 확인
- [x] 가능성, 위험성, 장단점, 대안 비교
- [x] adopt/defer/reject와 #3에 전달할 공통 policy 도출
- [x] 코드 및 dependency mutation 없음
- [ ] 실제 대표 corpus benchmark
- [ ] 선택 model digest/SBOM receipt
- [ ] service container CPU smoke

최종 상태: **RESEARCH-1 DONE / PaddleOCR DEFER / 구현 PENDING**
