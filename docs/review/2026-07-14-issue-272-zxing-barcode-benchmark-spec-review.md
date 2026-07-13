# Issue #272 ZXing Barcode Benchmark Spec Review

## Scope

- Artifact: `docs/superpowers/specs/2026-07-14-issue-272-zxing-barcode-benchmark-design.md`
- Artifact kind: spec
- Research basis: issue #272, barcode API/provider source and tests, issue #247
  fixture decisions, existing `images-benchmark` configurations and reports
- Lenses: performance, stability, security, operator/Ops, developer/API,
  user/caller, followed by main-session integration

The active native-agent interface does not expose the required `agent_type`
field. Per `model-routing.md`, each required lens was therefore executed as a
separate read-only main-session pass rather than inventing agent roles.

## Initial Findings

| Priority | Lens | Evidence | Required edit | Resolution |
|---|---|---|---|---|
| P1 | Developer/API | Sections 7-8 did not constrain the new provider dependency configuration. | Keep ZXing on `benchmarkImplementation` and `testImplementation`; preserve the main/published dependency surface. | Fixed in sections 7.1, 8, and 12. |
| P1 | Operator/Ops | Sections 9 and 11 required raw JSON but did not define collision-safe accepted-run ownership. | Use a validated run id, fresh build staging, append-only accepted directory, and one run manifest. | Fixed in sections 9 and 11. |
| P2 | Security | The manifest-controlled resource path and encoded input size had no explicit bounds. | Restrict the classpath prefix, reject traversal/absolute paths, and cap each fixture at 1 MiB. | Fixed in sections 6, 7.1, 9, and 10. |

## Rerun Verdicts

| Lens | Verdict | Evidence |
|---|---|---|
| Performance | PASS | Sections 7.2-7.3 isolate `readBarcodes`, use identical scenarios for both modes, and pin thread/fork/warmup/measurement conditions. |
| Stability | PASS | Sections 6, 9, and 11 fail before measurement on fixture/expectation errors and prevent accepted-evidence overwrite. |
| Security | PASS | Sections 6 and 9 bound manifest resource paths and bytes; there are no external inputs, secrets, or network calls. |
| Operator/Ops | PASS | Sections 9 and 11 define run identity, staging, immutable promotion, environment capture, and rerun behavior. |
| Developer/API | PASS | Section 8 keeps ZXing imports in the provider and provider dependencies out of the benchmark module's main/published surface. |
| User/caller | PASS | Sections 11-13 require runnable commands, metric directions, bilingual README parity, and conservative interpretation. |

## Integration Verdict

- Alternatives, boundaries, compatibility, failure modes, testability, and
  acceptance criteria are explicit.
- Chart N/A is evidence-backed: one provider, three workload shapes, and two
  metrics with incompatible units and directions.
- CHANGELOG/WIP remain correctly deferred to #270/#271.
- Latest convergence: **P0=0, P1=0**. The P2 manifest-bound finding is fixed.

Required checks: 7/7; N/A: 0; Blocked: 0.
