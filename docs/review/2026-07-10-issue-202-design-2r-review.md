# Issue #202 Step 2-R Design Review

## Scope

Review the approved dependency-boundary design for
`bluetape4k-images-vips-api` before implementation. The review covers the
public opt-in contract, Java 21/25 backend compilation boundary, publication
metadata, documentation migration, and verification feasibility.

## Findings and Repairs

The first review pass found three blocking gaps:

1. Java 21/25 backend and API test sources still directly import
   `IncubatingImageApi`; removing the main API dependency would break their
   compilation.
2. Maven POM inspection alone does not prove that Gradle normal variants omit
   `bluetape4k-images`; the intentional test-fixture variant must be checked
   separately.
3. The design did not distinguish public `@VipsIncubatingApi` propagation from
   implementation-only `@OptIn`, leaving stable codec report usability
   ambiguous.

The design now requires all Vips API and Java 21/25 main/test opt-ins to
migrate to the new marker, validates both POM and Gradle Module Metadata,
applies the public marker only to AVIF/HEIC enum entries, and keeps report
containers implementation-opted-in but stable for callers. It also fixes the
marker target/message contract, strict compiler-fixture verification, README
examples, image-module KDoc boundary, and rollback criteria.

## Perspective Results

| Perspective | Final result | Evidence |
|---|---|---|
| Performance | PASS — P0/P1/P2/P3: 0/0/0/0 | Annotation/import and metadata changes do not alter JNI/FFM resource, codec, or benchmark hot paths. |
| Stability | PASS — P0/P1/P2/P3: 0/0/0/0 | API and backend main/test migration plus strict compiler fixtures close compilation failure modes. |
| Security | PASS — P0/P1/P2/P3: 0/0/0/0 | Normal Gradle variants and POM are checked separately from intentional fixtures; marker propagation is explicit. |
| Operator/release | PASS — P0/P1/P2/P3: 0/0/0/0 | Verification uses only generation/compile/test tasks; rollback stops before PR on POM or normal metadata leakage. |
| User/caller | PASS — P0/P1/P2/P3: 0/0/0/0 | Every AVIF/HEIC README example receives the import and scoped opt-in; stable reports remain usable. |
| Developer/API | PASS — P0/P1/P2/P3: 0/0/0/0 | Target set, ABI boundary, fixture scope, and no-dependency compiler fixture approach match current Kotlin/Gradle structure. |

## Final Verdict

**PASS.** No P0 or P1 finding remains. The approved design is ready for Step
3 implementation planning. No source code, publication, release, pull request,
or merge action occurred during this review.
