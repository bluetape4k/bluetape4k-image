# Issue #3 이미지 분류 ML backend 평가 연구

- Epic: #513 AI/ML backend 연구 train
- Train 단계: RESEARCH-2
- 선행 train: RESEARCH-1, Issue #169, PR #540
- 조사일: 2026-08-18
- 범위: ONNX Runtime Java, LiteRT/TFLite, DJL, TensorFlow Java, OpenCV DNN, 모델 의미 계약, packaging, native CI
- 결정: **ONNX Runtime Java direct 조건부 ADOPT**
- 구현 상태: 이번 문서는 research 결과만 고정하며 production code와 dependency 추가를 승인하지 않는다.

## 결정 요약

Issue #3의 첫 provider 후보는 ONNX Runtime Java direct다. 다만 지금의 “이미지 분류 API + ImageNet 결과 + DINOv2 + 다운로드/번들” 요구는 runtime 선택만으로 구현할 수 없다. 구체적인 classifier head, labels, preprocessing, model provenance와 native lifecycle을 먼저 고정하는 별도 Type-A 설계가 필요하다.

| 후보 | 판정 | 이유 |
| --- | --- | --- |
| ONNX Runtime Java direct | 조건부 채택 | JVM Java/Kotlin API, CPU/GPU artifact 분리, session lifecycle과 실행 provider 경계가 Issue 요구에 가장 직접적으로 맞는다. |
| LiteRT/TFLite | 서버 JVM 보류 | 최신 surface가 Android/edge의 Kotlin/Java와 CompiledModel 중심이다. Android provider가 필요해질 때 별도 평가한다. |
| DJL | 보류 | ORT 위에 engine, model-zoo, translator, remote/local cache 정책을 추가해 초기 public contract가 넓어진다. |
| TensorFlow Java | 첫 provider로 거부 | native artifact와 TensorFlow/SavedModel 결합이 넓고 JVM API stability 보장이 약하다. |
| OpenCV DNN | 첫 provider로 거부 | 별도 OpenCV JNI surface와 native distribution을 추가하며 분류 API보다 범위가 크다. |

이 판정은 #169의 PaddleOCR를 자동으로 JVM service로 만들거나 ORT를 OCR runtime으로 재사용하자는 뜻이 아니다. #169에서 확정한 model provenance/license/checksum, offline cache, native CI tier 정책만 공통 policy로 참고한다. #169는 code compile dependency가 아니라 research ordering/policy dependency다.

## 저장소 경계

현재 저장소는 Java/Kotlin 25를 기본 toolchain으로 사용하고 optional provider module을 settings에 분리한다. barcode API/provider 분리와 이미지 detection의 명시적 runtime/model boundary가 classification에도 적용되어야 한다.

| 현재 경계 | 관찰 | 설계 영향 |
| --- | --- | --- |
| images module | ImmutableImage와 extension 중심의 core | ORT native type와 model download를 core에 넣지 않음 |
| 이미지 detection | production runtime, model download, GPU를 core 밖의 명시적 adapter로 둠 | classifier도 명시적 provider를 받음 |
| settings.gradle.kts | optional provider module을 별도 project로 등록 | API와 ORT implementation을 두 artifact로 분리 |
| BOM | published module을 자동 constraint에 포함 | 새 published module은 BOM·consumer smoke·catalog를 함께 변경 |
| Java/Kotlin toolchain | Java 25 기반 | ORT native smoke와 public ABI를 Java 25 기준으로 검증 |
| Issue #3 요구 | ImageNet 결과, ImmutableImage classify/suspend extension, model bundling/download | 결과 의미와 model manifest를 API보다 먼저 고정 |

권장 module은 다음과 같다.

1. bluetape4k-images-classification-api
   - ImageClassifier
   - ClassificationResult
   - ClassificationOptions
   - ClassifierIdentity와 model manifest의 provider-neutral 부분
   - deterministic fake/test fixture
   - ImmutableImage.classify(classifier, options)
   - ImmutableImage.suspendClassify(classifier, options)
2. bluetape4k-images-classification-onnxruntime
   - model/label 검증
   - preprocessing/postprocessing
   - OrtEnvironment, OrtSession, RunOptions와 execution provider lifecycle
   - native resource ownership과 telemetry

