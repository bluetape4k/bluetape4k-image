# Issue #208 Codec/Runtime Matrix Plan Review

- Date: 2026-07-13
- Artifact: `docs/superpowers/plans/2026-07-13-issue-208-codec-runtime-matrix-plan.md`
- Artifact SHA-256: `caeff9e8ff24d825f9250efcb26378279d9f5ba573850b784ff6c1243f981341`
- Artifact kind: implementation plan
- Review mode: six isolated read-only role lenses plus main integration

## Initial Findings

| Priority | Lens | Finding | Repair |
|---|---|---|---|
| P1 | Performance | Default/focused/direct-profiler protocols could drift; experimental profiler included ineligible directions; chart keys were incomplete. | Pinned class/config/CLI protocol parity, derived profiler regexes from direction eligibility, required exact latency/allocation cell equality, and enumerated the full comparability key. |
| P1 | Stability | Capability and experimental preparation were sibling dependencies; Java 21 `N/A` did not expand to terminal cells; failed attempts lacked durable lineage tests. | Enforced the output-provider chain, expanded `N_A` preflight to all expected backend cells while rejecting native artifacts, and added immutable failed-attempt ledger plus one-way replacement lineage tests. |
| P1 | Security | Fixture/finalizer caller paths widened trust; promotion races/symlinks were underspecified. | Derived fixed roots from the pinned working directory, rejected traversal and symlinks, required locked no-replace atomic promotion, and added strict bounded JSON plus full raw-tree leakage scans. |
| P1 | Operator/Ops | JMH jar selection could choose stale output; clean/prerequisite/rollback rules were not fail closed. | Staged the exact `Jar.archiveFile` provider with freshness/class/hash checks, added prerequisite and run-path absence gates, and defined append-only correction through a new superseding run. |
| P1 | Developer/API | A concurrent draft opened catalog-version scope, replacement lineage implied mutating an old ledger, and dynamic JMH filtering lacked a concrete plugin API and RED/GREEN functional proof. | Forbade catalog changes, made lineage new-run-to-old-ledger only, used the actual kotlinx-benchmark 0.4.17 `JavaExec` parameter-file contract with `onlyIf`/`setArgs`, and added TestKit RED/GREEN cases. |
| P1 | User/caller | Local results could be over-generalized; locale parity and rerun commands were not executable contracts. | Required a local-only/no-production-ranking statement, a value/command/link parity ledger, fresh run IDs, and tested Gradle-provider mapping for `supersedes` and failed-attempt replacement. |

No P0 finding was reported. All P1 findings were repaired in the plan and the affected lens was rerun after each repair.

## Integration Decisions

- `src/main` stays Vips-free; the selected runtime/image adapter and native entrypoints stay under `src/benchmark`.
- Backend facts use backend-keyed preflight, eligibility, size, latency, and allocation artifacts under one run ID, avoiding last-writer-wins evidence.
- `prepareExperimentalCodecMatrixFixtures` consumes the capability task output provider, enforcing preflight -> stable fixtures -> capability -> experimental fixtures -> JMH.
- The installed kotlinx-benchmark 0.4.17 source shows one JMH jar per target containing the compiled benchmark source set. Therefore a configuration-specific AVIF/HEIC jar is neither available nor required; the plan instead stages the exact target `Jar.archiveFile` and verifies both matrix classes before profiler use.
- Accepted and failed raw evidence is immutable. `supersedes` and `replaces-failed-attempt` create forward references only from a new manifest to an existing manifest hash.
- Catalog/BOM/settings/CI/Nightly/public API changes remain out of scope. Dependency-resolution failure reopens approval rather than granting an inline catalog fix.

## Final Rerun Verdict

| Lens | P0 | P1 | Residual P2/P3 | Verdict |
|---|---:|---:|---:|---|
| Performance | 0 | 0 | 0 | PASS |
| Stability | 0 | 0 | 0 | PASS |
| Security | 0 | 0 | 0 | PASS |
| Operator/Ops | 0 | 0 | 0 | PASS |
| Developer/API | 0 | 0 | 0 | PASS |
| User/caller | 0 | 0 | 0 | PASS |
| Main integration | 0 | 0 | 0 | PASS |

Main integration rechecked all 11 tasks, exact class/task names, vips-free source-set boundaries, TDD order, task isolation, evidence immutability, documentation parity, and the PR stop boundary against the approved spec.

Required checks: 7/7; N/A: 0; Blocked: 0.

Final plan review convergence: **P0=0, P1=0**.

## Concurrent-Change Boundary

During this plan review, another process committed `7e405dc` (`feat: add codec matrix manifest model`). That implementation commit is outside this review's mutation scope and was neither reverted nor included as evidence that later implementation tasks pass. This artifact approves only the current plan; implementation verification must still follow its RED/GREEN and review gates.
