# WIP - bluetape4k-image

Snapshot: 2026-05-13 KST
Scope: open GitHub issues assigned to `debop`, created on or after 2026-01-01.
Open count: 5 issues.

## Recently Completed

- BOM module and localized BOM README files are merged by PR #12 and PR #13.
- CI/Nightly, dependency governance, Kover policy, NMCP version, and dependency
  catalog maintenance are merged through PR #15 through PR #26.
- Test code has been migrated from Kluent to `bluetape4k-assertions`.

## Current Direction

The active backlog remains native/model-heavy. Keep optional model or native
runtime work isolated so CI, packaging, and libvips behavior do not leak into
the core `images` module.

`images-vips-*` and scrimage improvements not represented by current GitHub
issues remain deferred unless they become release blockers.

## Priority Queue

| Priority | Issue | Difficulty | Notes |
|---|---|---:|---|
| P2 | [#4](https://github.com/bluetape4k/bluetape4k-image/issues/4) images-captcha | M | Most self-contained; can migrate legacy CAPTCHA code without native model risk. |
| P2 | [#1](https://github.com/bluetape4k/bluetape4k-image/issues/1) OCR | L | Tesseract/PaddleOCR packaging and CI strategy must be isolated. |
| P2 | [#2](https://github.com/bluetape4k/bluetape4k-image/issues/2) face/object detection | L | OpenCV/ONNX/model bundling risk; keep as a separate optional module. |
| P2 | [#5](https://github.com/bluetape4k/bluetape4k-image/issues/5) S3/CDN/Spring Boot integration | L | Wait for stable `bluetape4k-aws` S3/Spring conventions before implementation. |
| P4 | [#3](https://github.com/bluetape4k/bluetape4k-image/issues/3) image classification | L | Defer until model packaging is proven by `#1/#2`. |

## Dependency Map

```text
#4 CAPTCHA
  -> independent quick feature

#1 OCR
#2 face/object detection
  -> #3 classification, if model/runtime packaging is settled

bluetape4k-aws S3/Spring conventions
  -> #5 S3/CDN/Spring Boot image integration
```

## WIP Limits

| Lane | Limit | Current next |
|---|---:|---|
| Self-contained feature | 1 | `#4` |
| Native/model feature | 1 | `#1` or `#2`, not both. |
| Cross-repo integration | 0 until AWS S3/Spring settles | `#5` waits. |
