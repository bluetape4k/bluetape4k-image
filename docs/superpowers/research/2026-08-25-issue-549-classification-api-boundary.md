# Issue #549 provider-neutral ImageClassifier API·ONNX 모듈 경계 연구

- Epic: [#513 AI/ML backend 연구 train](https://github.com/bluetape4k/bluetape4k-image/issues/513)
- 하위 epic: [#3 이미지 분류 ONNX backend 도입 검증 train](https://github.com/bluetape4k/bluetape4k-image/issues/3)
- 선행 문서: [#543 공통 공급망 정책](../research/2026-08-19-issue-543-ai-ml-supply-chain-policy.md), [#548 model manifest·provenance](2026-08-25-issue-548-model-manifest-provenance.md)
- 대상 issue: [#549 provider-neutral ImageClassifier API 및 ONNX 모듈 경계](https://github.com/bluetape4k/bluetape4k-image/issues/549)
- 조사일: 2026-08-25
- 변경 유형: Type-E research/design
- 범위: provider-neutral API, 선택적 ONNX Runtime provider, 호환성·fixture·CI 경계를 설계한다. 이번 문서에서는 Kotlin production source, dependency, module, model, native runtime을 변경하지 않는다.

## 결정 요약

`#549`는 ONNX Runtime을 `images` core에 넣는 구현 issue가 아니다. `ImmutableImage`를
입력으로 받는 공통 classifier 계약과, ORT/JNI/native session을 별도 provider
artifact에 가두는 Type-A 구현 입력을 고정하는 문서 issue다.

| 질문 | 이번 연구의 결론 |
|---|---|
| 공통 API의 입력 | `ImmutableImage`와 명시적인 `ClassificationOptions`를 받는다. provider가 image bytes를 어떻게 인코딩하는지는 public 계약에 넣지 않는다. public timeout은 Java-friendly `java.time.Duration`을 사용한다. |
| 결과 의미 | `ClassificationResult`는 model identity, class index, label, rank, `0.0..1.0` confidence를 반환한다. confidence 내림차순, 동률은 class index 오름차순으로 정렬한다. |
| 동기·suspend surface | Java-friendly blocking `ImageClassifier.classify`를 기본 계약으로 두고 `ImmutableImage.classify`와 `suspendClassify` extension을 제공한다. suspend가 native inference를 즉시 중단한다고 주장하지 않는다. |
| provider 경계 | `bluetape4k-images-classification-api`와 `bluetape4k-images-classification-onnxruntime`을 분리한다. API public signature에는 `OrtEnvironment`, `OrtSession`, `OnnxTensor`, JNI path, `NDArray`를 노출하지 않는다. |
| 모델 의미 | #548의 manifest가 model bytes, labels, preprocessing, postprocessing, license/provenance의 기준 원본이다. model path/URL/자동 다운로드는 API에서 제거한다. |
| JSON·serialization | Jackson 3를 private implementation-only codec의 기본값으로 사용한다. `kotlinx.serialization`, mapper, polymorphic class name, default typing은 common public API에 넣지 않는다. |
| ORT 판정 | ONNX Runtime Java direct는 조건부 `ADOPT` 후보다. #550 native/BOM/CI와 #551 adoption gate 전에는 dependency·module 구현을 시작하지 않는다. |

현재 상태는 `RESEARCH_ONLY / API_DESIGN_PENDING / IMPLEMENTATION_BLOCKED`다.

## 저장소 source ledger

| 근거 | 현재 관찰 | #549 설계 영향 |
|---|---|---|
| `images/src/main/kotlin/io/bluetape4k/images/ImmutableImageSupport.kt`와 지원 extension | 이미지 core가 불변 이미지 연산과 명시적 extension 진입점을 소유한다. | classifier는 core에 추가하지 않고 별도 API module에서 extension을 제공한다. |
| `images/src/main/kotlin/io/bluetape4k/images/detection/ImageDetection.kt` | detector interface와 result가 provider-neutral이며 runtime/model 선택은 외부에서 주입한다. | classifier도 명시적 provider와 identity를 받고 global singleton을 만들지 않는다. |
| `images-barcode-api` / `images-barcode-zxing` | provider-neutral contract와 ZXing implementation이 artifact로 분리되어 있다. | classification API와 ORT implementation의 분리·dependency 소유 모델을 재사용한다. |
| `images-ocr` 및 #546 계획 | blocking contract, suspend bridge, `CancellationException` 재전파, `Dispatchers.IO`, limits와 cleanup을 문서화했다. | classifier에도 같은 cancellation·resource lifecycle gate를 적용하되 ORT 특유의 `RunOptions` 종료 한계를 명시한다. |
| `images/src/main/kotlin/io/bluetape4k/images/privacy/PrivacyDerivativeJackson.kt` | Jackson 3 private strict codec이 implementation 내부에 있다. | deterministic JSON/golden fixture는 Jackson 3를 사용하되 public API에는 mapper/type을 노출하지 않는다. |
| `settings.gradle.kts`, `bom/build.gradle.kts` | published project와 BOM constraint 수집이 분리되어 있다. | 후속 Type-A PR에서 settings·BOM·catalog·consumer smoke·CI fan-out을 한 단위로 갱신한다. |
| `docs/superpowers/research/2026-08-25-issue-548-model-manifest-provenance.md` | ResNet50-v1-12는 기준 후보일 뿐이며 license·golden inference·attestation은 PENDING이다. | API가 특정 model head/label을 암묵적으로 가정하지 않고 manifest identity를 받도록 한다. |
| [#549 live issue](https://github.com/bluetape4k/bluetape4k-image/issues/549) | public API/module/dependency 구현을 명시적으로 제외한다. | 이 train의 changed paths는 문서와 검증 receipt로 제한한다. |

## 공식 ONNX Runtime 근거

2026-08-25에 공식 자료를 다시 읽었다. 아래는 구현 승인 증거가 아니라 API·lifecycle
경계를 설계하는 source evidence다.

| 공식 자료 | 확인한 사실 | 설계상의 제한 |
|---|---|---|
| [ONNX Runtime Java guide](https://onnxruntime.ai/docs/get-started/with-java.html) | JVM Java binding은 `OrtEnvironment`를 만들고 model path/bytes로 `OrtSession`을 만든 뒤 `run` 결과를 읽는 흐름을 제공한다. CPU `onnxruntime`와 GPU `onnxruntime_gpu` artifact가 분리된다. | CPU provider를 기본 후보로 두고 GPU artifact와 CUDA/cuDNN matrix를 optional/nightly로 분리한다. |
| [OrtEnvironment API](https://onnxruntime.ai/docs/api/java/ai/onnxruntime/OrtEnvironment.html) | `OrtEnvironment`는 JVM lifetime에 하나만 생성되는 host object이며 `close()`는 no-op이다. session을 생성하고 available execution provider를 조회할 수 있다. | API에서 environment ownership을 노출하지 않고 provider가 process-global lifecycle과 thread policy를 관리한다. |
| [OrtSession API](https://onnxruntime.ai/docs/api/java/ai/onnxruntime/OrtSession.html) | `OrtSession`과 `OrtSession.Result`는 `AutoCloseable`이며 input/output info, `run`, `close`를 제공한다. | session/result/tensor close를 모든 정상·예외·cancellation 경로에서 검증하고 public result로 ORT object를 반환하지 않는다. |
| [SessionOptions API](https://onnxruntime.ai/docs/api/java/ai/onnxruntime/OrtSession.SessionOptions.html) | `SessionOptions`는 `AutoCloseable`이고 생성된 session이 사용하는 동안 먼저 닫으면 안 된다. | provider close 순서를 `session 종료 → SessionOptions 종료`로 고정하고, 이 순서와 in-flight close race를 fixture로 검증한다. |
| [RunOptions API](https://onnxruntime.ai/docs/api/java/ai/onnxruntime/OrtSession.RunOptions.html) | `RunOptions`는 logging과 incomplete run termination을 제어하고 `AutoCloseable`이다. | suspend cancellation은 cooperative termination 범위로 문서화하며 hard abort를 일반 API 보장으로 승격하지 않는다. |
| [Kotlin inline/value class Java interop](https://kotlinlang.org/docs/inline-classes.html) | Kotlin value class는 Java 호출 surface에서 boxing/name mangling 고려가 필요하다. | public options의 timeout은 `kotlin.time.Duration` 대신 `java.time.Duration`으로 고정하고 Java non-null fixture를 요구한다. |
| [v1.29.0 release](https://github.com/microsoft/onnxruntime/releases/tag/v1.29.0) | 2026-08-12 release에 external-data path validation과 operator input validation 관련 보강이 기록되어 있다. | ORT upgrade마다 native security receipt와 model parser compatibility를 다시 검토한다. 현재 문서에서 version pin이나 dependency 추가는 하지 않는다. |

## provider-neutral 계약의 판단 기준

### 입력과 identity

공통 classifier는 호출자가 준비한 `ImmutableImage`와 명시적 options를 받는다. API가
`Path`, `URL`, `ByteBuffer`, `OrtSession`, native library path, credential을 받으면
provider-specific transport와 공급망 정책이 common contract로 올라오므로 금지한다.

`ClassifierIdentity`는 최소한 다음을 갖는다.

- `providerId`: `onnxruntime` 같은 provider 식별자
- `modelId`, `modelVersion`: #548 manifest의 stable identity
- `manifestSha256`: model/labels/preprocess/postprocess 계약을 묶는 digest
- `capabilities`: `supportsTopK`, `supportsBatch`, `supportsTimeout`, `confidenceSemantics`를 설명하는 `ClassifierCapabilities` immutable 값

identity에는 local path와 mutable URL을 넣지 않는다. 동일 model bytes라도 labels,
preprocess, postprocess가 바뀌면 다른 manifest identity로 취급한다.

### 결과와 score semantics

provider가 logits를 반환하더라도 common result의 `confidence`는 `0.0..1.0` 범위의
확률 의미로 정규화해야 한다. ORT output에 Softmax node가 없으면 provider가 manifest의
postprocess 계약에 따라 변환한다. raw tensor, `FloatBuffer`, provider label object는
public result로 전파하지 않는다.

정렬은 다음으로 고정한다.

1. confidence 내림차순
2. confidence가 같은 경우 `classIndex` 오름차순
3. `rank`는 1부터 연속적으로 부여

`topK`, `minConfidence`, `maxResults`가 결과를 제한하며, NaN·무한대·음수 class
index·빈 label·중복 rank는 `OUTPUT_INVALID`로 거부한다. provider가 confidence를
계산하지 못하면 성공한 빈 결과로 위장하지 않고 capability mismatch 또는 output
invalid를 반환한다.

### limits와 오류

공통 options는 low-cost preflight에서 검증할 수 있는 값만 보유한다.

- `topK`: `1..100` hard cap
- `minConfidence`: 유한한 `0.0..1.0`
- `maxPixels`: decoded image pixel upper bound
- `maxResults`: `topK` 이하의 bounded result count
- optional deadline: provider가 실제로 적용할 수 있는 전체 wall-clock 제한

stable error reason은 `INVALID_INPUT`, `INPUT_DECODE_FAILED`, `LIMIT_EXCEEDED`,
`MODEL_UNAVAILABLE`, `MODEL_MISMATCH`, `UNSUPPORTED_CAPABILITY`, `OUTPUT_INVALID`,
`NATIVE_RUNTIME_UNAVAILABLE`, `INFERENCE_FAILED`로 제한한다. `CancellationException`은
`INFERENCE_FAILED`로 감싸지 않고 먼저 재전파한다. 원본 image bytes, model path,
credential, native stack trace를 public message나 metric label에 넣지 않는다.

### suspend·cancellation·lifecycle

blocking `classify`는 Java caller와 fake provider가 사용할 수 있는 기준 surface다.
`suspendClassify`는 `Dispatchers.IO`에서 blocking/native call을 실행하고 시작 전에
coroutine cancellation을 확인한다. provider는 `RunOptions` 종료를 사용할 수 있지만,
native inference가 즉시 중단되지 않는 환경에서는 cancellation이 “호출자 관찰 중단”과
“native 작업 종료”를 분리해 기록한다.

다음 invariant를 후속 fixture에 넣는다.

- 정상, `OrtException`, output validation failure, coroutine cancellation에서
  `OrtSession.Result`, tensor, temporary buffer가 닫힌다.
- `ImageClassifier`는 `AutoCloseable`이고 close 이후 새 호출을 거부한다. provider
  lifetime에서 `SessionOptions`를 먼저 만들고 verified model identity별 session을
  재사용하되, 모든 session을 닫은 뒤에만 `SessionOptions`를 닫는다.
- 호출마다 `OnnxTensor`, `RunOptions`, `OrtSession.Result`, result에서 꺼낸 pinned output을
  각각 소유하고 명시적으로 닫는다. `Result.close()`가 pinned output을 닫는다고 가정하지 않는다.
- close와 in-flight cancellation race에서 새 호출 차단, bounded wait/cleanup, close 이후
  재사용 거부를 fixture로 검증한다.
- global `OrtEnvironment`를 public singleton으로 제공하지 않는다. provider 내부
  lifecycle은 `use {}` 또는 명시적 `close()`로 책임을 가진다.
- `CancellationException`은 broad `RuntimeException` catch보다 먼저 재전파한다.

## API와 module 대안 비교

| 선택지 | 장점 | 단점·위험 | 이번 판정 |
|---|---|---|---|
| `ImageClassifier` + 별도 ORT provider | JVM/Kotlin caller와 native runtime 경계를 분리하고 CPU baseline·향후 EP 확장이 가능하다. | module 두 개, model manifest, native CI와 lifecycle 테스트가 필요하다. | 권장 설계, #550/#551 전제 |
| `images` core에 ORT 직접 추가 | 처음에는 호출 코드가 짧다. | core classpath, public ABI, native loader, model policy가 결합된다. | 거부 |
| API와 ORT를 한 artifact로 합침 | publish graph가 단순해 보인다. | LiteRT/DJL 등 대체 provider를 추가할 때 consumer가 native dependency를 강제로 받는다. | 거부 |
| LiteRT/TFLite JVM provider | Android/edge accelerator 선택지가 있다. | 현재 서버 JVM·Java 25 library와 실행 surface가 다르고 Android context가 필요하다. | 서버 JVM은 DEFER |
| DJL adapter | translator/model-zoo/cache 생산성이 있다. | engine·translator·remote cache graph가 common API에 들어온다. | 첫 provider는 DEFER |
| TensorFlow Java/OpenCV DNN | 기존 ecosystem에서 활용 가능한 runtime이 있다. | native distribution과 model semantics가 ORT 요구보다 넓다. | 첫 provider는 REJECT/후속 평가 |
| Python/CLI/JNI를 common API에 직접 포함 | 기존 모델 재사용이 쉽다. | process/ABI/allocator/security/timeout이 public contract로 전파된다. | REJECT |

## 보안·공급망·운영 위험

| 위험 | 조기 거부 또는 완화 |
|---|---|
| mutable URL/tag가 다른 model bytes를 반환 | API에 URL을 넣지 않고 #548 manifest의 managed local artifact와 SHA-256만 허용 |
| external-data와 path traversal | v1 single-file ONNX만 허용하고 external-data/custom op는 `UNSUPPORTED_CAPABILITY` 또는 model validation failure로 거부 |
| native library/classpath extraction 변조 | ORT artifact version·checksum·license/SBOM을 provider release receipt로 고정하고 world-writable temp를 사용하지 않음 |
| input/model memory bomb | encoded bytes·pixels·batch·top-k·result count hard cap을 edge와 provider 양쪽에서 재검증 |
| session/thread oversubscription | model identity별 bounded session과 명시적인 intra/inter-op thread policy, RSS/thread benchmark |
| 민감 image/result log 노출 | image bytes, local path, 전체 labels를 log/metric label에서 제거하고 identity/digest prefix만 기록 |
| silent fallback | capability mismatch와 provider failure를 stable reason으로 반환하고 다른 provider로 자동 전환하지 않음 |

## 후속 acceptance gate

다음 조건을 모두 통과하기 전까지 이 연구는 production adoption을 승인하지 않는다.

- [ ] #548 model manifest의 actual bytes/labels/preprocess/postprocess/license receipt 연결
- [ ] `bluetape4k-images-classification-api` public signature와 Java source/binary compatibility fixture
- [ ] API module의 ORT/JNI/NDArray dependency leakage 검사
- [ ] fake provider와 Jackson 3 canonical JSON/golden fixture
- [ ] `ClassifierCapabilities`와 `java.time.Duration` timeout을 Java compile fixture로 검증
- [ ] `bluetape4k-images-classification-onnxruntime`의 `SessionOptions → OrtSession → RunOptions/result/pinned-output` close와 cancellation race 설계
- [ ] #550 Ubuntu x64·macOS ARM64 CPU smoke, Java 25 native access, BOM/consumer smoke
- [ ] #551 동일 corpus quality·latency·RSS·SBOM/NOTICE/license·adoption decision

하나라도 미완료이면 상태는 `IMPLEMENTATION_BLOCKED`다. #551이 `ADOPT`를 반환한
뒤에만 별도의 Type-A implementation epic과 stacked PR을 생성한다.

## 조사 원장

| 근거 | 용도 |
|---|---|
| [#549 live issue](https://github.com/bluetape4k/bluetape4k-image/issues/549) | public API/module/dependency 구현 제외와 완료 조건 |
| [#3 live child epic](https://github.com/bluetape4k/bluetape4k-image/issues/3) | #548 → #549 → #550 → #551 dependency order와 ORT 조건부 ADOPT |
| [#513 main epic](https://github.com/bluetape4k/bluetape4k-image/issues/513) | research 후 ADOPT 때만 Type-A implementation train을 여는 상위 정책 |
| [#548 research](2026-08-25-issue-548-model-manifest-provenance.md) | model identity, manifest, SHA/license/cache/fail-closed 계약 |
| [#3 research](2026-08-18-issue-3-image-classification-ml-backend-evaluation.md) | ORT direct 대안 평가, native/CI와 lifecycle 위험 |
| [ONNX Runtime Java guide](https://onnxruntime.ai/docs/get-started/with-java.html) | Java binding, CPU/GPU artifact, session 실행 흐름 |
| [OrtEnvironment API](https://onnxruntime.ai/docs/api/java/ai/onnxruntime/OrtEnvironment.html) | JVM singleton과 session creation/EP 관찰 |
| [OrtSession API](https://onnxruntime.ai/docs/api/java/ai/onnxruntime/OrtSession.html) | AutoCloseable session/result, input/output, run 계약 |
| [RunOptions API](https://onnxruntime.ai/docs/api/java/ai/onnxruntime/OrtSession.RunOptions.html) | cooperative run termination과 cleanup 범위 |
| [ONNX Runtime v1.29.0 release](https://github.com/microsoft/onnxruntime/releases/tag/v1.29.0) | native security/input validation release note 확인 |

이번 조사에서는 외부 model bytes나 artifact를 저장소에 내려받지 않았다. 따라서
workspace wiki에 binary asset을 보존하는 별도 단계는 발생하지 않았고, 결정에 필요한
내용은 source URL·retrieval date·repo-local 문서로 보존했다.

## Writer DoD

- `SPW-01`: PASS — #549 독자·질문·범위, #548/#3/#513 source ledger와 미지원 주장을 고정했다.
- `SPW-02`: PASS — research 결과, API 판단 기준, 대안, 위험, acceptance gate를 연결했다.
- `SPW-03`: PASS — 한국어 기술 문체를 사용하고 API·command·URL·version·reason code를 그대로 보존했다.
- `SPW-04`: PASS — 현재 저장소 패턴과 공식 ORT 문서를 source-to-claim 표로 연결하고 확인하지 않은 runtime/license 항목을 PENDING으로 남겼다.
- `SPW-05`: PASS — 문서 read-back에서 조건부 ADOPT를 production 승인으로 승격하지 않았다.

최종 상태: `RESEARCH COMPLETE / API DESIGN INPUT READY / IMPLEMENTATION BLOCKED`
