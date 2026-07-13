# Issue #272 ZXing Barcode Benchmark Plan Review

## Scope

- Artifact: `docs/superpowers/plans/2026-07-14-issue-272-zxing-barcode-benchmark-plan.md`
- Artifact kind: plan
- Basis: approved design spec, issue #272, current barcode/provider tests,
  existing kotlinx-benchmark task and evidence patterns
- Lenses: performance, stability, security, operator/Ops, developer/API,
  user/caller, followed by integration and Step 3-R ordering review

The active native-agent interface has no `agent_type` field. The six required
lenses were executed as separate read-only main-session passes under the
documented model-routing fallback.

## Initial Findings

| Priority | Lens/area | Evidence | Required plan edit | Resolution |
|---|---|---|---|---|
| P1 | Developer/API | Task 1 referenced `BarcodeBenchmarkFixture` and `loadForTest` without defining their signatures. | Define the runtime wrapper and exact injected test-loader API before later tests use them. | Fixed in Task 1 Steps 3-4. |
| P1 | Security | Entry path validation occurred only when one scenario was selected. | Put path and semantic validation in every decoded manifest entry. | Fixed in Task 1 Step 3. |
| P1 | Stability/Ops | The temporary generator could overwrite an existing source fixture directory. | Refuse an existing output path and regenerate the complete PNG+manifest set as one reviewed unit. | Fixed in Task 1 Steps 5-6. |
| P2 | Build/API | Provider configuration inspection had no exact command or expected contrast. | Compare `runtimeClasspath` and `benchmarkRuntimeClasspath` explicitly. | Fixed in Task 2 Step 6. |

## Lens Rerun

| Lens | Verdict | Evidence |
|---|---|---|
| Performance | PASS | Tasks 2 and 4 isolate the extraction call, pin two real JMH modes, and prohibit reciprocal metric derivation. |
| Stability | PASS | Tasks 1, 3, and 4 validate fixtures/reports before timing and use fresh staging plus append-only promotion. |
| Security | PASS | Task 1 covers strict JSON, exact scenario set, normalized fixed-prefix paths, size bounds, hashes, and malformed inputs. |
| Operator/Ops | PASS | Tasks 3-4 define run ownership, collision behavior, environment capture, failure cleanup, and sequential rerun points. |
| Developer/API | PASS | Tasks 1-2 define all used types before consumers and confine the provider to benchmark/test configurations. |
| User/caller | PASS | Task 5 assigns exact report/README content, metric directions, locale parity, links, and caveats. |

## Step 3-R Integration

- Every spec acceptance criterion maps to an ordered task and fresh proof.
- No task consumes fixture bytes, task names, raw JSON, or documentation before
  its producer task completes.
- Success, missing/malformed input, traversal, oversize, hash/dimension drift,
  expectation mismatch, stale report, wrong row/mode/unit, duplicate accepted
  target, and documentation drift are assigned.
- Concurrency, coroutine cancellation, HTTP, Spring, Exposed, Testcontainers,
  OCR, native/JNI, new-module registration, migration, and closeable-resource
  lifecycle are N/A from the approved synchronous pure-JVM existing-module
  scope. Task 6 requires this evidence to be rechecked against the final diff.
- README English/Korean parity, English benchmark KDoc, issue-linked PR metadata,
  benchmark hazards, rollback/rerun points, and lesson capture are assigned.
- The plan contains no unresolved manual hash substitution; the reviewed
  generator creates all fixture bytes and the hash-pinned manifest together.

Latest convergence: **P0=0, P1=0**. The one P2 finding is fixed.

Required checks: 21/21; N/A: 8; Blocked: 0.
