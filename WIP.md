# WIP - bluetape4k-image

Snapshot: 2026-05-24 KST
Scope: open GitHub issues assigned to `debop`.
Open count: 4 issues.

## 2026-05-24 Milestone Refresh

Current evidence: latest tags `0.1.2`, `0.1.1`, `0.1.0`. GitHub has four open
backlog feature issues and an empty `0.2.0` milestone.

| Lane | Candidate milestone | Current candidates | Decision |
|---|---|---|---|
| Patch | `0.1.3` | none yet | Keep empty unless image-core, docs, dependency, or CI regressions appear. |
| Minor | `0.2.0` | #4 first, then #1/#2/#3 after research | Use #4 CAPTCHA as the first self-contained feature. Keep native/model-heavy OCR/detection/classification isolated until dependency and model packaging are decided. |

Recommended order: #4 `images-captcha`; then research/design gates before #1
OCR, #2 face/object detection, or #3 classification implementation.

## New Milestone Queue - 2026-05-24

### New patch milestone `0.1.3`

1. [#82](https://github.com/bluetape4k/bluetape4k-image/issues/82)
   `docs: refresh libvips prerequisite and native-access troubleshooting`

### New minor milestone `0.2.0`

1. [#4](https://github.com/bluetape4k/bluetape4k-image/issues/4)
   `feat: CAPTCHA 이미지 생성 모듈 추가 (images-captcha)`
2. [#83](https://github.com/bluetape4k/bluetape4k-image/issues/83)
   `research: OCR dependency and model packaging strategy`
3. [#84](https://github.com/bluetape4k/bluetape4k-image/issues/84)
   `research: face and object detection dependency and model packaging strategy`
4. [#85](https://github.com/bluetape4k/bluetape4k-image/issues/85)
   `research: image classification dependency and model packaging strategy`
5. [#86](https://github.com/bluetape4k/bluetape4k-image/issues/86)
   `perf: refresh scrimage vs libvips benchmark report and README charts`

### Backlog reference

- [#1](https://github.com/bluetape4k/bluetape4k-image/issues/1),
  [#2](https://github.com/bluetape4k/bluetape4k-image/issues/2), and
  [#3](https://github.com/bluetape4k/bluetape4k-image/issues/3) remain backlog
  implementation ideas until the 0.2.0 research issues decide dependency and
  model packaging.

## Issue Discovery - 2026-05-24

Patch candidates:

- `docs: refresh libvips prerequisite and native-access troubleshooting`
  - Candidate only if README user-facing setup has drifted from current
    JNI/FFM/libvips operational rules.

Minor candidates:

- `feat: CAPTCHA image generation module` (#4)
- `research: OCR dependency and model packaging strategy` (#1)
- `research: face/object detection dependency and model packaging strategy` (#2)
- `research: image classification dependency and model packaging strategy` (#3)
- `perf: refresh scrimage vs libvips benchmark report and README charts`

## Refresh Notes

Verified with `gh` on 2026-05-22 KST.

- qmd was queried first for prior image/vips plans, specs, and follow-ups.
- All current open issues remain assigned to `debop`.
- Milestone `0.1.1` has zero open issues and is ready for release.

## Recently Completed

- **images-spring-boot** module (Spring Boot 4 auto-configuration — S3/CDN/health/metrics) merged via [PR #42](https://github.com/bluetape4k/bluetape4k-image/pull/42), closes [#5](https://github.com/bluetape4k/bluetape4k-image/issues/5).
- Pre-stabilization typo compatibility APIs were removed via [#61](https://github.com/bluetape4k/bluetape4k-image/issues/61).
- Extended test coverage for `images`, `images-vips-java21`, `images-vips-java25` via [PR #39](https://github.com/bluetape4k/bluetape4k-image/pull/39), [PR #40](https://github.com/bluetape4k/bluetape4k-image/pull/40), [PR #41](https://github.com/bluetape4k/bluetape4k-image/pull/41).
- POM license metadata corrected (Apache 2.0 → MIT) via [PR #38](https://github.com/bluetape4k/bluetape4k-image/pull/38).
- BOM module and localized BOM README files merged via [PR #12](https://github.com/bluetape4k/bluetape4k-image/pull/12) and [PR #13](https://github.com/bluetape4k/bluetape4k-image/pull/13).
- CI/Nightly, dependency governance, Kover policy, NMCP version, and dependency catalog maintenance merged through [PR #15](https://github.com/bluetape4k/bluetape4k-image/pull/15)–[PR #26](https://github.com/bluetape4k/bluetape4k-image/pull/26).
- Test code migrated from Kluent to `bluetape4k-assertions`.

## Current Direction

All active feature issues (#1–#4) are in the **Backlog** milestone with no fixed release target.
The 0.1.1 release gate is clear; next implementation work should start with the backlog priority queue.

Native/model-heavy work (#1 OCR, #2 face/object detection, #3 classification) remains isolated
from the core `images` module. No CI or packaging changes should touch core modules for these.

## Priority Queue (Backlog)

| Priority | Issue | Difficulty | Notes |
|---|---|---:|---|
| P1 | [#4](https://github.com/bluetape4k/bluetape4k-image/issues/4) images-captcha | M | Most self-contained; can migrate legacy CAPTCHA code without native model risk. |
| P2 | [#1](https://github.com/bluetape4k/bluetape4k-image/issues/1) OCR | L | Tesseract/PaddleOCR packaging and CI strategy must be isolated. |
| P2 | [#2](https://github.com/bluetape4k/bluetape4k-image/issues/2) face/object detection | L | OpenCV/ONNX/model bundling risk; keep as a separate optional module. |
| P4 | [#3](https://github.com/bluetape4k/bluetape4k-image/issues/3) image classification | L | Defer until model packaging is proven by #1/#2. |

## Dependency Map

```text
#4 CAPTCHA
  -> independent quick feature

#1 OCR
#2 face/object detection
  -> #3 classification, if model/runtime packaging is settled
```

## WIP Limits

| Lane | Limit | Current next |
|---|---:|---|
| Self-contained feature | 1 | `#4` (when ready) |
| Native/model feature | 1 | `#1` or `#2`, not both |
| Cross-repo integration | 0 | resolved — `#5` shipped |
