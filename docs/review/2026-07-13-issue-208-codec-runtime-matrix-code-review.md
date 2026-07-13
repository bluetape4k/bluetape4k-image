# Issue #208 Codec/Runtime Matrix Implementation Review

## Scope and Baseline

- Baseline: `origin/develop...HEAD`
- Issue: `#208`, milestone `0.4.0`
- Runtime lanes: Java 21 JVips JNI preflight and Java 25 FFM measurement
- Fixtures: `cafe.jpg` (`1920x1080`, web photo) and `homer.jpg`
  (`512x512`, profile)
- Evidence run: `issue-208-20260713-macos-arm64-09`
- Measurement commit: `999b1e87f764a175d9887af9972ed41644e37f9e`

## Six-Lens Review

| Lens | P0 | P1 | P2 | P3 | Result |
|---|---:|---:|---:|---:|---|
| Performance | 0 | 0 | 0 | 0 | Each measured cell pins scenario, format, direction, input hash, warmup/measurement protocol, latency, allocation, and encoded size. Charts compare only cells from the accepted Java 25 run. |
| Stability | 0 | 0 | 0 | 0 | Java 21 host incompatibility expands to 16 explicit `N/A` cells. Experimental AVIF/HEIC execution is capability-gated and excluded from the default stable task. |
| Security | 0 | 0 | 0 | 0 | Strict bounded JSON parsing, fixed evidence roots, traversal/symlink rejection, artifact hashes, and no-replace atomic promotion guard accepted evidence. |
| Operator/Ops | 0 | 0 | 0 | 0 | Preflight, immutable run IDs, failure ledgers, backend identity checks, sequential native execution, and reproducible commands make failure states diagnosable. |
| Developer/API | 0 | 0 | 0 | 0 | Production API and artifact coordinates are unchanged. Native runtime adapters stay in the benchmark source set; only the Java 25 internal codec probe changes in production code. |
| User/caller | 0 | 0 | 0 | 0 | English and Korean README tables are identical, distinguish `MEASURED` from `N/A`, avoid cross-runtime ranking, and link the report and immutable raw evidence. |
| Integration | 0 | 0 | 0 | 0 | The task graph, manifest finalizer, report, locale pair, chart assets, and accepted evidence agree on the same 32-cell matrix. |

## Findings and Repairs

| Priority | Finding | Repair and rerun evidence |
|---|---|---|
| P1 | The documented default `benchmarkBenchmark` command omitted the mandatory `codec.matrix.runId`, so a real invocation would fail when stable fixture preparation started. | Added a fresh run ID to both locale commands and to the documented dry run. The dry run now includes `codecMatrixPreflight` and `prepareCodecMatrixFixtures` and succeeds. |
| P1 | The benchmark build temporarily consumed the governed serialization alias through an external catalog path even though the repository must remain independently reproducible from a released catalog tag. | Switched to `libs.kotlinx.serialization.json` and documented a repo-local issue #208 version pin. The pin has an explicit removal condition: delete it after a release-train central catalog tag publishes the alias. No central dependency repository was modified. |
| P1 | A Java 21 benchmark compile immediately after Java 25 failed because the unused benchmark-module atomicfu transformer tried to load stale Java 25 class files (`69.0`) on Java 21 (`65.0`). | Added a contract assertion and disabled the unused benchmark-module JVM transform. The assertion failed before the repair; Java 25 then Java 21 `benchmarkBenchmarkCompile` passed sequentially without cleaning after it. |
| P2 | The design attributed all provenance fields to `run-manifest.json`, while the implementation intentionally distributes them across hash-linked preflight, fixture, JMH, size, capability, and report artifacts. | Corrected the design to describe the accepted evidence ledger rather than overstate the top-level manifest schema. |

The current central release-train tag observed during review was
`catalog/2026-07-08-00`; it does not contain the serialization JSON alias.
Removing the local version while resolving through that tag reproduced an
empty-version dependency failure. Restoring the temporary pin produced 70
passing benchmark-module tests.

## Integration and Hazard Evidence

- The accepted run contains exactly 32 terminal cells: 16 `MEASURED` Java 25
  cells and 16 Java 21 `N/A` cells.
- All 11 manifest-linked artifacts match their recorded SHA-256 and byte count;
  all 13 JSON files parse and the raw tree contains no symlinks.
- Accepted raw evidence is added append-only; no accepted file is modified or
  deleted relative to `origin/develop`.
- Both codec chart SVG files pass `xmllint`; the CairoSVG `-s 2` PNGs are
  `3120x1880` and `3120x1720` and were inspected at original size. The latency
  chart uses a complementary blue/orange pair for its two compared series;
  the four-series output-size chart retains its categorical palette.
- No settings, BOM, module registration, CI, Nightly, Kover, or public API
  change is present. The Java 25 backend atomicfu setting remains unchanged;
  only the benchmark module disables its unused transformer.
- The only catalog delta is the documented temporary serialization version pin.

## Verification

| Command or check | Result |
|---|---|
| `:bluetape4k-images-benchmark:test --rerun-tasks` on Java 25 | PASS, 70 tests |
| `:bluetape4k-images-benchmark:build` | PASS |
| Java 25 `benchmarkBenchmarkCompile` | PASS |
| Java 21 `benchmarkBenchmarkCompile` immediately after Java 25 | PASS |
| `:bluetape4k-images-benchmark:tasks --all` | PASS, all 11 codec tasks registered |
| Documented Java 25 `benchmarkBenchmark --dry-run` with run ID | PASS; stable preflight and fixture preparation included |
| `./gradlew detekt` | PASS (`NO-SOURCE`) |
| Manifest/hash/byte-count/JSON/symlink/append-only audit | PASS |
| README codec-table parity | PASS, identical values and links |
| `git diff --check` | PASS |

## Verdict

`PASS` — final integrated count is `P0=0`, `P1=0`, `P2=0`, `P3=0`.
Issue #208 is ready for PR and CI validation.
