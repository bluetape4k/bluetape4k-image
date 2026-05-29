# Issue #84 Face and Object Detection Dependency and Model Packaging Research

- Issue: [#84](https://github.com/bluetape4k/bluetape4k-image/issues/84)
- Implementation target: [#2](https://github.com/bluetape4k/bluetape4k-image/issues/2)
- Date: 2026-05-29
- Scope: face/object detection runtime, model packaging, module boundary,
  dependency risk, and CI handoff.

## Summary

Proceed only after accepting the native dependency footprint. The recommended
implementation shape is a separate `bluetape4k-images-detection-opencv` module
using OpenCV Java/DNN APIs, with model files supplied explicitly by the user or
by examples/tests. Do not add detection dependencies to core
`bluetape4k-images`, and do not bundle YOLO or other large model weights in the
published artifact.

Detection has the largest runtime and packaging risk among the AI image issues.
Handle it after OCR and classification research because it combines native
libraries, model files, post-processing, labels, and bounding-box contracts.

## Current Repository Fit

- `bluetape4k-image` already has optional native modules for libvips. Detection
  should use the same opt-in module pattern.
- Existing `ImmutableImage` APIs can provide the source image, but detection
  requires a separate result model: bounding boxes, labels, confidence,
  coordinate system, and optional class IDs.
- The issue combines two different workloads: face detection and general object
  detection. Keep the public API common, but allow separate detector
  implementations.

## Candidate Evaluation

| Candidate | Decision | Rationale |
|---|---|---|
| OpenCV Java DNN / Cascade APIs | Recommended with caution | Matches issue direction, supports cascade face detection and DNN object detection, but has native packaging and model-license risk. |
| Bytedeco OpenCV platform artifacts | Recommended packaging candidate | Practical Maven distribution for OpenCV native binaries; dependency size and transitive native/licenses must be reviewed before implementation. |
| DJL object detection model zoo | Deferred | Good developer ergonomics, but introduces a broader ML framework before this repo has a stable model-packaging policy. |
| ONNX Runtime direct | Deferred for detection | Good for classifier first. Detection would need more custom preprocessing/post-processing and model-specific output decoding. |
| TensorFlow Lite / LiteRT | Rejected for first pass | Edge/mobile-oriented and less aligned with the current JVM server library shape. |

## Recommended Module Boundary

Add a dedicated module only:

```text
images-detection-opencv/
  artifact: io.github.bluetape4k.image:bluetape4k-images-detection-opencv
  package: io.bluetape4k.images.detection
```

Dependencies:

- `api(project(":bluetape4k-images"))`
- OpenCV Java binding, most likely via `org.bytedeco:opencv-platform` after
  license and artifact-size review.
- `implementation(libs.kotlinx.coroutines.core)`
- JUnit 5 and bluetape4k assertions for tests.

Public API shape:

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

Use top-left pixel coordinates in the original image coordinate space. Document
that boxes are clamped to image bounds.

## Model Packaging Strategy

Keep model assets external in the first release:

- Face detection can support a caller-supplied Haar/LBP cascade XML path.
- Object detection can support a caller-supplied DNN model path plus optional
  config path, labels path, input size, scale, mean, channel order, confidence
  threshold, and NMS threshold.
- Do not bundle YOLO, SSD, or COCO label files in the published artifact without
  a separate license review.
- Test fixtures may include tiny or license-cleared assets only; otherwise use
  generated/fake detectors for unit tests and gate real OpenCV tests.

This avoids coupling library releases to third-party model updates and keeps the
Maven artifact reviewable.

## CI and Verification Strategy

Use three lanes:

1. Pure JVM unit tests for result models, option validation, and post-processing.
2. Native OpenCV smoke tests gated by `-Ddetection.enabled=true`.
3. Optional model-backed tests with license-cleared fixtures.

Recommended commands after implementation:

```bash
./gradlew :bluetape4k-images-detection-opencv:test
./gradlew :bluetape4k-images-detection-opencv:test -Ddetection.enabled=true
```

Tests should cover:

- invalid model/config/labels paths fail clearly.
- bounding boxes clamp to image dimensions.
- confidence and NMS thresholds validate.
- detector lifecycle closes native resources.
- suspend API preserves cancellation.
- native lane is skipped with a clear reason when OpenCV is unavailable.

## Dependency Risk Notes

- OpenCV 4.5.0+ is Apache-2.0 licensed, but packaged native distributions and
  model files can carry additional license obligations. Review the exact Maven
  dependency and model assets before implementation.
- `opencv-platform` style artifacts are convenient but large. If artifact size
  becomes unacceptable, split native classifiers by platform or document a
  system-installed OpenCV requirement instead.
- OpenCV DNN output decoding is model-specific. Do not expose a fake
  model-agnostic object detector without recording the supported model families.

## Handoff for Issue #2

Acceptance criteria for implementation:

- Add a dedicated optional detection module; do not touch core dependencies.
- Support explicit model/cascade paths and labels/config metadata.
- Provide sync and suspend detection APIs.
- Return serializable bounding-box result models in original image coordinates.
- Document model asset responsibilities and unsupported bundled-model behavior.
- Add gated native tests and keep them sequential.
- Complete a license/artifact-size review before adding `opencv-platform` or any
  model fixtures.

## Sources

- OpenCV license: https://opencv.org/license/
- OpenCV Java `CascadeClassifier`: https://docs.opencv.org/4.x/javadoc/org/opencv/objdetect/CascadeClassifier.html
- OpenCV Java `DetectionModel`: https://docs.opencv.org/master/javadoc/org/opencv/dnn/DetectionModel.html
- OpenCV Java DNN package: https://docs.opencv.org/4.x/javadoc/org/opencv/dnn/package-summary.html
- Bytedeco OpenCV packaging candidate: https://bytedeco.org/ and https://github.com/bytedeco/javacpp-presets
- Bytedeco OpenCV Maven metadata checked on 2026-05-29: latest `4.10.0-1.5.11`
