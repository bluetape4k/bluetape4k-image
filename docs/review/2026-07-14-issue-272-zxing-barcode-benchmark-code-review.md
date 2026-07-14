# Issue #272 ZXing Barcode Benchmark Implementation Review

## Scope and Baseline

- Baseline: `origin/develop...4abcd2cd20f23838d1b74765d471035384754b05`
- Issue: `#272`, milestone `0.4.0`
- Module slice: `benchmark/images-benchmark`
- Provider: ZXing `3.5.4`
- Evidence run: `issue-272-20260714-macos-arm64-01`
- Review inputs: approved design, approved implementation plan, current diff,
  accepted raw JSON, locale documentation, fresh tests, dependency reports,
  CodeGraph change and impact analysis

The active collaboration interface does not expose the required native-agent
`agent_type` field. Per `model-routing.md`, the six perspectives were executed
as separate read-only main-session passes. The main session then integrated and
normalized their findings.

## Step 5 Verifier

| Accepted requirement | Current proof | Result |
|---|---|---|
| Supported latency and throughput tasks | `barcodeLatency`, `barcodeThroughput`, task listing, contract tests, and two successful real executions | PASS |
| Immutable QR, Code 128, and no-result PNGs | Three committed PNGs, strict manifest, SHA-256/dimension/provider tests | PASS |
| Timed extraction excludes setup | `ZxingBarcodeExtractionBenchmark.setup` loads, decodes, validates, and constructs the reader before `extractBarcodes` | PASS |
| Exact three-row protocol per mode | Finalizer validation plus accepted `latency.json` and `throughput.json` | PASS |
| Reproducible evidence and interpretation | Run manifest, detailed report, English/Korean README tables, raw links, metric direction, caveats | PASS |
| Dependency boundary | Main `runtimeClasspath` has the provider-neutral API but no ZXing; `benchmarkRuntimeClasspath` has the ZXing provider and core `3.5.4` | PASS |
| Append-only accepted run | Fresh-report timestamp check, collision rejection, atomic directory promotion, duplicate-finalization proof | PASS |
| Repository scope | No production barcode API/provider, settings, BOM, catalog, workflow, Nightly, Kover, native/JNI, OCR, or Testcontainers change | PASS |

All six plan tasks are complete. The provider test dependency was introduced in
Task 1 because the fixture expectation test needed it before Task 2, and the
provider-neutral API was added to main because the strict fixture loader owns
provider-neutral expectation types. Neither change leaks ZXing into the main
runtime classpath.

Verifier verdict: `PASS`. No hidden gap or deferred acceptance row remains.

## Six-Lens Review

| Lens | P0 | P1 | P2 | P3 | Final result |
|---|---:|---:|---:|---:|---|
| Performance | 0 | 0 | 0 | 0 | One parameterized timed call measures the same three preloaded images under pinned `avgt` and `thrpt` protocols. Latency and throughput are observed independently. |
| Stability | 0 | 0 | 0 | 0 | Strict fixture validation precedes timing; reports must be fresh, complete, finite, and staged once; accepted evidence is promoted atomically and append-only. |
| Security | 0 | 0 | 0 | 0 | JSON is strict and bounded; resource paths reject absolute, traversal, dot, empty-segment, and backslash forms; fixture bytes are bounded and hash-pinned. |
| Operator/Ops | 0 | 0 | 0 | 0 | Validated run IDs, sequential modes, host/JVM/provider metadata, exact commands, useful failure messages, and immutable raw artifacts support diagnosis and reruns. |
| Developer/API | 0 | 0 | 0 | 0 | New Kotlin types are internal, the public barcode contracts are unchanged, ZXing imports remain in benchmark/test code, and configuration-specific tests avoid cross-benchmark coupling. |
| User/caller | 0 | 0 | 0 | 0 | English and Korean summaries contain the same six values, links, directions, and local-snapshot caveat. A table is clearer than a chart for the one-provider mixed-unit result. |
| Integration | 0 | 0 | 0 | 0 | Spec, plan, tasks, fixtures, raw evidence, hashes, report, locale pair, and repository hazards agree on one bounded benchmark feature. |

## Findings and Repairs

| Priority | Finding | Repair and rerun evidence |
|---|---|---|
| P1 | The existing codec-matrix contract counted protocol literals across the whole Gradle file, so adding two barcode configurations broke an unrelated test. | Scoped every assertion to its named codec-matrix configuration. The focused regression test and the full 84-test benchmark suite pass. |
| P1 | Resource validation rejected `..` and absolute paths but still accepted dot segments, duplicate separators, or backslashes before a later hash failure. | Added error-specific RED tests and exact normalized-segment validation. The focused test and full suite pass. |
| P2 | Report validation checked `score` but not the documented `scoreError`. | Added a TestKit failure case plus finite, non-negative `scoreError` validation. The test failed on unexpected finalization before the repair and passes after it. |

## Performance, Stability, and Hazard Evidence

- CodeGraph analyzed the committed diff at medium change risk. Its wide generic
  symbol impact was reviewed against the actual file scope; no production
  execution flow changes because all executable changes stay in the benchmark
  module.
- The timed method contains no loading, decoding, generation, validation,
  reflection, blocking I/O, retry, lock, coroutine, or native work introduced
  by the harness. Returning the provider result keeps extraction allocations in
  the measurement.
- The Kotlin diff contains no new `!!`, `GlobalScope`, `runBlocking`, sleep,
  monitor, broad `runCatching`, or cancellation boundary.
- Accepted raw evidence contains four regular files. The canonical fixture
  manifest matches byte-for-byte, all three recorded artifact hashes recompute,
  and the tree contains no local absolute path or secret-like value.
- A second finalization attempt failed without changing any accepted hash.
- No chart asset changed. Chart N/A remains appropriate because the report has
  one provider and two metrics with incompatible units and directions. A future
  two-provider comparison must use the complementary pastel pair rule.
- CHANGELOG and WIP remain assigned to release issues `#270` and `#271`.

## Verification

| Command or check | Result |
|---|---|
| `:bluetape4k-images-benchmark:cleanTest :bluetape4k-images-benchmark:test :bluetape4k-images-benchmark:benchmarkClasses --no-build-cache` | PASS, 84 tests |
| Fresh barcode API and ZXing provider tests | PASS, 14 + 8 tests |
| `:bluetape4k-images-benchmark:build` | PASS |
| `:bluetape4k-images-benchmark:tasks --all` and `projects` | PASS; both modes and finalizer registered |
| `detekt` | PASS (`NO-SOURCE`) |
| Main vs benchmark runtime dependency reports | PASS; ZXing absent from main and present only in benchmark runtime |
| Six-row mode/unit/protocol audit | PASS, 3 latency + 3 throughput rows |
| Artifact hash, secret/path, locale value/link, and `git diff --check` audits | PASS |

## Verdict

`PASS` — final integrated count is `P0=0`, `P1=0`, `P2=0`, `P3=0`.
Issue #272 is ready for PR and exact-head CI validation.
