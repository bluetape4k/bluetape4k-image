# Issue #208 Codec/Runtime Matrix Spec Review

- Date: 2026-07-13
- Artifact: `docs/superpowers/specs/2026-07-13-issue-208-codec-runtime-matrix-design.md`
- Artifact kind: spec
- Review mode: six isolated main-session passes plus integration

The native collaboration interface available in this session does not accept
the mandatory installed `agent_type`. Unlabeled agent dispatch would violate
the workspace routing contract, so the model-routing fallback was used and the
main session performed each perspective as a separate read-only pass.

## Initial Findings

| Priority | Lens | Evidence | Required edit | Rerun lane |
|---|---|---|---|---|
| P1 | performance | Sections 7.1 and 7.3 named formats and metrics but did not freeze quality, effort, lossless, metadata, or measurement timing. | Pin one option/timing profile and forbid equivalent-quality claims for lossy WebP versus PNG. | performance |
| P1 | stability | Section 7.2 required skipped/unsupported rows without defining the artifact that produces those statuses. | Add a fail-closed capability snapshot task and define status/error semantics. | stability |
| P1 | operator/Ops | Experimental configurations had no exact task names or preflight ordering. | Name AVIF/HEIC tasks, capability task, evidence path, and execution hold. | operator/Ops |
| P2 | developer/API | Section 6 said center crop/resize but did not define deterministic ordering or source resolution. | Define cover-then-center-crop and exact source paths/checksums. | developer/API |
| P2 | user/caller | Optional chart wording could omit a useful comparison without a decision rule. | Require a chart for at least two comparable rows and evidence-backed N/A otherwise. | user/caller |
| N/A | security | The scope uses checked-in fixtures, sanitized capability reasons, and local benchmark output; it adds no external input, credential, network, deserialization, or publication boundary. | None. Retain fixture integrity and sanitized diagnostics tests. | security |

## Integrated Repairs

- Pinned `quality=85`, `effort=4`, lossy WebP, metadata stripping, and the
  one-warmup/three-measurement timing profile.
- Defined `codecMatrixCapabilityReport` and its structured JSON evidence,
  observation/failure semantics, and preflight hold.
- Named stable, AVIF, and HEIC Gradle tasks and excluded the experimental class
  from the default configuration.
- Defined exact source files, SHA-256 capture, and deterministic
  cover-then-center-crop fixture preparation.
- Added a measurable chart trigger and an evidence-backed N/A rule.

## Rerun Verdict

| Lens | P0 | P1 | Residual P2/P3 | Verdict |
|---|---:|---:|---|---|
| Performance | 0 | 0 | 0 | PASS |
| Stability | 0 | 0 | 0 | PASS |
| Security | 0 | 0 | 0 | PASS (scoped N/A risk surface) |
| Operator/Ops | 0 | 0 | 0 | PASS |
| Developer/API | 0 | 0 | 0 | PASS |
| User/caller | 0 | 0 | 0 | PASS |
| Main integration | 0 | 0 | 0 | PASS |

Final spec review convergence: **P0=0, P1=0**.
