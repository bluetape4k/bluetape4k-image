# WIP - bluetape4k-image

Snapshot: 2026-05-09 KST
Scope: open GitHub issues assigned to `debop`, created on or after 2026-01-01.
Open count: 5 issues.

## Current Direction

The active backlog is mostly native/model-heavy work. Keep each feature isolated
so CI and packaging risk does not leak into the core `images` module.

`images-vips-*` and scrimage improvements not represented by current GitHub
issues remain deferred unless they become release blockers.

## Priority Queue

| Priority | Issue | Difficulty | Notes |
|---|---|---:|---|
| P2 | [#4](https://github.com/bluetape4k/bluetape4k-image/issues/4) images-captcha | M | Most self-contained; can migrate legacy CAPTCHA code without native model risk. |
| P2 | [#1](https://github.com/bluetape4k/bluetape4k-image/issues/1) OCR | L | Tesseract/PaddleOCR packaging and CI strategy must be isolated. |
| P2 | [#2](https://github.com/bluetape4k/bluetape4k-image/issues/2) face/object detection | L | OpenCV/ONNX/model bundling risk; keep as a separate optional module. |
| P2 | [#5](https://github.com/bluetape4k/bluetape4k-image/issues/5) S3/CDN/Spring Boot integration | L | Wait for `bluetape4k-aws #1` S3 conventions. |
| P4 | [#3](https://github.com/bluetape4k/bluetape4k-image/issues/3) image classification | L | Defer until model packaging is proven by `#1/#2`. |

## Dependency Map

```text
#4 CAPTCHA
  -> independent quick feature

#1 OCR
#2 face/object detection
  -> #3 classification, if model/runtime packaging is settled

bluetape4k-aws #1 Spring Boot S3
  -> #5 S3/CDN/Spring Boot image integration
```

## WIP Limits

| Lane | Limit | Current next |
|---|---:|---|
| Self-contained feature | 1 | `#4` |
| Native/model feature | 1 | `#1` or `#2`, not both. |
| Cross-repo integration | 0 until AWS S3 settles | `#5` waits. |
