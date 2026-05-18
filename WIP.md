# WIP - bluetape4k-image

Snapshot: 2026-05-17 KST
Scope: open GitHub issues assigned to `debop`.
Open count: 5 issues.

## Refresh Notes

Verified with `gh` on 2026-05-18 KST.

- qmd was queried first for prior image/vips plans, specs, and follow-ups.
- New issue registered from this audit:
  - [#61](https://github.com/bluetape4k/bluetape4k-image/issues/61) - `chore: remove typo compatibility APIs before 0.1.x stabilization`
- All current open issues remain assigned to `debop`.

## Recently Completed

- **images-spring-boot** module (Spring Boot 4 auto-configuration — S3/CDN/health/metrics) merged via [PR #42](https://github.com/bluetape4k/bluetape4k-image/pull/42), closes [#5](https://github.com/bluetape4k/bluetape4k-image/issues/5).
- Extended test coverage for `images`, `images-vips-java21`, `images-vips-java25` via [PR #39](https://github.com/bluetape4k/bluetape4k-image/pull/39), [PR #40](https://github.com/bluetape4k/bluetape4k-image/pull/40), [PR #41](https://github.com/bluetape4k/bluetape4k-image/pull/41).
- POM license metadata corrected (Apache 2.0 → MIT) via [PR #38](https://github.com/bluetape4k/bluetape4k-image/pull/38).
- BOM module and localized BOM README files merged via [PR #12](https://github.com/bluetape4k/bluetape4k-image/pull/12) and [PR #13](https://github.com/bluetape4k/bluetape4k-image/pull/13).
- CI/Nightly, dependency governance, Kover policy, NMCP version, and dependency catalog maintenance merged through [PR #15](https://github.com/bluetape4k/bluetape4k-image/pull/15)–[PR #26](https://github.com/bluetape4k/bluetape4k-image/pull/26).
- Test code migrated from Kluent to `bluetape4k-assertions`.

## Current Direction

All active feature issues (#1–#4) are in the **Backlog** milestone with no fixed release target.
Before treating the 0.1.x API as stable, resolve the small public API hygiene item (#61).
The next release action should happen only after deciding whether #61 is part of the release gate.

Native/model-heavy work (#1 OCR, #2 face/object detection, #3 classification) remains isolated
from the core `images` module. No CI or packaging changes should touch core modules for these.

## Priority Queue (Backlog)

| Priority | Issue | Difficulty | Notes |
|---|---|---:|---|
| P1 | [#61](https://github.com/bluetape4k/bluetape4k-image/issues/61) pre-stabilization typo/deprecated API cleanup | S | Remove or intentionally document typo compatibility aliases before the API surface stabilizes. |
| P2 | [#4](https://github.com/bluetape4k/bluetape4k-image/issues/4) images-captcha | M | Most self-contained; can migrate legacy CAPTCHA code without native model risk. |
| P2 | [#1](https://github.com/bluetape4k/bluetape4k-image/issues/1) OCR | L | Tesseract/PaddleOCR packaging and CI strategy must be isolated. |
| P2 | [#2](https://github.com/bluetape4k/bluetape4k-image/issues/2) face/object detection | L | OpenCV/ONNX/model bundling risk; keep as a separate optional module. |
| P4 | [#3](https://github.com/bluetape4k/bluetape4k-image/issues/3) image classification | L | Defer until model packaging is proven by #1/#2. |

## Dependency Map

```text
#61 API hygiene
  -> decide before stable 0.1.x API consumers depend on typo aliases

#4 CAPTCHA
  -> independent quick feature

#1 OCR
#2 face/object detection
  -> #3 classification, if model/runtime packaging is settled
```

## WIP Limits

| Lane | Limit | Current next |
|---|---:|---|
| API hygiene | 1 | `#61` before declaring 0.1.x API stable |
| Self-contained feature | 1 | `#4` (when ready) |
| Native/model feature | 1 | `#1` or `#2`, not both |
| Cross-repo integration | 0 | resolved — `#5` shipped |
