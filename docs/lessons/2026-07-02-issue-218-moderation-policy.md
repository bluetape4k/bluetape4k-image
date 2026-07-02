# Issue 218 Moderation Policy

## Context

#214 added backend-neutral sensitive-content detection facts, and #2 added the detector boundary. #218 needed the next layer: policy decisions that can choose treatment actions without detector inference or pixel rendering.

## Decision

Keep moderation policy in `bluetape4k-images` as a small renderer-neutral model:

- `SensitiveModerationRule` matches category, minimum severity, and minimum confidence.
- `SensitiveTreatmentParameters` carries optional renderer hints such as blur radius, mosaic block size, mask opacity, review priority, and reject reason.
- `SensitiveModerationPolicy.failClosed(...)` quarantines unmatched detections by default.
- Empty detection lists return `ALLOW`; unmatched detections fail closed.

## Outcome

The policy layer can produce an auditable report while leaving detector runtime selection, rendering, persistence, and service-side workflow side effects to follow-up issues or applications.

## Verification

- `./gradlew :bluetape4k-images:test --tests 'io.bluetape4k.images.moderation.SensitiveContentPolicyTest'`
- `./gradlew :bluetape4k-images:test`
- `git diff --check`

## Future Guard

Do not add ML runtimes, model downloads, or pixel rendering to `bluetape4k-images` when extending moderation policy. Add adapter or renderer modules only when a follow-up issue explicitly chooses that boundary.
