# Issue #208 Codec/Runtime Matrix Plan Review

- Date: 2026-07-13
- Artifact: `docs/superpowers/plans/2026-07-13-issue-208-codec-runtime-matrix-plan.md`
- Artifact kind: implementation plan
- Review mode: six isolated main-session passes plus integration

The native collaboration interface available in this session does not accept
the mandatory installed `agent_type`. Unlabeled agent dispatch would violate
the workspace routing contract, so each perspective was executed as a separate
read-only main-session pass and then integrated against the current artifact.

## Initial Findings and Repairs

| Priority | Lens | Finding | Repair | Rerun |
|---|---|---|---|---|
| P1 | performance | Eligible AVIF/HEIC rows had latency commands but no allocation-profiler addendum, and output-size ownership was ambiguous. | Added direction-specific GC-profiler commands and assigned stable versus experimental size sampling outside JMH. | PASS |
| P1 | stability | Java 21 and Java 25 shared a run ID but a single preflight path, allowing last-writer-wins evidence. | Backend-keyed preflight, eligibility, and size artifacts in both spec and plan. | PASS |
| P1 | security | Serializable evidence models did not explicitly forbid host-local `Path`/`File` values. | Restricted persisted paths to repository-relative strings and retained bounded diagnostic/leakage checks. | PASS |
| P1 | operator/Ops | Generated JMH reports could be selected ambiguously and experimental task ordering was incomplete. | Require start-time-bounded single-report staging; name all Sync/JavaExec/JMH tasks and their exact dependencies. | PASS |
| P1 | developer/API | Early drafts invoked fixture preparation before Gradle wiring, leaked Vips API toward `src/main`, and disagreed on the finalizer entrypoint name. | Kept Vips types in `src/benchmark`, delayed task invocation until wiring, and standardized `CodecMatrixFinalizeMain`. | PASS |
| P1 | user/caller | Internal `N_A` would serialize differently from the documented `N/A` terminal status. | Added `@SerialName("N/A")` and retained one bilingual status legend. | PASS |
| P1 | main integration | Preparation, capability, experimental fixture generation, report staging, and finalization were not initially one executable graph. | Added the exact task contract, run-ID propagation, source-set classpaths, dry-run proofs, rollback, and append-only promotion. | PASS |

## Perspective Rerun Verdict

| Lens | P0 | P1 | Residual P2/P3 | Verdict |
|---|---:|---:|---:|---|
| Performance | 0 | 0 | 0 | PASS — timing, allocation, and size boundaries share the pinned protocol. |
| Stability | 0 | 0 | 0 | PASS — per-backend facts, lifecycle, smoke blockers, and reruns fail closed. |
| Security | 0 | 0 | 0 | PASS — selectors, input paths, serialized paths, diagnostics, and promotion are constrained. |
| Operator/Ops | 0 | 0 | 0 | PASS — exact commands, outputs, sequencing, and stop conditions are executable. |
| Developer/API | 0 | 0 | 0 | PASS — source-set ownership, dependencies, tests, and commit boundaries are consistent. |
| User/caller | 0 | 0 | 0 | PASS — scenarios, statuses, metric caveats, locale parity, and chart trigger are explicit. |
| Main integration | 0 | 0 | 0 | PASS — every acceptance row maps to implementation, proof, evidence, and documentation. |

## Completion Checks

- Stable PNG/WebP scope is exactly four boundaries across `web-photo` and
  `profile`, producing eight rows per runnable backend.
- AVIF/HEIC stay opt-in, direction-specific, capability-gated, and absent from
  the default benchmark graph.
- `cafe.jpg` and `homer.jpg` are the only source fixtures; preparation is
  deterministic and occurs before any timed process.
- Java 21 JNI and Java 25 FFM execute sequentially with backend-keyed evidence;
  a known incompatible Java 21 native binary becomes `N/A` without loading it.
- Finalization is the only path to tracked raw evidence and rejects missing
  metrics, blocking states, hash mismatches, leakage, and overwrite.
- The plan stops at PR readiness and does not authorize PR creation or merge.

Final plan review convergence: **P0=0, P1=0**.
