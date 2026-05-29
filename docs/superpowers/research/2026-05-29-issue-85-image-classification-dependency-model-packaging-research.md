# Issue #85 Image Classification Dependency and Model Packaging Research

- Issue: [#85](https://github.com/bluetape4k/bluetape4k-image/issues/85)
- Implementation target: [#3](https://github.com/bluetape4k/bluetape4k-image/issues/3)
- Date: 2026-05-29
- Scope: image classification runtime, ONNX/model packaging, module boundary,
  CI and API handoff.

## Summary

Proceed with a separate `bluetape4k-images-classification` module using ONNX
Runtime Java directly. Keep the first implementation CPU-only and model-file
driven. Do not bundle ImageNet-scale model weights in the published artifact,
and do not add automatic remote model downloads.

ONNX Runtime is a better first fit than a higher-level ML framework because the
issue explicitly asks for ONNX Runtime, the public API can stay small, and
model/preprocessing contracts can be versioned by this library instead of hidden
behind a model zoo abstraction.

## Current Repository Fit

- The repository already separates optional runtimes into dedicated modules.
  Classification should follow that pattern rather than adding inference
  dependencies to `bluetape4k-images`.
- The existing image pipeline can provide resized/cropped/encoded pixels, but
  classification needs explicit preprocessing metadata: input size, color
  order, normalization, and label mapping.
- Java 21 is the baseline for core modules. ONNX Runtime Java supports JVM use,
  but platform-native support must be verified in CI before claiming full
  cross-platform coverage.

## Candidate Evaluation

| Candidate | Decision | Rationale |
|---|---|---|
| ONNX Runtime Java direct API | Recommended | Smallest dependency surface for #3, aligns with issue requirements, supports explicit session/options lifecycle. |
| DJL with ONNX Runtime engine | Deferred | Useful if multiple engines/model zoo support become a goal, but too broad for the first classification module. |
| TensorFlow Java | Rejected for #3 | Heavier ecosystem choice and not aligned with the ONNX Runtime requirement. |
| TensorFlow Lite / LiteRT Java | Rejected for server-first module | Better suited to Android/edge deployment; JVM server packaging is less direct for this repo. |
| OpenCV DNN | Rejected for classification first pass | Better fit for detection issue #84/#2; ONNX Runtime gives a clearer classifier lifecycle. |

## Recommended Module Boundary

Add:

```text
images-classification/
  artifact: io.github.bluetape4k.image:bluetape4k-images-classification
  package: io.bluetape4k.images.classification
```

Dependencies:

- `api(project(":bluetape4k-images"))`
- `implementation(com.microsoft.onnxruntime:onnxruntime)`
- `implementation(libs.kotlinx.coroutines.core)`
- test dependencies for JUnit 5 and bluetape4k assertions

Recommended public API:

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

`ImageClassifier` should own an ONNX `OrtSession` and close it explicitly. Do
not create a new session per call.

## Model Packaging Strategy

Use explicit local model assets:

- Required: `modelPath` and `labelsPath`.
- Optional later: classpath resources for examples or tests.
- Not in first release: remote downloads, background cache population, or
  bundled ImageNet-scale weights in the library jar.

The implementation must persist preprocessing metadata beside the model config.
An ONNX file alone is not enough to produce stable results; the API must capture
input shape, normalization, channel order, and label mapping.

For tests, prefer a tiny synthetic ONNX model or a small test fixture that is
clearly marked test-only. If creating a tiny ONNX fixture requires Python during
development, commit only the generated test resource plus its provenance note;
do not require Python for normal Gradle test execution.

## CI and Verification Strategy

Recommended commands after implementation:

```bash
./gradlew :bluetape4k-images-classification:test
./gradlew :bluetape4k-images-classification:build
```

Tests should cover:

- classifier lifecycle: session reused and closed once.
- invalid model/label paths produce actionable exceptions.
- preprocessing produces the expected tensor shape.
- top-k result ordering is deterministic.
- suspend API uses an appropriate dispatcher and preserves
  `CancellationException`.
- platform support is documented if native ONNX Runtime cannot run on a CI or
  local architecture.

## Handoff for Issue #3

Acceptance criteria for implementation:

- Add `bluetape4k-images-classification` as an optional published module.
- Use ONNX Runtime Java directly; no DJL/TensorFlow abstraction in the first
  implementation.
- Require explicit model and label paths.
- Capture preprocessing metadata in a serializable model config.
- Expose sync and suspend `ImmutableImage.classify` extension functions from the
  classification module package.
- Add README examples in English and Korean showing local model/labels setup.
- Verify test/build and record any native-platform limitations.

## Sources

- ONNX Runtime Java documentation: https://onnxruntime.ai/docs/get-started/with-java.html
- ONNX Runtime Maven metadata checked on 2026-05-29: latest `1.22.0`
- DJL documentation and model zoo: https://docs.djl.ai/ and https://djl.ai/docs/model-zoo.html
- TensorFlow Java documentation: https://www.tensorflow.org/jvm
