# Issue #272 ZXing Barcode Extraction Benchmark Design

## 1. Context

- Issue: [#272](https://github.com/bluetape4k/bluetape4k-image/issues/272)
- Milestone: `0.4.0`
- Work type: Type A - Full Feature
- Repository: `bluetape4k/bluetape4k-image`
- Base: `origin/develop`
- Branch: `perf/issue-272-zxing-barcode-benchmark`

`bluetape4k-images-barcode-zxing` provides a pure-JVM ZXing implementation of
the provider-neutral barcode API. Its tests prove QR, Code 128, and no-result
behavior, but the repository has no repeatable extraction latency or throughput
evidence. Issue #272 adds that evidence without changing the production barcode
contracts.

## 2. Goals

1. Measure ZXing extraction latency and throughput independently.
2. Cover representative QR, Code 128, and no-result shapes.
3. Exclude fixture generation, resource loading, and PNG decoding from the timed
   extraction operation.
4. Commit immutable fixture bytes, hashes, commands, environment metadata, raw
   JSON, result tables, and interpretation limits.
5. Keep the English and Korean benchmark README sections equivalent.

## 3. Non-goals

- Do not add or compare another barcode provider.
- Do not change `images-barcode-api` or `images-barcode-zxing` production APIs.
- Do not add a module, artifact, public API, BOM/catalog entry, or dependency
  version.
- Do not change CI, Nightly, Kover, native/JNI, OCR, or Testcontainers surfaces.
- Do not measure PNG loading, fixture generation, web delivery, or end-to-end
  request handling.
- Do not present one local run as a production-wide provider ranking.

## 4. Current Evidence

- `BarcodeReader.readBarcodes` is the provider-neutral synchronous extraction
  boundary.
- `ZxingBarcodeReader` creates and executes ZXing's `MultiFormatReader` inside
  each call and returns provider-neutral results.
- Existing provider tests generate QR and Code 128 images and verify that a
  blank image returns an empty result.
- `benchmark/images-benchmark` already uses `kotlinx-benchmark`, the JMH JVM
  target, named configurations, JSON reports, Gradle TestKit contracts, and
  bilingual result documentation.
- The approved baseline executed 14 barcode API tests and 8 ZXing provider
  tests successfully. The benchmark module tests and `tasks --all` also passed.

## 5. Considered Approaches

### 5.1 One benchmark class with a scenario parameter - selected

Use one `ZxingBarcodeExtractionBenchmark` and a fixed scenario parameter. Run
the same class through separate latency and throughput configurations.

Benefits:

- the two result sets share exactly the same operation and fixture setup;
- scenario coverage is easy to compare and validate;
- implementation duplication is minimal.

Trade-off: raw rows include a scenario parameter rather than distinct method
names.

### 5.2 Separate method per scenario - rejected

This produces explicit method names but duplicates setup and makes it easier for
one scenario to drift from the others.

### 5.3 Separate latency and throughput classes - rejected

This isolates modes but duplicates both state and extraction code. The two
measurement paths could diverge without adding user value.

## 6. Fixture Contract

Commit these immutable PNG inputs under
`benchmark/images-benchmark/src/main/resources/bench/barcode/`:

| Scenario | Shape | Expected result |
|---|---:|---|
| `qr` | square QR image | one `QR_CODE` with the pinned payload |
| `code-128` | wide linear image | one `CODE_128` with the pinned payload |
| `no-result` | square blank image | empty result list |

The same directory contains `manifest.json` with:

- schema version and SHA-256 algorithm;
- fixture id and classpath resource;
- width and height;
- exact SHA-256;
- expected payload and provider-neutral format for successful cases;
- explicit empty-result expectation for the blank case;
- generation provenance including ZXing writer version and parameters where
  applicable.

Runtime benchmark setup never regenerates or overwrites these inputs. It loads
the selected bytes, verifies hash and dimensions, decodes the PNG once, and
checks the expected barcode result before measurement starts.

The loader accepts exactly the three declared scenario ids, restricts resource
paths to the fixed `bench/barcode/` classpath prefix, rejects absolute paths and
`..` segments, and caps each encoded fixture at 1 MiB. These checks keep a
malformed manifest from turning a benchmark run into an unbounded or unrelated
resource read.

## 7. Benchmark Architecture

### 7.1 Fixture loader

An internal main-source fixture component parses the manifest, validates the
selected resource, and returns the immutable image plus its provider-neutral
expectation. It depends only on the existing image and serialization surfaces;
it does not depend on the ZXing provider. Tests exercise success and
corrupted/missing/hash-mismatch/path/size failure paths without exposing a
public API.

### 7.2 Benchmark state

`ZxingBarcodeExtractionBenchmark` uses one trial-scoped state with a fixed
`scenario` parameter. `@Setup` loads and validates the fixture and creates a
`ZxingBarcodeReader`. The timed benchmark method performs only:

```kotlin
reader.readBarcodes(image, options)
```

The method returns the result list so the JMH backend consumes the value. Result
allocation and ZXing decode work remain part of extraction; PNG I/O and
`ImmutableImage` construction do not.

### 7.3 Measurement configurations

Both configurations use the same benchmark class and scenarios:

| Configuration | Mode | Unit | Direction |
|---|---|---|---|
| `barcodeLatency` | `AverageTime` | `ms/op` | lower is better |
| `barcodeThroughput` | `Throughput` | `ops/s` | higher is better |

Each uses one thread, one fork, three one-second warmups, and five one-second
measurement iterations. Generated Gradle tasks are verified from `tasks --all`
before they are used in documentation or evidence collection.

## 8. Dependency Boundary

The benchmark source set adds `benchmarkImplementation` on
`:bluetape4k-images-barcode-zxing`; tests add the corresponding
`testImplementation`. The module's existing main `implementation` and published
dependency surface do not gain the provider. Benchmark setup and expectation
tests use `ZxingBarcodeReader` plus provider-neutral API models. They do not
import `com.google.zxing` or add a new external coordinate/version. ZXing
dependencies remain owned by the provider module.

## 9. Failure Handling

| Failure | Required behavior |
|---|---|
| Fixture is missing or unreadable | fail setup with the scenario/resource name |
| SHA-256 or dimensions differ | fail setup before any measurement |
| QR/Code 128 payload or format differs | fail setup before any measurement |
| Blank fixture returns a result | fail setup before any measurement |
| Manifest path escapes the fixed prefix or fixture exceeds 1 MiB | reject it before image decoding |
| Configuration scenario or timing contracts diverge | fail the Gradle contract test |
| Raw output is missing, partial, or would overwrite accepted evidence | reject it and use a new run id |
| Documentation overstates local evidence | block review until caveats are restored |

No extraction exception is converted into an empty result by the benchmark.
Provider behavior remains authoritative.

## 10. Test Strategy

Use RED/GREEN cycles for each behavior:

1. Fixture manifest parsing and complete three-scenario coverage.
2. Resource hash, dimensions, expected success result, and expected no-result.
3. Missing resource, changed bytes, and expectation mismatch failures.
4. Benchmark configuration names, modes, units, timing, fork/thread count,
   scenario coverage, and target include pattern.
5. Benchmark source-set compilation and focused latency/throughput smoke runs.

Targeted validation starts with the benchmark module and barcode provider, then
expands proportionally to repository static checks. Benchmark and any native
checks remain sequential; this benchmark itself is pure JVM and must not load a
libvips backend.

## 11. Evidence and Documentation

Each attempt uses a validated run id and writes only below a fresh build
directory. Accepted evidence is promoted once to
`benchmark/images-benchmark/docs/raw/issue-272-<run-id>/`; an existing target is
never overwritten. The accepted run records:

- exact Gradle commands;
- macOS/architecture/CPU, JVM vendor/version, and ZXing provider version;
- fixture ids, dimensions, payload class, and SHA-256 values;
- raw JSON paths for latency and throughput;
- a run manifest tying commands, environment, fixture hashes, and both raw
  files to the same attempt;
- six result rows: three scenarios in each mode;
- score error and interpretation caveats.

Write a detailed English report under `benchmark/images-benchmark/docs/` and
add equivalent concise sections to `README.md` and `README.ko.md`.

A chart is N/A for this issue: there is one provider, only three workload
shapes, and the two metrics have incompatible units/directions. A table presents
the six values more accurately. If later provider comparison introduces a
chart, exactly two series use a complementary pastel pair; three or more series
use a categorical palette.

## 12. Compatibility and Repository Hazards

- Existing barcode API/provider behavior and artifact coordinates remain
  unchanged.
- The existing benchmark module and kotlinx-benchmark plugin are reused; there
  is no module registration change.
- The provider is confined to benchmark/test configurations, so the published
  benchmark artifact dependency surface does not change.
- Benchmark sources remain excluded from production coverage.
- README locale parity is required.
- CHANGELOG and WIP updates remain owned by issues #270 and #271 so they are not
  updated prematurely here.
- The measured pure-JVM path does not require libvips, OCR, Docker, or network
  access.

## 13. Acceptance Criteria

- Both `barcodeLatency` and `barcodeThroughput` run through repository-supported
  Gradle benchmark tasks.
- QR, Code 128, and no-result scenarios use immutable, hash-pinned local PNGs.
- The timed operation excludes fixture generation, loading, and PNG decoding.
- Each accepted raw JSON contains exactly the three expected scenario rows with
  the configured mode, unit, thread, fork, warmup, and iteration contracts.
- Documentation includes command, environment, raw paths, result table, metric
  direction, and caveats.
- English and Korean README sections remain source-equivalent.
- Review converges at P0=0 and P1=0.

## 14. Definition of Done

- Spec and implementation plan are approved, reviewed, and committed.
- All test-first fixture and benchmark contracts pass.
- Both benchmark modes produce accepted raw evidence and six documented rows.
- Targeted compile/tests, relevant static checks, and `git diff --check` pass.
- A durable lesson and final review evidence are committed.
- The issue-linked PR has correct milestone, labels, assignee, final DoD body,
  green CI, and no unresolved P0/P1 findings.
- Merge remains blocked until a fresh merge-ready approval; after merge, local
  `develop` sync and merged worktree/branch cleanup are automatic.
