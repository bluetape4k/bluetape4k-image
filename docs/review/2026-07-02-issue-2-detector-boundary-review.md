# Issue #2 Detector Boundary 검토

## 범위

- 이슈: #2 `feat(images): define detector boundary for face and object results`
- 검토 파일:
  - `images/src/main/kotlin/io/bluetape4k/images/detection/ImageDetection.kt`
  - `images/src/test/kotlin/io/bluetape4k/images/detection/ImageDetectionTest.kt`
  - `images/src/test/kotlin/io/bluetape4k/images/detection/ImageDetectionSampleCorpusTest.kt`
  - `images/src/test/resources/detection/samples/metadata.json`
  - `docs/images/detection-samples/sample-detection-results.png`
  - `docs/scripts/generate-detection-sample-overlays.py`
  - `README.md`
  - `README.ko.md`

## 발견 사항

- P0: 없음.
- P1: 없음.

## 검토 메모

- detector boundary는 OpenCV, ONNX Runtime, TensorFlow Lite, MediaPipe, GPU, model-download, large-fixture dependency를 추가하지 않는다.
- Region geometry reuses the existing sensitive-content model through detector-facing aliases, avoiding a second rectangle/polygon/mask contract.
- Public value models use `@ConsistentCopyVisibility` with private constructors and companion factories so validation is not bypassed through `copy()`.
- 테스트는 deterministic fake detector와 bluetape4k assertion을 사용한다. MockK나 operation mocking은 도입하지 않는다.
- internet-derived sample corpus는 작고 checksum 검증을 거쳤으며 public-domain source를 사용한다. 범위는 CI-deterministic core signal과 manifest-backed detector annotation으로 제한된다.
- README preview assets are generated from the same manifest-backed annotations, so the shown rectangles do not imply a production ML runtime.

## 검증

- `:bluetape4k-images:compileTestKotlin`
- `:bluetape4k-images:test --tests io.bluetape4k.images.detection.ImageDetectionTest`
- `:bluetape4k-images:test --tests io.bluetape4k.images.detection.ImageDetectionSampleCorpusTest`
- `:bluetape4k-images:test` (610 PASSing, 18 pending)
- `docs/scripts/generate-detection-sample-overlays.py`
- `CodeGraph detect_changes` (risk 0.00, affected flows 0, test gaps 0)
- `git diff --check`
