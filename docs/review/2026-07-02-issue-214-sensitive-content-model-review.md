# 검토 - Issue #214 Sensitive Content Detection Model

날짜: 2026-07-02
범위: `bluetape4k-images` public model addition for backend-neutral sensitive-content detection results.

## 검토 관점

| 관점 | 판정 | 근거 |
|---|---|---|
| Tier 4 - 정확성 | PASS | Model validates confidence, blank labels/backend fields, normalized coordinates, pixel bounds, polygon closure, polyline openness, and mask dimensions. |
| Tier 5 - 테스트 범위 | PASS | `SensitiveContentModelsTest` covers accepted model construction and major invalid contracts. |
| Tier 7 - 문서 | PASS | `images/README.md` and `images/README.ko.md` describe the model, geometry variants, validation, non-goals, and caller policy risks. |
| 공개 API/KDoc | PASS | New public enums/data classes have English KDoc and keep detector runtime/policy actions out of core. |
| CodeGraph | PASS | `get_review_context_tool` reported low risk and no impacted nodes; untracked files were passed explicitly. |

## 발견 사항

P0/P1 findings: 0.

P2/P3:

- P3: Initial test used a nullable safe-call for region validation. Fixed by capturing `detection.region.shouldNotBeNull()` before comparing geometry.

## 검증 근거

- Red test: `:bluetape4k-images:compileTestKotlin` failed before implementation because `SensitiveContent*` model types were unresolved.
- `./gradlew :bluetape4k-images:test --tests 'io.bluetape4k.images.moderation.SensitiveContentModelsTest' --no-daemon`: PASS, 9 tests.
- `./gradlew :bluetape4k-images:test --no-daemon`: PASS, 598 PASSing / 18 pending.
- `git diff --check`: PASS.
- MockK lifecycle check: no MockK usage in the touched test.

## 남은 위험

No ML detector runtime, model packaging, policy action, or redaction rendering is included by design. Future detector modules must preserve `rawBackendLabel` while mapping into stable categories.
