# Issue #84 face 및 object detection dependency와 model packaging 연구

- 이슈: [#84](https://github.com/bluetape4k/bluetape4k-image/issues/84)
- 구현 대상: [#2](https://github.com/bluetape4k/bluetape4k-image/issues/2)
- 날짜: 2026-05-29
- 범위: face/object detection runtime, model packaging, module boundary, dependency risk, CI handoff.

## 요약

native dependency footprint를 수용한 뒤에만 진행한다. 권장 구현 형태는 OpenCV Java/DNN API를 사용하는 별도 `bluetape4k-images-detection-opencv` module이다. model file은 user 또는 example/test가 명시적으로 제공한다. core `bluetape4k-images`에는 detection dependency를 추가하지 않고, published artifact에는 YOLO 또는 다른 큰 model weight를 bundle하지 않는다.

Detection은 AI image issue 중 runtime 및 packaging risk가 가장 크다. native library, model file, post-processing, label, bounding-box contract가 모두 결합되므로 OCR과 classification research 뒤에 처리한다.

## 현재 repository 적합성

- `bluetape4k-image`에는 이미 libvips용 optional native module이 있다. Detection도 같은 opt-in module pattern을 따른다.
- 기존 `ImmutableImage` API는 source image를 제공할 수 있지만, detection에는 별도 result model이 필요하다. result에는 bounding box, label, confidence, coordinate system, optional class ID가 포함된다.
- issue는 face detection과 general object detection이라는 서로 다른 workload를 함께 다룬다. public API는 공통으로 유지하되 detector implementation은 분리할 수 있게 한다.

## 후보 평가

| 후보 | 결정 | 근거 |
|---|---|---|
| OpenCV Java DNN / Cascade APIs | 주의 조건부 권장 | issue 방향과 맞고 cascade face detection 및 DNN object detection을 지원하지만, native packaging과 model license risk가 있다. |
| Bytedeco OpenCV platform artifacts | packaging 후보로 권장 | OpenCV native binary를 Maven으로 배포하는 현실적인 방법이다. 구현 전에 dependency size와 transitive native/license를 검토해야 한다. |
| DJL object detection model zoo | 보류 | developer ergonomics는 좋지만, 이 repo가 안정적인 model-packaging policy를 갖기 전에 더 넓은 ML framework를 도입한다. |
| ONNX Runtime direct | detection에서는 보류 | classifier에는 좋지만 detection에는 model-specific preprocessing/post-processing과 output decoding이 더 많이 필요하다. |
| TensorFlow Lite / LiteRT | 첫 pass에서는 제외 | edge/mobile 지향이며 현재 JVM server library 형태와 덜 맞다. |

## 권장 module boundary

전용 module만 추가한다:

```text
images-detection-opencv/
  artifact: io.github.bluetape4k.image:bluetape4k-images-detection-opencv
  package: io.bluetape4k.images.detection
```

의존성:

- `api(project(":bluetape4k-images"))`
- license 및 artifact-size review 후 OpenCV Java binding을 추가한다. 가장 가능성이 큰 후보는 `org.bytedeco:opencv-platform`이다.
- `implementation(libs.kotlinx.coroutines.core)`
- test에는 JUnit 5와 bluetape4k assertion을 사용한다.

Public API 형태:

```kotlin
interface ImageDetector : AutoCloseable {
    fun detect(image: ImmutableImage, options: DetectionOptions = DetectionOptions()): List<DetectionResult>
    suspend fun detectSuspend(image: ImmutableImage, options: DetectionOptions = DetectionOptions()): List<DetectionResult>
}
```

```kotlin
data class BoundingBox(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) : Serializable
```

```kotlin
data class DetectionResult(
    val label: String,
    val confidence: Float,
    val box: BoundingBox,
    val classIndex: Int? = null,
) : Serializable
```

원본 image coordinate space의 top-left pixel coordinate를 사용한다. box는 image bounds로 clamp된다고 문서화한다.

## model packaging 전략

첫 release에서는 model asset을 external로 유지한다:

- Face detection은 caller가 제공하는 Haar/LBP cascade XML path를 지원할 수 있다.
- Object detection은 caller가 제공하는 DNN model path와 optional config path, labels path, input size, scale, mean, channel order, confidence threshold, NMS threshold를 지원할 수 있다.
- 별도 license review 없이 YOLO, SSD, COCO label file을 published artifact에 bundle하지 않는다.
- test fixture에는 tiny asset 또는 license-cleared asset만 포함한다. 그렇지 않으면 unit test에는 generated/fake detector를 사용하고 real OpenCV test는 gate한다.

이 방식은 library release를 third-party model update에 묶지 않고 Maven artifact를 검토 가능한 상태로 유지한다.

## CI 및 verification 전략

세 lane을 사용한다:

1. result model, option validation, post-processing용 pure JVM unit test.
2. `-Ddetection.enabled=true`로 gate한 native OpenCV smoke test.
3. license-cleared fixture가 있는 optional model-backed test.

구현 후 권장 command:

```bash
./gradlew :bluetape4k-images-detection-opencv:test
./gradlew :bluetape4k-images-detection-opencv:test -Ddetection.enabled=true
```

test는 다음을 cover해야 한다:

- invalid model/config/labels path가 명확하게 실패한다.
- bounding box가 image dimension으로 clamp된다.
- confidence 및 NMS threshold를 validate한다.
- detector lifecycle이 native resource를 close한다.
- suspend API가 cancellation을 보존한다.
- OpenCV를 사용할 수 없을 때 native lane이 명확한 reason과 함께 skip된다.

## dependency risk notes

- OpenCV 4.5.0+는 Apache-2.0 license지만 packaged native distribution과 model file은 추가 license obligation을 가질 수 있다. 구현 전에 exact Maven dependency와 model asset을 검토한다.
- `opencv-platform` 방식 artifact는 편리하지만 크다. artifact size가 허용 불가 수준이면 platform별 native classifier를 나누거나 system-installed OpenCV requirement를 문서화한다.
- OpenCV DNN output decoding은 model-specific이다. 지원 model family를 기록하지 않은 가짜 model-agnostic object detector를 노출하지 않는다.

## Issue #2 handoff

구현 acceptance criteria:

- 전용 optional detection module을 추가하고 core dependency는 건드리지 않는다.
- 명시적인 model/cascade path와 labels/config metadata를 지원한다.
- sync 및 suspend detection API를 제공한다.
- 원본 image coordinate의 serializable bounding-box result model을 반환한다.
- model asset responsibility와 unsupported bundled-model behavior를 문서화한다.
- gated native test를 추가하고 순차 실행을 유지한다.
- `opencv-platform` 또는 model fixture를 추가하기 전에 license/artifact-size review를 완료한다.

## 출처

- OpenCV license: https://opencv.org/license/
- OpenCV Java `CascadeClassifier`: https://docs.opencv.org/4.x/javadoc/org/opencv/objdetect/CascadeClassifier.html
- OpenCV Java `DetectionModel`: https://docs.opencv.org/master/javadoc/org/opencv/dnn/DetectionModel.html
- OpenCV Java DNN package: https://docs.opencv.org/4.x/javadoc/org/opencv/dnn/package-summary.html
- Bytedeco OpenCV packaging candidate: https://bytedeco.org/ and https://github.com/bytedeco/javacpp-presets
- Bytedeco OpenCV Maven metadata checked on 2026-05-29: latest `4.10.0-1.5.11`
