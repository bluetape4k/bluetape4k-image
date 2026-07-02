# Issue #2 Detector Boundary Review

## Scope

- Issue: #2 `feat(images): define detector boundary for face and object results`
- Files reviewed:
  - `images/src/main/kotlin/io/bluetape4k/images/detection/ImageDetection.kt`
  - `images/src/test/kotlin/io/bluetape4k/images/detection/ImageDetectionTest.kt`
  - `images/src/test/kotlin/io/bluetape4k/images/detection/ImageDetectionSampleCorpusTest.kt`
  - `images/src/test/resources/detection/samples/metadata.json`
  - `docs/images/detection-samples/sample-detection-results.png`
  - `docs/scripts/generate-detection-sample-overlays.py`
  - `README.md`
  - `README.ko.md`

## Findings

- P0: none.
- P1: none.

## Review Notes

- The detector boundary adds no OpenCV, ONNX Runtime, TensorFlow Lite, MediaPipe, GPU, model-download, or large-fixture dependency.
- Region geometry reuses the existing sensitive-content model through detector-facing aliases, avoiding a second rectangle/polygon/mask contract.
- Public value models use `@ConsistentCopyVisibility` with private constructors and companion factories so validation is not bypassed through `copy()`.
- Tests use deterministic fake detectors and bluetape4k assertions. No MockK or operation mocking is introduced.
- The internet-derived sample corpus is small, checksum-verified, public-domain sourced, and limited to CI-deterministic core signals plus manifest-backed detector annotations.
- README preview assets are generated from the same manifest-backed annotations, so the shown rectangles do not imply a production ML runtime.

## Validation

- `:bluetape4k-images:compileTestKotlin`
- `:bluetape4k-images:test --tests io.bluetape4k.images.detection.ImageDetectionTest`
- `:bluetape4k-images:test --tests io.bluetape4k.images.detection.ImageDetectionSampleCorpusTest`
- `:bluetape4k-images:test` (610 passing, 18 pending)
- `docs/scripts/generate-detection-sample-overlays.py`
- `CodeGraph detect_changes` (risk 0.00, affected flows 0, test gaps 0)
- `git diff --check`
