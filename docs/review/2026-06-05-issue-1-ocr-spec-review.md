# Issue #1 OCR Spec Review

- Step: 2-R
- Spec:
  `docs/superpowers/specs/2026-06-05-issue-1-ocr-design.md`
- Reference:
  `/Users/debop/.codex/skills/bluetape4k-full-feature/references/step-2r-spec-review.md`
- Date: 2026-06-05

## Verdict

P0 = 0
P1 = 0

The spec is ready for Step 3 planning after the P2 clarity findings below were
applied to the spec.

## Perspective Reviews

| Perspective | P0 | P1 | P2 | P3 | Evidence |
|---|---:|---:|---:|---:|---|
| Developer/Kotlin | 0 | 0 | 1 | 0 | API uses optional module, `OcrEngine`, options/result models, suspend wrapper |
| Security | 0 | 0 | 0 | 0 | No credentials/network OCR provider; Tesseract variables are caller-controlled but local-only |
| Ops/SRE | 0 | 0 | 2 | 0 | Native runtime install, language-pack preflight, Testcontainers evidence boundary |
| User/Caller | 0 | 0 | 0 | 0 | README/KDoc/troubleshooting and unsupported backend scope are explicit |

## Local 7-Tier Review

| Tier | P0 | P1 | P2 | P3 | Notes |
|---|---:|---:|---:|---:|---|
| Tier 1 Security | 0 | 0 | 0 | 0 | Local OCR only; no auth, network, cloud credentials, or deserialization boundary introduced |
| Tier 2 Ops/SRE | 0 | 0 | 1 | 0 | Added `tesseract --list-langs` preflight so CI fails before Gradle when language packs are missing |
| Tier 3 Structural | 0 | 0 | 0 | 0 | Separate module keeps core dependency surface clean and matches optional native module pattern |
| Tier 4 Kotlin/API | 0 | 0 | 1 | 0 | Added enum wrappers for Tess4J integer constants so ordinary callers do not import `ITessAPI` |
| Tier 5 Tests/Types | 0 | 0 | 1 | 0 | Added explicit Testcontainers image ownership strategy and host-native/container evidence split |
| Tier 6 Performance/Stability | 0 | 0 | 0 | 0 | Fresh Tess4J instance per call avoids shared mutable state; suspend path uses `Dispatchers.IO` |
| Tier 7 Docs/Release/Evidence | 0 | 0 | 0 | 0 | README locale set, diagrams, CI/Nightly, BOM, AGENTS, and review evidence are in scope |

## Resolved Findings

| Priority | Finding | Resolution |
|---|---|---|
| P2 | Spec named `TesseractEngineMode` and `TesseractPageSegmentationMode` but did not state why they exist or how they map. | Added enum wrapper rule around Tess4J integer constants. |
| P2 | Testcontainers lane did not specify whether to trust a public image or build a test-owned runtime. | Added test-owned Dockerfile strategy and rejected unverified public OCR images. |
| P2 | CI lane installed language packs but did not explicitly preflight them before Gradle. | Added `tesseract --list-langs` preflight requirement. |

## Accepted Non-Blocking Risk

| Priority | Risk | Rationale |
|---|---|---|
| P2 | Exact Korean/Japanese OCR string matching may be flaky because font rendering and OCR recognition vary by host. | Spec requires language-pack availability and allows exact non-Latin matching to become follow-up if unreliable. English OCR remains the blocking native text extraction proof. |

## Convergence

After the spec edits above:

- P0 = 0
- P1 = 0
- Remaining P2 = 1 accepted with rationale
- P3 = 0

Step 2-R is closed.