API artifact에는 ORT class, model path, native library path를 public signature로 노출하지 않는다. global singleton classifier도 만들지 않고 extension에 classifier를 명시적으로 전달한다. Session은 inference마다 만들지 않고 model identity별로 재사용하되 close 시 native 자원을 확실히 회수한다.

## ONNX Runtime 조사

### 공식 upstream와 artifact

- ONNX Runtime 최신 release는 v1.29.0이며 2026-08-12 release다.
- Java guide는 Maven Central Java API, OrtEnvironment, OrtSession, SessionOptions, OnnxTensor, RunOptions 흐름을 제공한다.
- CPU와 GPU artifact는 분리된다. GPU execution provider는 CUDA/cuDNN과 platform matrix를 추가한다.
- 조사 중 Maven Central v1.29.0 jar를 직접 측정한 결과 CPU jar는 약 54.4MB, GPU jar는 약 642.3MB였다. CPU 측정 artifact는 Linux x64, Linux aarch64, macOS aarch64, Windows x64 native를 포함하며 GPU artifact는 Linux/Windows x64 계열로 확인했다. 이 측정은 release 문서의 추상 플랫폼 표보다 우선하는 소비자 evidence로 보존하되, 구현 시 exact jar와 checksum을 다시 고정한다.

장점은 JVM에서 모델 session을 직접 관리하고 CPU baseline을 작은 optional implementation으로 제공할 수 있다는 점이다. CUDA 등 execution provider는 같은 session API에 opt-in으로 붙일 수 있어 기본 classpath와 GPU image를 분리할 수 있다.

### 위험

1. native loader와 classpath resource는 Java code처럼 안전하지 않다. artifact와 native file provenance, checksum, license, extraction root를 관리해야 한다.
2. GPU jar가 크고 x64에 제한되므로 default provider로 넣으면 소비자와 CI를 불필요하게 제한한다.
3. ONNX model의 input tensor, opset, preprocessing, labels, classifier head가 API 의미를 결정한다.
4. external-data model과 custom op는 추가 파일·native code·path traversal·ABI 위험을 가져온다.
5. OrtSession과 tensor result가 AutoCloseable이므로 coroutine 예외와 cancellation 경로에서 close를 검증해야 한다.
6. session 수와 intra/inter-op thread pool을 동시에 늘리면 CPU oversubscription과 native RSS가 증가할 수 있다.

v1은 single-file ONNX만 허용하고 external-data, custom op, remote URL model을 거부한다. untrusted model을 이미지 데이터처럼 취급해서는 안 되며, model managed root와 SHA-256 검증을 먼저 통과해야 한다.

## 모델 의미와 ImageNet 계약

ONNX 파일만으로 ImageNet 분류 의미가 완성되지 않는다. 특히 DINOv2는 visual-feature backbone이지 고정된 ImageNet softmax classifier가 아니다. DINOv2를 선택하려면 어떤 exported backbone, classifier head, labels와 training provenance를 사용할지 별도로 결정해야 한다.

repo-owned model manifest에는 최소한 다음을 고정한다.

| 필드 | 의미 |
| --- | --- |
| model id/version | publisher와 artifact identity |
| ONNX opset | runtime compatibility |
| input/output tensor | name, dtype, shape, batch, NCHW/NHWC |
| preprocessing | RGB/BGR, resize, crop, interpolation, mean/std, scale, quantization |
| output semantics | logits/probability, softmax, threshold, top-k |
| labels | class index와 label 파일의 exact order |
| source/provenance | URL, publisher, commit/release |
| license | model과 dataset의 separate license |
| integrity | SHA-256, byte size, optional signature |
| postprocessing | tie-break, deterministic ordering, unknown class policy |

초기 model packaging 정책은 다음과 같다.

- ImageNet weight를 library JAR에 번들링하지 않는다.
- 호출자가 준비한 immutable local path 또는 별도 verified resolver만 허용한다.
- background auto-download와 first-use network는 금지한다.
- test에는 network 없는 tiny synthetic ONNX fixture만 포함한다.
- cache key는 filename/URL이 아니라 model SHA-256, ORT version, provider/EP, architecture 조합으로 만든다.
- model manifest와 labels는 application 배포 artifact에서 versioned receipt로 보존한다.

