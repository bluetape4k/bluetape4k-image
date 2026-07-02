# Sensitive Content Moderation Workflow Blog Seed

This is a blog-ready outline for a future bluetape4k image moderation article.
It is intentionally an outline, not a published post. Keep production model
selection, runtime packaging, GPU/native dependencies, and detector benchmark
claims out of the article unless a separate detector issue selects and verifies
that runtime.

## Working Title

Sensitive image moderation without coupling detection, policy, and rendering

## Audience

Kotlin backend engineers who need image moderation workflows but do not want the
core image library to own model downloads, detector runtime choices, storage
side effects, or application-specific review queues.

## Source Anchors

- `io.bluetape4k.images.moderation.SensitiveContentDetection`
- `io.bluetape4k.images.moderation.SensitiveModerationPolicy`
- `io.bluetape4k.images.privacy.PrivacyDerivativeOptions`
- `io.bluetape4k.images.examples.basic.SensitiveContentWorkflowQuickstart`

Use source links to the `develop` branch when turning this outline into a blog
post.

## Article Shape

1. Problem
   - A detector result is not a moderation decision.
   - A moderation decision is not a pixel renderer.
   - A renderer is not a storage, quarantine, or rejection workflow.

2. Boundary model
   - Detection facts: category, severity, confidence, raw backend label, region.
   - Policy decisions: selected action, level, parameters, matched rule, reason.
   - Rendering: public-safe derivative, metadata/GPS stripping, redaction report.
   - Side effects: reject, drop, quarantine, and manual review remain caller-owned.

3. Architecture
   - Detector adapters produce backend-neutral facts.
   - Policy rules convert facts into auditable treatment decisions.
   - Renderers consume only supported geometry/action pairs.
   - Application services own durable side effects such as dropping, rejecting,
     quarantine storage, review queues, and audit persistence.

4. Workflow states
   - `DETECTED`
   - `CLASSIFIED`
   - `POLICY_EVALUATED`
   - `ACTION_SELECTED`
   - Optional terminal or side-effect states: `RENDERED`, `REJECTED`,
     `QUARANTINED`, `FAILED`

5. Severity and confidence
   - `LOW`: usually audit-only or allow.
   - `MEDIUM`: treatment is likely, but thresholds still need review.
   - `HIGH`: automatic treatment or manual review is common.
   - `CRITICAL`: drop, reject, or quarantine paths become likely.
   - Severity is a policy-facing risk bucket.
   - Confidence is detector output and should not be treated as truth.
   - Thresholds need review because false positives, false negatives, model
     drift, and distribution shifts are normal operating risks.

6. Region geometry
   - Rectangle: directly renderable by the current core privacy derivative
     pipeline.
   - Polygon: useful for contours and area selections, but needs a renderer
     adapter before pixel treatment.
   - Polyline: useful for text lines, strokes, or paths; not a filled area by
     itself.
   - Raster mask metadata: carries an external mask reference without forcing
     mask bytes or storage dependencies into the core model.

7. Treatment actions
   - `ALLOW`: audit-only path.
   - `MOSAIC`: action intent plus `mosaicBlockSize`; renderer-specific.
   - `BLUR`: action intent plus `blurRadius` and optional sigma; renderer-specific.
   - `SOLID_MASK`: directly maps well to privacy derivative rectangle redaction.
   - `DROP`: automated derivative/feed suppression.
   - `REJECT`: request or asset rejection.
   - `QUARANTINE`: fail-closed hold for unknown or unmatched detections.
   - `MANUAL_REVIEW`: human decision queue with priority.

8. Example walkthrough
   - Run `./gradlew :basic-processing:runSensitiveWorkflow`.
   - Explain the deterministic fixture detector.
   - Show the action matrix from `06-sensitive-moderation-report.txt`.
   - Show why only rectangle actions are rendered by the core derivative today.

9. Real detector adapter path
   - Keep the adapter behind the existing detector or sensitive detection
     contract.
   - Preserve raw backend labels and model metadata.
   - Map backend labels into stable categories and severity outside the renderer.
   - Keep model weights, runtime selection, GPU/native libraries, and licensing
     review in adapter or application modules.

10. Operational caveats
   - No model is perfect.
   - Log policy version, detector identity, threshold set, and matched rule.
   - Track false positives and false negatives separately.
   - Make quarantine and manual review queues observable.
   - Never treat generated derivatives as evidence that the source image is safe.

## Suggested Code Snippets

- Deterministic fake detector output returning `SensitiveContentDetection`.
- `SensitiveModerationPolicy.failClosed(...)` with rule examples.
- Mapping rectangle policy decisions into `PrivacyRedaction`.
- Running the example task and inspecting generated files.

## Non-Goals

- Recommending a production moderation model.
- Bundling ONNX/OpenCV/TensorFlow/PyTorch/runtime assets.
- Claiming that deterministic fixtures prove semantic moderation accuracy.
- Implementing storage, review queues, or moderation dashboards in the core
  image module.
