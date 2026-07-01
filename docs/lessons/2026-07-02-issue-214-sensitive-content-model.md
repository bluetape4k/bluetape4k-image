# Lessons Learned - Issue #214 Sensitive Content Detection Model

Date: 2026-07-02
Related issue: #214

## Context

The 0.4.0 line needed the first sensitive-content boundary without selecting a detector runtime or adding model dependencies to `bluetape4k-images`.

## Decision

Add only backend-neutral result models to the core image module:

- stable category and severity enums
- raw backend label preservation
- rectangle, polygon, polyline, and raster-mask region geometries
- coordinate and confidence validation

Detector runtimes, policy actions, and redaction rendering remain separate issues/modules.

## Outcome

The public model now lets future detector adapters publish consistent results without forcing OpenCV, ONNX, model weights, or policy engines into the core module.

## Verification

- Red compile failure before model implementation.
- Targeted model tests pass.
- Full `:bluetape4k-images:test` passes.
- README/README.ko document the non-goals and caller policy risks.

## Future Guard

Future sensitive-content work should consume this model first. Do not add runtime detector dependencies, bundled model assets, or treatment actions to `bluetape4k-images` without a separate issue and module-boundary review.
