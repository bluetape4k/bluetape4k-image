# Issue #85 image classification dependency 및 model packaging 연구

- 이슈: [#85](https://github.com/bluetape4k/bluetape4k-image/issues/85)
- 구현 대상: [#3](https://github.com/bluetape4k/bluetape4k-image/issues/3)
- 날짜: 2026-05-29
- 범위: image classification runtime, ONNX/model packaging, module boundary, CI 및 API handoff.

## 요약

ONNX Runtime Java를 직접 사용하는 별도 `bluetape4k-images-classification` module로 진행한다. 첫 구현은 CPU-only와 model-file driven으로 유지한다. published artifact에는 ImageNet 규모의 model weight를 bundle하지 않고, automatic remote model download도 추가하지 않는다.

ONNX Runtime은 상위 ML framework보다 첫 구현에 더 적합하다. issue가 ONNX Runtime을 명시적으로 요구하고, public API를 작게 유지할 수 있으며, model/preprocessing contract를 model zoo abstraction 뒤에 숨기지 않고 이 library가 versioning할 수 있기 때문이다.

## 현재 repository 적합성

- repository는 이미 optional runtime을 전용 module로 분리한다. Classification도 inference dependency를 `bluetape4k-images`에 추가하지 않고 같은 pattern을 따른다.
- 기존 image pipeline은 resized/cropped/encoded pixel을 제공할 수 있지만, classification에는 input size, color order, normalization, label mapping 같은 명시적인 preprocessing metadata가 필요하다.
- core module의 baseline은 Java 21이다. ONNX Runtime Java는 JVM 사용을 지원하지만, full cross-platform coverage를 주장하기 전에 CI에서 platform-native support를 검증해야 한다.

## 후보 평가

| 후보 | 결정 | 근거 |
|---|---|---|
| ONNX Runtime Java direct API | 권장 | #3에 대해 dependency surface가 가장 작고, issue requirement와 맞으며, 명시적인 session/options lifecycle을 지원한다. |
| DJL with ONNX Runtime engine | 보류 | 여러 engine 또는 model zoo 지원이 목표가 되면 유용하지만, 첫 classification module에는 너무 넓다. |
| TensorFlow Java | #3에서는 제외 | ecosystem 선택이 더 무겁고 ONNX Runtime requirement와 맞지 않는다. |
| TensorFlow Lite / LiteRT Java | server-first module에서는 제외 | Android/edge deployment에는 더 적합하지만, 이 repo의 JVM server packaging에는 덜 직접적이다. |
| OpenCV DNN | classification first pass에서는 제외 | detection issue #84/#2에 더 적합하다. ONNX Runtime이 classifier lifecycle을 더 명확하게 만든다. |

## 권장 module boundary

다음을 추가한다:

```text
images-classification/
  artifact: io.github.bluetape4k.image:bluetape4k-images-classification
  package: io.bluetape4k.images.classification
```

의존성:

- `api(project(":bluetape4k-images"))`
- `implementation(com.microsoft.onnxruntime:onnxruntime)`
- `implementation(libs.kotlinx.coroutines.core)`
- JUnit 5 및 bluetape4k assertion용 test dependency

권장 public API:

```kotlin
interface ImageClassifier : AutoCloseable {
    fun classify(image: ImmutableImage, options: ClassificationOptions = ClassificationOptions()): List<ClassificationResult>
    suspend fun classifySuspend(image: ImmutableImage, options: ClassificationOptions = ClassificationOptions()): List<ClassificationResult>
}
```

```kotlin
data class ClassificationModel(
    val modelPath: Path,
    val labelsPath: Path,
    val inputName: String? = null,
    val inputSize: ImageInputSize,
    val colorOrder: ColorOrder = ColorOrder.RGB,
    val normalization: Normalization = Normalization.ImageNet,
) : Serializable
```

```kotlin
data class ClassificationResult(
    val label: String,
    val score: Float,
    val classIndex: Int,
) : Serializable
```

`ImageClassifier`는 ONNX `OrtSession`을 소유하고 명시적으로 close해야 한다. call마다 새 session을 만들지 않는다.

## model packaging 전략

명시적인 local model asset을 사용한다:

- Required: `modelPath`와 `labelsPath`.
- Optional later: example 또는 test용 classpath resource.
- 첫 release 제외: remote download, background cache population, library jar에 bundled ImageNet-scale weight 포함.

구현은 model config 옆에 preprocessing metadata를 보존해야 한다. ONNX file 하나만으로는 stable result를 만들 수 없다. API는 input shape, normalization, channel order, label mapping을 capture해야 한다.

test에는 tiny synthetic ONNX model 또는 test-only로 명확히 표시된 작은 test fixture를 선호한다. tiny ONNX fixture를 만들 때 development 중 Python이 필요하더라도, normal Gradle test execution에는 Python을 요구하지 말고 generated test resource와 provenance note만 commit한다.

## CI 및 verification 전략

구현 후 권장 command:

```bash
./gradlew :bluetape4k-images-classification:test
./gradlew :bluetape4k-images-classification:build
```

test는 다음을 cover해야 한다:

- classifier lifecycle: session이 재사용되고 한 번만 close된다.
- invalid model/label path는 actionable exception을 낸다.
- preprocessing이 expected tensor shape를 만든다.
- top-k result ordering이 deterministic하다.
- suspend API가 적절한 dispatcher를 사용하고 `CancellationException`을 보존한다.
- native ONNX Runtime이 CI 또는 local architecture에서 실행되지 못하면 platform support를 문서화한다.

## Issue #3 handoff

구현 acceptance criteria:

- `bluetape4k-images-classification`을 optional published module로 추가한다.
- 첫 구현에서는 ONNX Runtime Java를 직접 사용하고 DJL/TensorFlow abstraction을 두지 않는다.
- 명시적인 model path와 label path를 요구한다.
- preprocessing metadata를 serializable model config에 capture한다.
- classification module package에서 sync 및 suspend `ImmutableImage.classify` extension function을 노출한다.
- local model/labels setup을 보여주는 English/Korean README example을 추가한다.
- test/build를 검증하고 native-platform limitation을 기록한다.

## 출처

- ONNX Runtime Java documentation: https://onnxruntime.ai/docs/get-started/with-java.html
- ONNX Runtime Maven metadata checked on 2026-05-29: latest `1.22.0`
- DJL documentation and model zoo: https://docs.djl.ai/ and https://djl.ai/docs/model-zoo.html
- TensorFlow Java documentation: https://www.tensorflow.org/jvm
