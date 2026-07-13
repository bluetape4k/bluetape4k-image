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

## Independent Rerun After Concurrent Commit

While the current session was reviewing the artifact, commit `d9560bc` added
the repairs above. The current session preserved that commit and reran the six
required perspectives against its exact contents. The rerun found additional
blocking ambiguity that the first review had not covered:

| Priority | Lens | Evidence | Repair |
|---|---|---|---|
| P1 | performance | Experimental rows lacked exact directions/input bytes; fixture equality and the JMH protocol were not manifest-enforced. | Defined four exact method families, canonical hash-pinned fixtures, one protocol, and host/environment equivalence keys. |
| P1 | stability | `smokeTestCodec` could receive a JPEG while reporting an AVIF/HEIC smoke, and available-but-failed smoke was `SKIPPED`. | Required same-codec pinned smoke bytes, fresh JVM lifecycle, close tracking, and blocking `FAILED_SMOKE`. |
| P1 | security | Selector typos silently selected Java 25 and diagnostic sanitization was underspecified. | Added an exact selector allowlist, requested/actual identity check, fixed reason codes, bounded sanitization, and leakage scanning. |
| P1 | operator/Ops | The only capability snapshot lived under ignored `build/`, with no run manifest or retry retention contract. | Added atomic promotion into append-only tracked raw evidence, hashes, run manifests, and supersession/rollback rules. |
| P1 | developer/API | Experimental task dependencies, source-set test seam, and direct invocation behavior were undefined. | Added Gradle-enforced preflight, internal injected `src/main` components, and direct-task fail-fast behavior. |
| P1 | user/caller | Directional statuses and available-but-smoke-failed behavior could mislead readers. | Added cell-scoped status semantics, a shared bilingual legend, reasons, and rerun guidance. |

The selected binding-neutral design, module boundary, public API scope, and
issue acceptance criteria did not change. These edits make the chosen design
executable and fail closed.

Affected-lens rerun is required after these repairs; until that rerun completes,
the effective convergence state is **P0=0, P1=PENDING**.

## Final Affected-Lens Rerun

The repaired artifact was reopened from disk and every affected perspective was
rerun read-only. Findings that remained blocking were repaired and the affected
lane was rerun again; agent assertions were not accepted without inspecting the
current diff.

| Lens | P0 | P1 | Residual P2/P3 | Verdict |
|---|---:|---:|---:|---|
| Performance | 0 | 0 | 0 | PASS |
| Stability | 0 | 0 | 0 | PASS |
| Security | 0 | 0 | 0 | PASS |
| Operator/Ops | 0 | 0 | 0 | PASS |
| Developer/API | 0 | 0 | 0 | PASS |
| User/caller | 0 | 0 | 0 | PASS |
| Main integration | 0 | 0 | 0 | PASS |

Main integration verified that task names are consistent, preflight and fixture
dependencies are fail closed, eligibility is distinct from finalized evidence,
the `src/main` seam remains vips-free, and default task graphs do not acquire an
experimental codec dependency.

Effective final spec review convergence: **P0=0, P1=0**.

## Affected-Lens Rerun Verdict

The repaired artifact was reopened against the repository benchmark task graph,
the kotlinx-benchmark 0.4.17 task implementation, and the issue acceptance
criteria. One integration blocker remained: fixture preparation, cross-process
run identity, task dependencies, and evidence promotion were described but not
given executable Gradle task boundaries. The spec now names
`prepareCodecMatrixFixtures` and `finalizeCodecMatrixEvidence`, defines their
run-ID and no-overwrite contracts, and assigns dependencies without pulling
native probes into compile/build/check/test tasks.

| Lens | P0 | P1 | Residual P2/P3 | Verdict |
|---|---:|---:|---:|---|
| Performance | 0 | 0 | 0 | PASS — one protocol and manifest are pinned across timed and profiler runs. |
| Stability | 0 | 0 | 0 | PASS — preparation, smoke, timed work, retry, and finalization fail closed. |
| Security | 0 | 0 | 0 | PASS — selectors, paths, diagnostics, and promoted evidence are constrained and sanitized. |
| Operator/Ops | 0 | 0 | 0 | PASS — exact tasks, run identity, append-only promotion, and rerun points are assigned. |
| Developer/API | 0 | 0 | 0 | PASS — source-set seams, task ordering, and direct invocation behavior are executable. |
| User/caller | 0 | 0 | 0 | PASS — every cell has one scoped status, reason, and rerun guidance. |
| Main integration | 0 | 0 | 0 | PASS — acceptance criteria, hazards, ownership, rollback, and proof are aligned. |

Latest effective spec review convergence: **P0=0, P1=0**.

## Plan-Integration Addendum

The implementation-plan review exposed one cross-backend evidence collision in
the otherwise approved task contract: Java 21 and Java 25 commands share one
run ID, so a single `preflight.json` or `eligibility.json` path could overwrite
the other backend's facts before finalization. The spec now assigns
`preflight-<backend>.json`, `eligibility-<backend>.json`, and
`sizes-<backend>.json`. This is a storage-key clarification only; it does not
change the selected design, benchmark boundary, or acceptance scope.

The stability, operator/Ops, developer/API, and integration lenses were rerun
against the backend-keyed contract. Each backend has an immutable evidence
slot, stable fixtures remain shared by hash, and finalization can prove both
runtime outcomes without last-writer-wins behavior.

Latest post-plan-integration spec convergence: **P0=0, P1=0**.
