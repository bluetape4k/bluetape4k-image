# Issue 219 Sensitive Moderation Workflow Example

## Context

#219 needed a concrete example that connects sensitive detections, moderation
policy, action selection, and privacy-safe derivative generation without
selecting a production detector runtime.

## Decision

Add the workflow to the existing `basic-processing` example instead of creating
a new module. The example uses deterministic fake detector output, existing
`SensitiveContentDetection` and `SensitiveModerationPolicy` models, and the
privacy derivative pipeline for rectangle redactions only.

## Outcome

The example covers rectangle, polygon, polyline, and raster-mask metadata
regions plus allow, mosaic, blur, solid mask, manual review, drop, reject, and
quarantine actions. The README and blog seed state that production model
selection, renderer adapters, storage side effects, and review queues remain
outside the core image module.

## Verification

- `./gradlew :basic-processing:test --tests 'io.bluetape4k.images.examples.basic.SensitiveContentWorkflowQuickstartTest'`
- `./gradlew :basic-processing:runSensitiveWorkflow --args='build/tmp/sensitive-content-workflow-check'`
- `./gradlew :basic-processing:test`
- `./gradlew detekt`
- `git diff --check`

## Future Guard

Keep future moderation examples deterministic unless a separate detector issue
selects a runtime. Do not imply that fixture detections prove semantic model
accuracy, and do not move storage, quarantine, rejection, or manual-review side
effects into `bluetape4k-images`.