## API·수명주기 설계

provider-neutral API는 ORT에 종속되지 않는 sealed/result-oriented contract로 설계한다.

- 입력은 ImmutableImage와 명시적 classifier/options다.
- ClassificationResult는 class id, label, score, rank와 model identity를 immutable하게 반환한다.
- top-k, score threshold, maximum result count를 overflow-safe하게 제한한다.
- 결과 ordering과 floating-point tie-break를 deterministic하게 고정한다.
- malformed image, model mismatch, unsupported operator, limit exceeded, provider failure를 안정적 reason code로 분류한다.
- suspend extension은 blocking/native call을 적절한 dispatcher로 이동하되, interrupt가 native inference를 즉시 중단한다고 주장하지 않는다.
- 가능하면 RunOptions termination과 coroutine cancellation race를 검증하고, hard abort가 불가능한 provider는 cooperative cancellation의 범위를 문서화한다.
- session/model close는 cancellation과 exception을 포함한 모든 path에서 보장한다.

public API에는 OrtEnvironment, OrtSession, Path, URL, native library directory를 직접 노출하지 않는다. 이 경계를 지켜야 향후 LiteRT나 다른 provider가 별도 module로 추가될 수 있다.

## 대안 평가

### LiteRT/TFLite

LiteRT 최신 release는 v2.2.0, 2026-08-13, Apache-2.0이다. 공식 modern CompiledModel 문서와 Kotlin/Java surface는 Android context/assets/accelerator를 중심으로 한다. Interpreter는 backward compatibility 경로이며 새 기능의 중심이 아니다. 현재 서버 JVM과 Java 25 library의 첫 provider로는 runtime과 API portability를 증명하기 어렵기 때문에 DEFER한다.

향후 Android/edge 제품이 범위에 들어오면 images-classification-api를 재사용하고 별도 LiteRT Android provider를 평가한다. 서버 JVM provider와 하나의 artifact로 합치지 않는다.

### DJL

DJL은 ORT engine을 포함한 고수준 translator/model-zoo와 local/remote cache를 제공한다. 여러 engine이 실제 요구라면 생산성이 있지만, 현재 저장소가 먼저 해결해야 할 문제는 model semantics와 native lifecycle이다. DJL을 끼우면 dependency graph, download policy, translator contract가 늘어나므로 첫 provider로 DEFER한다.

### TensorFlow Java

TensorFlow Java는 Java API와 native runtime을 제공하지만 TensorFlow/SavedModel 의미가 Issue #3의 ONNX 요구보다 넓다. 공식 JVM API stability 경계와 platform artifact matrix를 별도로 관리해야 하므로 첫 provider로 거부한다.

### OpenCV DNN

OpenCV DNN은 Java JNI와 OpenCV native distribution을 추가한다. 이미지 전처리 유틸리티로는 유용하지만 분류 provider의 최소 경계보다 범위가 크고, OpenCV 5의 일부 GPU 경로도 별도 engine 의존성을 가진다. 후속 비교 대상이지 첫 provider가 아니다.

## 성능·CI·운영 검증

| tier | 검증 | 정책 |
| --- | --- | --- |
| PR required | tiny checked-in ONNX fixture, no-network, exact top-k, malformed/model mismatch/limit tests | Python/remote model download 없음 |
| CPU native smoke | Ubuntu x64와 macOS ARM64에서 Java 25 session load/run/close | CPU artifact만 required |
| consumer/BOM | versionless dependency consumer와 BOM constraint resolution | published API compile/runtime smoke |
| Windows periodic | Windows x64 CPU native smoke | PR required가 아닌 scheduled/periodic |
| GPU nightly/manual | CUDA EP 선택, fallback, image size와 driver matrix | self-hosted hardware, required CI 아님 |
| benchmark | cold session load, warm p50/p95/p99, throughput, heap/RSS, thread count | baseline과 artifact로 보존 |

