# Review - Issue #212 VIPS Codec Capability Report

Date: 2026-07-02
Scope: `images-vips-api`, `images-vips-java21`, and `images-vips-java25` AVIF/HEIC capability reporting and smoke helpers.

## Review Lenses

| Lens | Verdict | Evidence |
|---|---|---|
| Tier 4 - Correctness | PASS | `VipsCodecSupport` distinguishes `AVAILABLE`, `UNAVAILABLE`, and `UNKNOWN`; Java 21 reports JVips introspection gaps as `UNKNOWN`; Java 25 probes `heifload_buffer` and `heifsave_buffer`. |
| Tier 5 - Test Coverage | PASS | API, Java 21, and Java 25 tests cover stable formats, backend-specific HEIF-family capability states, sanitized smoke failures, and smoke-result invariants. |
| Tier 7 - Documentation | PASS | Root, API, Java 21, and Java 25 READMEs are updated in English and Korean with report/smoke guidance. |
| Public API/KDoc | PASS | New public enums/data classes and `VipsRuntime` functions have English KDoc and use serializable model types. |
| bluetape4k Patterns | PASS | Tests use bluetape4k assertions; Java 25 probe tests use a field seam reset in lifecycle methods instead of repeated MockK operation mocking. |

## Findings

P0/P1 findings: 0.

P2/P3:

- P3: Initial smoke result model allowed partial failure states without a failure stage. Fixed by requiring failure stage and reason unless both decode and encode succeeded.
- P3: Initial smoke helper wrapped decode and encode in one block. Fixed by splitting decode and encode stages so sanitized failures report the correct stage.

## Validation Evidence

- Red tests failed before implementation due to unresolved `VipsCodec*`, `codecCapabilityReport`, `smokeTestCodec`, and `FfmVipsCodecProbe` references.
- `./gradlew :bluetape4k-images-vips-api:test :bluetape4k-images-vips-java21:test :bluetape4k-images-vips-java25:test --configuration-cache --build-cache`: PASS, `BUILD SUCCESSFUL`, 5 passing / 41 pending in final aggregate output with API reporting 19 passing.
- `./gradlew :bluetape4k-images-vips-api:compileTestKotlin :bluetape4k-images-vips-java21:compileTestKotlin :bluetape4k-images-vips-java25:compileTestKotlin --warning-mode all --configuration-cache --build-cache`: PASS, `BUILD SUCCESSFUL`; no new Kotlin warnings observed.
- `git diff --check`: PASS.
- MockK lifecycle check: no MockK usage in the touched tests.

## Remaining Risk

Java 21 JVips still cannot prove native HEIF-family operation availability through the binding. This is intentionally surfaced as `UNKNOWN`; deployment hosts should run `smokeTestCodec(...)` with caller-provided samples before advertising AVIF/HEIC support.
