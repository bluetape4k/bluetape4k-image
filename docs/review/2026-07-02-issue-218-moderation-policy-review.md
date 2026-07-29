# Issue 218 Moderation Policy 검토

## 범위

- Added `SensitiveContentPolicy.kt` as a renderer-neutral policy layer over `SensitiveContentDetection`.
- Added tests for level rules, action parameters, confidence boundaries, unknown categories, mixed-region decisions, and fail-closed fallback.
- Updated root and module README locale pairs with policy boundaries and operational risk notes.

## 7계층 검토

| 계층 | 판정 | 근거 |
|---|---|---|
| API 경계 | PASS | Policy consumes detection facts and returns decisions; it does not run detector inference or render pixels. |
| 정확성 | PASS | Rule matching uses category, inclusive severity, and inclusive confidence thresholds. Empty rule sets route detections through the fail-closed fallback. |
| 안전성 | PASS | Unknown or unmatched sensitive categories select `QUARANTINE` by default instead of `ALLOW`. |
| 테스트 범위 | PASS | `SensitiveContentPolicyTest` covers rule thresholds, parameter mapping, unknown categories, mixed actions, empty detections, and invalid parameters. |
| 문서 | PASS | `README.md`, `README.ko.md`, `images/README.md`, and `images/README.ko.md` describe safe defaults and false-positive/false-negative risks. |
| 생태계 재사용 | PASS | Reuses existing sensitive-content models and bluetape4k assertion helpers; no new runtime dependency or ML backend was introduced. |
| 운영 | PASS | Normal CI remains deterministic and does not need GPU, model downloads, pixel rendering, OCR, or native backends. |

## 검증

- `git diff --check`
- `./gradlew :bluetape4k-images:test --tests 'io.bluetape4k.images.moderation.SensitiveContentPolicyTest'`
- `./gradlew :bluetape4k-images:test`

## 남은 위험

- The policy action precedence is intentionally conservative, but application owners may still need route-specific precedence or audit persistence when connecting this to real moderation workflows.