session 하나를 매 inference마다 만들지 않고 model별로 재사용한다. 동시에 session 수를 늘리지 않으며 intra-op/inter-op thread 수, CPU fallback 여부, 실제 선택 execution provider를 결과와 metrics에서 관찰한다. 새 module을 구현할 때 path output, module job, coverage needs, final fail-closed aggregation에 추가해야 한다.

## 보안·개인정보 위협 모델

| 위협 | 방어 |
| --- | --- |
| 악성/교체된 model | managed root, SHA-256, size, publisher/license, optional signature/SBOM |
| ONNX external-data traversal | v1 거부, no-follow path validation, single-file 정책 |
| custom op native code | v1 거부, allowlist가 생긴 뒤 별도 threat review |
| remote URL/first-use download | public API에서 URL 제거, explicit offline local path |
| native library extraction | pinned artifact, checksum, trusted classpath root, no world-writable temp |
| model/image memory bomb | input bytes/dimensions/pixels, batch, top-k/result limits |
| session/thread oversubscription | bounded session count와 fixed thread options |
| 민감 이미지/결과 log | image bytes, labels 전체, local path를 log/metrics label에 넣지 않음 |
| cancellation 중 자원 누수 | RunOptions/close race와 exception path test |

ORT 버전 업데이트는 일반 patch dependency보다 엄격하게 검토한다. ONNX external-data security와 ORT native hardening이 release에 포함될 수 있으므로 model parser와 native loader의 보안 receipt를 함께 갱신한다.

## RESEARCH-1 정책 대조

선행 PR #540의 Issue #169 문서와 다음 정책을 공유한다.

- model provenance/license/checksum과 SBOM/NOTICE를 release evidence에 남긴다.
- library artifact에 큰 model을 자동 번들링하지 않는다.
- first-use network와 background download를 금지하고 offline cache를 기본으로 한다.
- CPU baseline을 required CI로 두고 GPU/native 확장은 gated/nightly로 분리한다.
- provider runtime과 model lifecycle을 core API 밖에 둔다.

PaddleOCR가 HTTP service 후보라는 RESEARCH-1 결론은 ORT classification의 배포 방식까지 결정하지 않는다. ORT는 Java 25 프로세스에 직접 포함할 수 있으므로 direct CPU provider를 별도로 평가한다.

## 최종 판정과 Type-A 구현 gate

**판정: ONNX Runtime Java direct 조건부 ADOPT**

다음 조건을 충족하는 별도 Type-A 구현 이슈와 승인 전에는 Issue #3 구현을 시작하지 않는다.

- [ ] 구체 classifier model/head, labels, publisher, license, source URL, SHA-256, byte size 고정
- [ ] repo-owned preprocessing/postprocessing manifest와 golden top-k fixture 고정
- [ ] images-classification-api와 images-classification-onnxruntime 두 module 경계 승인
- [ ] v1 single-file ONNX, no external-data/custom-op/remote download 계약 승인
- [ ] model/session/native resource ownership과 cooperative cancellation 테스트 계획 승인
- [ ] Ubuntu x64·macOS ARM64 CPU smoke 및 Java 25 BOM consumer 계획 승인
- [ ] CPU memory/thread/session benchmark와 quality corpus 계획 승인
- [ ] #169 공통 model/cache/license/CI policy 링크를 implementation issue에 연결

하나라도 미충족하면 구현은 BLOCKED로 유지한다.

## 장점·단점·대안 요약

| 선택 | 장점 | 단점 |
| --- | --- | --- |
| ORT direct + API/provider 분리 | JVM 호출, CPU baseline, 향후 EP 확장, native 격리 | 두 artifact, model manifest, native CI와 lifecycle 부담 |
| ORT 단일 module | 초기 코드가 적음 | public API와 native runtime 결합, provider 확장과 consumer 격리 악화 |
| LiteRT | Android/edge accelerator 선택지 | 현재 서버 JVM surface와 portability 불명확 |
| DJL | model-zoo/translator 생산성 | dependency/cache/engine graph가 넓어짐 |
| bundled weight | 사용자 setup이 작음 | artifact size, license, update와 공급망 재현성 악화 |
| caller-managed local model | offline·재현성·보안 단순 | 사용자가 model 준비와 receipt를 책임짐 |
| verified resolver | 사용성·cache 일관성 향상 | 다운로드 권한, locking, eviction, mirror 보안이 추가됨 |

