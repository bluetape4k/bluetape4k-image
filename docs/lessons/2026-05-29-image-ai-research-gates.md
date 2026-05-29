# 2026-05-29 - Image AI Research Gates

## Context

Issues #83, #84, and #85 needed dependency and model-packaging decisions before
starting OCR, detection, or classification implementation work.

## Decision

Keep AI/image-intelligence dependencies out of the core `bluetape4k-images`
module. Use optional modules for OCR, classification, and detection, and keep
large native/model assets external unless a separate license and artifact-size
review approves bundling.

## Outcome

Research handoff documents now define recommended runtimes, rejected
alternatives, module boundaries, model packaging, CI strategy, and acceptance
criteria for issues #1, #2, and #3.

## Verification

- `git diff --check`
- Source evidence from official Tesseract, ONNX Runtime, DJL, TensorFlow,
  OpenCV, Bytedeco, and Maven Central metadata.

## Future Note

When implementing image AI modules, start from the research document tied to the
issue and update the document if dependency versions, native-platform support,
or license constraints change.
