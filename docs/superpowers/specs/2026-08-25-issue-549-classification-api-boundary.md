# Issue #549 ImageClassifier provider-neutral API 설계

## 설계 상태

| 항목 | 값 |
|---|---|
| 대상 | [#549](https://github.com/bluetape4k/bluetape4k-image/issues/549) |
| 선행 | [#548 model manifest·provenance](../research/2026-08-25-issue-548-model-manifest-provenance.md) |
| 후속 | [#550 native/platform·BOM·CI](https://github.com/bluetape4k/bluetape4k-image/issues/550), [#551 adoption gate](https://github.com/bluetape4k/bluetape4k-image/issues/551) |
| 변경 유형 | Type-E 설계 문서 |
| 구현 상태 | Kotlin source, public API, dependency, module, model, native runtime 변경 없음 |
| 결정 상태 | 후속 Type-A 구현 입력; #551 ADOPT 전 구현 금지 |

이 문서는 예시 API shape와 invariant를 고정한다. 아래 Kotlin 조각은 production
source가 아니며, 후속 Type-A 구현 PR에서 package·class name·binary surface를 다시
검증해야 한다.

## 목표와 비목표

### 목표

1. `ImmutableImage`를 사용하는 Java/Kotlin caller가 provider를 명시적으로 선택할 수 있는 공통 classifier 계약을 정의한다.
2. top-k, confidence, label, deterministic ordering, stable failure reason, limits를 provider 간 동일한 의미로 고정한다.
3. blocking/native inference와 coroutine `suspend` bridge의 cancellation·dispatcher·resource lifecycle을 분리해 설명한다.
4. API artifact와 ORT provider artifact의 dependency·BOM·Java 25·CI·consumer smoke 경계를 후속 PR 단위로 분해한다.
5. fake provider, Jackson 3 private codec, Java/Kotlin compatibility fixture와 golden result를 설계한다.

### 비목표

- 이 issue에서 `ImageClassifier`, module, ORT dependency 또는 model을 구현하지 않는다.
- DINOv2 backbone을 ImageNet classifier로 간주하지 않는다. classifier head·labels·preprocess는 #548 manifest가 먼저 고정한다.
- model JAR bundling, first-use/background download, remote URL, Python/CLI/JNI transport를 common API에 넣지 않는다.
- GPU/CUDA를 CPU required baseline으로 만들지 않는다.
- public API에 `OrtEnvironment`, `OrtSession`, `OnnxTensor`, `Path`, `URL`, `NDArray`, mapper type을 노출하지 않는다.

## module 경계

    bluetape4k-images
            ▲
            │ public input + ImmutableImage extensions
            bluetape4k-images-classification-api
            ▲
            │ provider-neutral contract only
    bluetape4k-images-classification-onnxruntime  ── implementation ── com.microsoft.onnxruntime:onnxruntime

### `bluetape4k-images-classification-api`

- `ImageClassifier`, `ClassifierCapabilities`, `ClassifierIdentity`, `ClassificationOptions`, `ClassificationResult`, `ClassificationPrediction`, `ClassificationFailureReason`을 소유한다.
- `bluetape4k-images`의 `ImmutableImage`를 입력으로 받는 public extension을 제공한다.
- ORT/JNI/native/model path/remote URL dependency는 없다.
- fake provider와 deterministic fixture는 test source 또는 별도 non-published test fixture로 둔다.
- public DTO는 workspace 규칙에 따라 `Serializable` marker와 `serialVersionUID`를 가져야
  하지만 새 persisted Java object-stream format을 만들지 않는다. JSON fixture는 codec
  모듈이 소유한다.

### `bluetape4k-images-classification-onnxruntime`

- #548 manifest를 검증한 local single-file ONNX model을 provider 내부에서 로드한다.
- `OrtEnvironment`, `OrtSession`, `SessionOptions`, `RunOptions`, tensor/result ownership을 모두 이 module에 가둔다.
- preprocessing/postprocessing은 manifest 계약을 실행하지만 ORT class를 API result로 반환하지 않는다.
- CPU artifact를 기본 provider 후보로 두고 GPU EP는 별도의 optional configuration/nightly surface로 둔다.
- Java 25/Kotlin 25 toolchain과 `--enable-native-access=ALL-UNNAMED`는 provider test/runtime scope에만 둔다.

### BOM·catalog·settings

후속 Type-A 구현에서만 다음 파일을 같은 PR unit으로 갱신한다.

- `settings.gradle.kts`: 두 project를 dependency order로 등록
- central version catalog: ORT version/CPU alias와 Jackson 3 alias를 중앙 고정
- `bom/build.gradle.kts`: published API/provider artifact constraint 수집 여부 확인
- consumer smoke: versionless dependency와 BOM constraint resolution을 검증
- CI workflow: API required test, CPU native matrix, scheduled Windows/GPU lane을 분리

이번 문서 PR에서는 위 파일을 변경하지 않는다.

## public contract shape

### classifier identity

    data class ClassifierCapabilities(
        val supportsTopK: Boolean,
        val supportsBatch: Boolean,
        val supportsTimeout: Boolean,
        val confidenceSemantics: String,
    ) : java.io.Serializable {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    data class ClassifierIdentity(
        val providerId: String,
        val modelId: String,
        val modelVersion: String,
        val manifestSha256: String,
        val capabilities: ClassifierCapabilities,
    ) : java.io.Serializable {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

불변식:

- 각 문자열은 blank가 아니며 `manifestSha256`는 lowercase hexadecimal SHA-256이다.
- identity에는 path, URL, credential, native library name, mutable cache key를 넣지 않는다.
- 같은 model bytes라도 label order·preprocess·postprocess가 바뀌면 manifest digest가 달라져야 한다.
- provider capability는 `ClassifierCapabilities`로 identity의 immutable metadata에서 읽을 수 있지만 caller가 임의로 바꾸지 못한다.
- `confidenceSemantics`는 공통 결과 계약과 일치하는 `probability` 값이어야 한다.

후속 구현에서는 Java consumer가 사용할 수 있도록 public value class나 inline class를
identity의 필수 key로 사용하지 않는다. 현재 저장소의 Java/Kotlin 25 ABI와 binary
compatibility 도구가 검증할 수 있는 일반 reference type을 우선한다.

### options와 limits

    data class ClassificationOptions(
        val topK: Int = 5,
        val minConfidence: Double? = null,
        val maxResults: Int = 5,
        val maxPixels: Long = 25_000_000,
        val timeout: java.time.Duration? = null,
    ) : java.io.Serializable {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

검증 규칙:

- `1 <= topK <= 100`
- `1 <= maxResults <= topK`
- `maxPixels > 0`이며 공통 hard cap보다 크게 올릴 수 없다.
- `minConfidence`는 null 또는 유한한 `0.0..1.0`이다.
- `timeout`은 null 또는 양수인 `java.time.Duration`이며 provider가 적용하지 못하면 `UNSUPPORTED_CAPABILITY`로 명시한다.
- options는 request마다 immutable 기준 데이터로 사용하고 provider가 값을 수정하지 않는다.

`data class`의 `copy()`가 mutable collection/array를 public으로 노출하는 문제를
만들지 않도록 options에는 array·mutable collection을 넣지 않는다. 후속 결과 DTO에
array가 필요하면 생성자와 getter에서 defensive copy를 사용한다.

### result와 prediction

    data class ClassificationPrediction(
        val classIndex: Int,
        val label: String,
        val confidence: Double,
        val rank: Int,
    ) : java.io.Serializable {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    data class ClassificationResult(
        val classifier: ClassifierIdentity,
        val predictions: List<ClassificationPrediction>,
    ) : java.io.Serializable {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    interface ImageClassifier : AutoCloseable {
        val identity: ClassifierIdentity

        fun classify(
            image: ImmutableImage,
            options: ClassificationOptions = ClassificationOptions(),
        ): ClassificationResult

        override fun close()
    }

결과 invariant:

- `predictions`는 empty일 수 있지만 성공한 result가 unsupported detail을 숨기기 위해 empty를 반환해서는 안 된다.
- `classIndex >= 0`, label은 blank가 아니고, confidence는 유한한 `0.0..1.0`이다.
- confidence 내림차순, 동률은 class index 오름차순이다. `rank`는 1부터 연속적이다.
- 결과 수는 `min(topK, maxResults)`를 넘지 않는다.
- `ClassificationResult`는 provider-specific tensor, native handle, throwable, image bytes를 보유하지 않는다.
- `List`는 생성 후 변경할 수 없는 기준 데이터로 보존한다. Java caller가 변형한 list가 내부 state를 바꾸지 않아야 한다.
- `ImageClassifier.close()` 이후 새 `classify` 호출은 허용하지 않으며, in-flight 호출과 close의
  순서는 provider가 명시적으로 정의하고 fixture로 검증한다.

### extension과 suspend bridge

    fun ImmutableImage.classify(
        classifier: ImageClassifier,
        options: ClassificationOptions = ClassificationOptions(),
    ): ClassificationResult = classifier.classify(this, options)

    suspend fun ImmutableImage.suspendClassify(
        classifier: ImageClassifier,
        options: ClassificationOptions = ClassificationOptions(),
    ): ClassificationResult = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        classifier.classify(this@suspendClassify, options)
    }

위 shape는 의미 계약을 설명하기 위한 예시다. 후속 implementation은 `Dispatchers.IO`
정책과 테스트용 dispatcher seam을 결정하되, public API가 caller의 arbitrary executor나
ORT scheduler를 제어하게 만들지 않는다.

- blocking call은 Java caller와 fake provider가 사용한다.
- suspend bridge는 blocking/native provider를 IO dispatcher로 이동한다.
- 시작 전 cancellation은 즉시 재전파한다.
- 실행 중 cancellation은 provider의 cooperative termination 지원 범위만 보장한다.
- broad `RuntimeException` catch보다 `CancellationException` 재전파가 먼저다.

### failure contract

    enum class ClassificationFailureReason {
        INVALID_INPUT,
        INPUT_DECODE_FAILED,
        LIMIT_EXCEEDED,
        MODEL_UNAVAILABLE,
        MODEL_MISMATCH,
        UNSUPPORTED_CAPABILITY,
        OUTPUT_INVALID,
        NATIVE_RUNTIME_UNAVAILABLE,
        INFERENCE_FAILED,
    }

    class ClassificationException(
        val reason: ClassificationFailureReason,
        message: String,
        cause: Throwable? = null,
    ) : RuntimeException(message, cause) {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

오류 계약:

- `CancellationException`은 `ClassificationException(INFERENCE_FAILED)`로 변환하지 않는다.
- raw native error와 model path는 public message에 넣지 않고 sanitized context만 남긴다.
- capability mismatch, model mismatch, output invalid를 generic inference failure로 합치지 않는다.
- provider 자동 fallback이나 silent downgrade는 하지 않는다.
- enum에 새 reason을 추가할 때 Java `switch` source compatibility와 JSON fixture를 갱신한다.

## model manifest와 provider responsibility

### common contract가 참조하는 값

`ClassifierIdentity.manifestSha256`는 #548 manifest의 다음 내용을 묶는다.

- model bytes size/SHA-256, format, opset, input/output name·dtype·shape
- labels source/ref/count/SHA-256와 zero-based index order
- RGB/BGR, scale, mean/std, resize/crop/interpolation, layout
- logits/probability semantics, softmax, top-k tie-break, unknown class policy
- source/provenance, license, NOTICE, SBOM/attestation 상태

API는 이 내용을 다시 `Map<String, Any>`로 받지 않는다. provider constructor가 검증된
manifest/immutable resolver를 받아야 하며, remote URL이나 opaque map은 구현 module의
typed internal configuration으로 제한한다.

### provider 내부 책임

1. managed root regular-file 확인과 symlink/`..`/external-data/custom-op 거부
2. bytes size와 SHA-256, manifest schema version/digest, labels hash 확인
3. image preprocessing을 manifest와 동일하게 수행
4. provider lifetime에서 `SessionOptions`를 먼저 만들고, 그 수명 안에서 `OrtSession`을
   유지하며, close 시 session을 모두 정리한 뒤 `SessionOptions`를 닫는다.
5. 호출마다 input `OnnxTensor`, `RunOptions`, `OrtSession.Result`, result에서 꺼낸
   pinned output을 각각 소유하고 명시적으로 닫는다. `Result.close()`가 pinned output까지
   닫는다고 가정하지 않는다.
6. cancellation·close race에서 새 호출을 차단하고 in-flight resource의 종료 순서를
   검증한다.
7. raw logits를 common confidence로 변환하고 deterministic ordering을 적용한다.
8. native/provider failure를 stable reason으로 mapping한다.

## serialization·fixture·compatibility

### Jackson 3 private codec

후속 provider/fixture module은 중앙 version catalog의 Jackson 3를 implementation-only
로 사용한다. 아래 DTO의 `Serializable` marker와 `serialVersionUID`는 workspace의
Kotlin data-class 규칙을 따르는 source/ABI 관례이며, 별도의 persisted Java stream format을
이번 issue에서 정의한다는 뜻은 아니다. canonical wire/fixture는 계속 JSON이다.

- canonical UTF-8 JSON, fixed field order, explicit null policy, stable number format
- `schemaVersion=1` 외 버전 거부
- unknown/duplicate field, trailing token, depth/string/array/body size 초과 거부
- default typing과 class-name polymorphic deserialization 금지
- mapper/Jackson DTO/class name을 public `ImageClassifier` signature와 generated POM API에 노출 금지
- fixture 변경 시 canonical JSON SHA-256과 schema digest를 함께 갱신

`kotlinx.serialization`은 common wire의 기본 구현으로 추가하지 않는다. public API가
serialization library보다 오래 유지되도록 codec은 provider/fixture 경계에 둔다.

### fake provider fixture

fake provider는 다음을 deterministic하게 반환한다.

- `ClassifierIdentity(providerId="fake", modelId="fixture", modelVersion="1", manifestSha256=<fixed>)`
- `ClassifierIdentity.capabilities`는 `supportsTopK=true`, `supportsBatch=false`,
  `supportsTimeout=false`, `confidenceSemantics="probability"`로 고정한다.
- 같은 image/options에 대해 고정된 class index/label/confidence
- confidence tie, top-k truncation, minConfidence filtering
- malformed output, capability mismatch, unsupported timeout, cancellation-before-start

fixture는 network, model download, native library를 사용하지 않는다. golden JSON에는
API result와 schema version만 넣고 image bytes·native handle·local path는 넣지 않는다.

### source/binary compatibility

후속 Type-A train의 compatibility gate:

- 기존 `images`와 `images-ocr`/barcode public API를 변경하지 않는다.
- API module Java consumer가 blocking `classify`와 getter를 compile/run할 수 있다.
- Kotlin source fixture가 default options, extension, cancellation semantics를 확인한다.
- `List`/array defensive copy와 result immutability를 확인한다.
- public API diff와 generated POM에서 ORT/JNI/Jackson runtime leakage를 검사한다.
- `Serializable` marker/`serialVersionUID`와 JSON codec을 함께 검사하되, 새 JSON을 Java
  stream 계약으로 취급하지 않고 legacy serialization과 혼동하지 않는다.

## acceptance criteria와 DoD

- [ ] API 계약과 error/limit invariant가 spec과 plan에서 동일하다.
- [ ] ORT/native/model path가 API signature와 API dependency graph에 없다.
- [ ] sync/suspend/fake provider/cancellation fixture가 deterministic하고 classifier close를 검증한다.
- [ ] Jackson 3 private codec와 unknown/duplicate/trailing-token rejection이 fixture로 검증된다.
- [ ] #548 manifest와 preprocessing/postprocessing identity 연결이 traceable하다.
- [ ] #550 native/CPU/BOM/consumer/CI 결과가 이 spec의 PENDING을 갱신한다.
- [ ] #551이 `ADOPT` 또는 `DEFER`를 기록하기 전에는 Type-A 구현을 시작하지 않는다.

이번 문서의 DoD는 설계 입력의 완성도다. 위 checkbox는 후속 implementation/adoption
gate이며 이 PR에서 모두 PASS로 주장하지 않는다.

## Writer DoD

- `SPW-01`: PASS — 대상 reader, API 질문, #548/#550/#551 source와 비목표를 고정했다.
- `SPW-02`: PASS — public contract, module boundary, failure/cancellation, compatibility, acceptance를 포함했다.
- `SPW-03`: PASS — 한국어 기술 register를 적용하고 Kotlin/API/URL/JSON token을 보존했다.
- `SPW-04`: PASS — research→spec traceability와 후속 gate mapping을 read-back했다.
- `SPW-05`: PASS — 예시 Kotlin은 production source가 아님을 명시하고 PENDING을 그대로 유지했다.

최종 상태: `SPEC READY / TYPE-A IMPLEMENTATION PENDING`