## 조사 근거

- [Issue #3](https://github.com/bluetape4k/bluetape4k-image/issues/3)
- [Epic #513](https://github.com/bluetape4k/bluetape4k-image/issues/513)
- [Issue #169 RESEARCH-1](https://github.com/bluetape4k/bluetape4k-image/issues/169)
- [RESEARCH-1 local note](2026-08-18-issue-169-paddleocr-backend-evaluation.md)
- [ONNX Runtime v1.29.0 release](https://github.com/microsoft/onnxruntime/releases/tag/v1.29.0)
- [ONNX Runtime Java guide](https://onnxruntime.ai/docs/get-started/with-java.html)
- [Execution providers](https://onnxruntime.ai/docs/execution-providers/)
- [CUDA execution provider](https://onnxruntime.ai/docs/execution-providers/CUDA-ExecutionProvider.html)
- [ONNX Runtime Java API](https://onnxruntime.ai/docs/api/java/)
- [OrtSession API](https://onnxruntime.ai/docs/api/java/ai/onnxruntime/OrtSession.html)
- [RunOptions API](https://onnxruntime.ai/docs/api/java/ai/onnxruntime/OrtSession.RunOptions.html)
- [Threading performance guide](https://onnxruntime.ai/docs/performance/tune-performance/threading.html)
- [Memory performance guide](https://onnxruntime.ai/docs/performance/tune-performance/memory.html)
- [ONNX external-data security](https://onnx.ai/onnx/repo-docs/ExternalDataSecurity.html)
- [ONNX Runtime CPU artifact 1.29.0](https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime/1.29.0/onnxruntime-1.29.0.jar)
- [ONNX Runtime GPU artifact 1.29.0](https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime_gpu/1.29.0/onnxruntime_gpu-1.29.0.jar)
- [LiteRT v2.2.0 release](https://github.com/google-ai-edge/LiteRT/releases/tag/v2.2.0)
- [LiteRT inference overview](https://ai.google.dev/edge/litert/inference)
- [LiteRT Kotlin/Java API](https://developers.google.com/edge/api/litert/kotlin/java/com/google/ai/edge/litert/package-summary)
- [LiteRT Android guide](https://developers.google.com/edge/litert/android)
- [DJL engine guide](https://djl.ai/docs/engine.html)
- [DJL model zoo](https://docs.djl.ai/master/docs/model-zoo.html)
- [DJL cache management](https://djl.ai/docs/development/cache_management.html)
- [TensorFlow Java release](https://github.com/tensorflow/java/releases/tag/v1.2.0)
- [TensorFlow JVM install](https://www.tensorflow.org/jvm/install)
- [OpenCV DNN Java API](https://docs.opencv.org/5.0/javadoc/org/opencv/dnn/Dnn.html)
- [ONNX Model Zoo status](https://github.com/onnx/models)
- [Torchvision pretrained model policy](https://docs.pytorch.org/vision/stable/models.html)
- [Hugging Face model cards](https://huggingface.co/docs/hub/main/model-cards)
- build.gradle.kts, settings.gradle.kts, bom/build.gradle.kts
- images/src/main/kotlin/io/bluetape4k/images/detection/ImageDetection.kt
- README.md

## Research DoD

- [x] Issue #3, #169, #513 live 상태와 stacked 순서 확인
- [x] current Java/Kotlin toolchain, optional module, BOM, detection boundary 확인
- [x] ONNX Runtime, LiteRT, DJL, TensorFlow Java, OpenCV DNN 공식 자료 조사
- [x] 가능성, 위험성, 장단점, 대안과 runtime/platform/license 비교
- [x] model semantics, packaging, cache, security, cancellation, native CI 계약 도출
- [x] #169 공통 policy와 #3 독립 decision boundary 기록
- [x] production code와 dependency mutation 없음
- [ ] 구체 classifier head/labels/model receipt
- [ ] quality corpus 및 benchmark
- [ ] native CPU/GPU smoke

최종 상태: **RESEARCH-2 DONE / ONNX Runtime Java direct 조건부 ADOPT / Type-A 구현 PENDING**
