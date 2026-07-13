# Issue #208 Codec/Runtime Matrix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a fail-closed, reproducible PNG/WebP codec benchmark matrix with opt-in AVIF/HEIC evidence for the Java 21 JNI and Java 25 FFM libvips runtimes.

**Architecture:** Internal main-source harness components prepare hash-pinned fixtures, probe the selected runtime, serialize eligibility/run manifests, and finalize append-only evidence. JMH source files contain only trial setup and measured transcode calls; stable tasks consume the same canonical manifest, while experimental tasks depend on direction-specific capability and smoke gates.

**Tech Stack:** Kotlin 2.3, Java 21/25 toolchains, Gradle Kotlin DSL, kotlinx-benchmark 0.4.17/JMH, kotlinx-serialization JSON, Scrimage, libvips through JVips JNI or vips-ffm, JUnit 5, bluetape4k assertions.

---

## Preconditions and Boundaries

- Worktree: `.worktrees/perf-issue-208-codec-runtime-matrix`
- Branch: `perf/issue-208-codec-runtime-matrix`
- Base: `origin/develop` at `feb75001a35fceb53f976a982e7d44a1eb28e204`
- Approved spec: `docs/superpowers/specs/2026-07-13-issue-208-codec-runtime-matrix-design.md`
- Scope is limited to `bluetape4k-images-benchmark`, its benchmark evidence, both benchmark README locales, and triggered chart assets.
- Do not change `VipsImage`, `VipsRuntime`, JVips, FFM, BOM, catalog versions, module registration, CI, Nightly, or production APIs.
- Keep historical `VipsBenchmarkState` and `VipsBackendEncodeBenchmark.vips_encodeJpeg` unchanged.
- Run all JNI/FFM/capability/JMH commands sequentially. Diagnose failed native attempts before a fresh-process rerun.
- After every `.kt` edit, run IDE diagnostics when available, optimize imports, and resolve all errors and deprecations before Gradle compilation. When IDE tooling is unavailable, record that limitation and use the focused Kotlin compile/test command as fallback evidence.
- Stop after implementation review, lesson commit, and PR readiness. PR creation and merge remain explicit user boundaries.

## File and Ownership Map

| File | Responsibility |
|---|---|
| `benchmark/images-benchmark/build.gradle.kts` | strict selector, dependencies, named configs, prepare/capability/finalize tasks |
| `benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixModels.kt` | scenarios, statuses, reason codes, manifests |
| `benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixJson.kt` | canonical JSON, SHA-256, atomic writes, validation |
| `benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixFixtures.kt` | fixed-source preparation and fixture manifest |
| `benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixPreflight.kt` | vips-free selector, host/JDK/binary preflight, diagnostics |
| `benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixCapability.kt` | vips-free capability/smoke DTO and operation seam |
| `benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixFixtureMain.kt` | stable fixture preparation CLI |
| `benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixPreflightMain.kt` | non-native preflight CLI |
| `benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixFinalizeMain.kt` | non-native finalization/promotion CLI |
| `benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/CodecMatrixRuntimeAdapter.kt` | selected Vips runtime/image adapter with no fallback |
| `benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/CodecMatrixCapabilityMain.kt` | selected-backend capability and directional-smoke CLI |
| `benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/CodecMatrixExperimentalFixtureMain.kt` | eligible AVIF/HEIC target-input CLI |
| `benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/VipsCodecMatrixBenchmark.kt` | stable state and four measured boundaries |
| `benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/VipsExperimentalCodecMatrixBenchmark.kt` | opt-in AVIF/HEIC states and methods |
| `benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/CodecMatrixModelsTest.kt` | manifest/status invariants |
| `benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/CodecMatrixFixturesTest.kt` | fixture determinism/path/magic |
| `benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/CodecMatrixRuntimeTest.kt` | selector/preflight/sanitizer |
| `benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/CodecMatrixCapabilityTest.kt` | direction/smoke/close ownership |
| `benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/CodecMatrixEvidenceFinalizerTest.kt` | hashes/cells/no-overwrite |
| `benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/CodecMatrixBenchmarkContractTest.kt` | JMH/config/task graph contract |
| `benchmark/images-benchmark/docs/raw/<run-id>/` | finalized append-only evidence |
| `benchmark/images-benchmark/docs/codec-runtime-matrix-2026-07-13.md` | detailed report |
| `benchmark/images-benchmark/README.md` / `README.ko.md` | equivalent summary and links |
| `docs/images/readme-charts/images-benchmark-codec-runtime-matrix-chart-01.svg` / `.png` | conditional comparable chart |
| `docs/lessons/2026-07-13-issue-208-codec-runtime-matrix.md` | durable lesson |

## Acceptance Traceability

| Requirement | Tasks | Proof |
|---|---|---|
| PNG/WebP four-boundary matrix, two scenarios | 2, 7 | 8 stable JMH rows per runnable backend |
| Identical inputs across backends | 2, 6 | one run ID and manifest hashes |
| No lazy-open/no-op timing | 7, 8 | forced output and no `bh.consume(null)` |
| Direction-specific AVIF/HEIC | 4, 8 | cell eligibility, smoke, focused task graph |
| Terminal statuses and blockers | 1, 4, 5 | validators and negative tests |
| Latency/allocation/input/output bytes | 5, 9 | JMH, GC profiler, size artifacts |
| Runtime/environment evidence | 3, 4, 9 | preflight/capability/run manifests |
| Default experimental isolation | 6, 8 | dry-runs and contract tests |
| README/report/locale parity | 10 | paired docs and link checks |
| Comparable chart only | 10 | diagram ledger or evidence-backed N/A |
| Review/lesson | 11 | P0/P1=0 and committed lesson |

## Risk Prediction

| Risk | Signal | Mitigation | Rerun/rollback |
|---|---|---|---|
| Backend inputs differ | hash mismatch | prepare once with explicit run ID | discard run and restart native evidence |
| Java 21 binary incompatible | arm64 host/x86_64 JNI | non-native preflight emits `N_A` | do not invoke JNI JMH |
| Capability lies about operation | malformed/failed transcode | blocking `FAILED_SMOKE` | diagnose and use new run ID |
| Native lifecycle leak | retry-only pass/close mismatch | `use`, fresh process per lane, no `shutdown()` | invalidate and rerun lane |
| Experimental graph leakage | default dry-run includes probe/task | explicit exclusion and graph tests | repair before native work |
| Evidence leaks local data | paths/secret-like token | fixed reasons, sanitizer, promotion scan | reject staging and regenerate |
| GC protocol differs | row/iteration/thread mismatch | pin direct JMH flags | rerun profiler lane |
| Promotion overwrites evidence | target exists/partial move | atomic append-only promotion | new run with `supersedes` |

### Task 1: Define Manifest and Status Invariants

**Complexity:** High
**Depends on:** approved spec
**Pattern skills:** `bluetape-kotlin-patterns`, `references/testing.md`
**Files:** create `CodecMatrixModels.kt`, `CodecMatrixJson.kt`, `CodecMatrixModelsTest.kt`; modify module `build.gradle.kts`.

- [ ] **Step 1: Add failing invariant tests**

Apply `alias(libs.plugins.kotlin.serialization)` and
`implementation(libs.kotlinx.serialization.json)`. Preserve the existing
`benchmarkImplementation(project(":bluetape4k-images-vips-api"))`; do not add
Vips API types to the main source-set dependency graph. Test:

```kotlin
@Test
fun `eligibility manifest cannot claim measured`() {
    assertFailsWith<IllegalArgumentException> {
        eligibilityManifest(CodecMatrixCellStatus.MEASURED).validateEligibility()
    }
}

@Test
fun `accepted manifest rejects blocking states`() {
    assertFailsWith<IllegalArgumentException> {
        finalizedManifest(CodecMatrixCellStatus.FAILED_SMOKE).validateAccepted()
    }
}
```

- [ ] **Step 2: Observe RED**

```bash
./gradlew :bluetape4k-images-benchmark:test \
  --tests "io.bluetape4k.images.benchmark.CodecMatrixModelsTest" \
  -Pvips.impl=java25 --console=plain
```

Expected: missing codec-matrix model types fail compilation.

- [ ] **Step 3: Implement models and canonical JSON**

Define:

```kotlin
@Serializable internal enum class CodecMatrixScenario { WEB_PHOTO, PROFILE }
@Serializable internal enum class CodecMatrixDirection { ENCODE, DECODE }
@Serializable internal enum class CodecMatrixCellStatus {
    ELIGIBLE, MEASURED, UNSUPPORTED, SKIPPED,
    @SerialName("N/A") N_A,
    FAILED_SMOKE, ERROR,
}
@Serializable internal enum class CodecMatrixReasonCode {
    NONE, CAPABILITY_UNAVAILABLE, CAPABILITY_UNKNOWN, HOST_BINARY_INCOMPATIBLE,
    POLICY_HOLD, RUNTIME_INITIALIZATION_FAILED, BACKEND_IDENTITY_MISMATCH,
    FIXTURE_INVALID, SMOKE_FAILED, EVIDENCE_INVALID,
}
```

Import `kotlinx.serialization.SerialName`. Use internal `@Serializable` data
classes implementing `java.io.Serializable` with `serialVersionUID` for
dimensions, hashes, inputs, fixture entries, cells, eligibility, and finalized
manifests. Introduce named value/request types for run ID, SHA-256, dimensions,
paths, and multi-string operation inputs instead of positional same-type
parameters. Persist only repository-relative `String` paths; serialized models
must not contain `Path`, `File`, or host-local absolute paths. Enforce unique
expected cells, run IDs matching `[a-z0-9][a-z0-9._-]{7,79}`, no `MEASURED`
in eligibility, no `ELIGIBLE/FAILED_SMOKE/ERROR` in accepted evidence,
complete latency/allocation/input/output metrics and hashes for `MEASURED`, and fixed reason/rerun guidance for
unmeasured cells. `CodecMatrixJson` uses pretty, explicit-default JSON,
SHA-256, and same-directory temporary plus atomic move.

- [ ] **Step 4: Observe GREEN and commit**

Rerun the focused test; expect PASS without native initialization.

```bash
git add benchmark/images-benchmark/build.gradle.kts \
  benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixModels.kt \
  benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixJson.kt \
  benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/CodecMatrixModelsTest.kt
git commit -m "feat: add codec matrix manifest model"
```

### Task 2: Prepare Canonical PNG/WebP Fixtures

**Complexity:** High
**Depends on:** Task 1
**Pattern skills:** `bluetape-kotlin-patterns`, `references/testing.md`
**Files:** create `CodecMatrixFixtures.kt`, `CodecMatrixFixtureMain.kt`, `CodecMatrixFixturesTest.kt`.

- [ ] **Step 1: Write failing fixture tests**

Prove exact generated-source names/dimensions, deterministic hashes, symlink/missing rejection, no-overwrite, derived 1920x1080/512x512, positive size, and JPEG/PNG/WebP magic. Tests copy only the two repository fixtures into a temporary directory shaped like the Gradle `Sync` output; harness code never receives a repository root or another module's test path:

```kotlin
@Test
fun `canonical preparation is deterministic`() {
    val first = prepareFixtures(generatedSources, tempDir.resolve("a"), "fixture-a-0001")
    val second = prepareFixtures(generatedSources, tempDir.resolve("b"), "fixture-b-0001")
    first.fixtures.map { it.inputs.map(CodecMatrixInput::sha256) }
        .shouldBeEqualTo(second.fixtures.map { it.inputs.map(CodecMatrixInput::sha256) })
}
```

- [ ] **Step 2: Observe RED**

```bash
./gradlew :bluetape4k-images-benchmark:test \
  --tests "io.bluetape4k.images.benchmark.CodecMatrixFixturesTest" \
  -Pvips.impl=java25 --console=plain
```

Expected: fixture functions are unresolved.

- [ ] **Step 3: Implement fixed-source preparation**

Consume only `build/generated/codec-matrix-source-fixtures/`, populated by
`syncCodecMatrixSourceFixtures` in Task 6. Keep the checked-in source paths
solely in that Gradle `Sync` declaration. Use:

```kotlin
private const val CAFE_SOURCE = "cafe.jpg"
private const val HOMER_SOURCE = "homer.jpg"
private val JPEG_WRITER = JpegWriter(85, false)
private val PNG_WRITER = PngWriter(4)
private val WEBP_WRITER = WebpWriter(-1, 85, 4, false, false)
```

Resolve below the generated-source directory, reject symlinks, verify original
dimensions, and implement the specified integer cover-scale plus centered crop
without stretching. Write derived JPEG/PNG/WebP under
`build/codec-matrix/<run-id>/fixtures/<scenario>/` and atomically write
`fixtures/manifest.json` with source/derived/input hashes, magic, byte counts,
dimensions, recipe, and options. Existing run content must validate
byte-identically or fail. `CodecMatrixFixtureMain` accepts only
`--source-root`, `--preflight`, `--run-id`, and `--output-root`.

- [ ] **Step 4: Observe GREEN**

```bash
./gradlew :bluetape4k-images-benchmark:test \
  --tests "io.bluetape4k.images.benchmark.CodecMatrixFixturesTest" \
  -Pvips.impl=java25 --console=plain
```

Expected: fixture tests pass. The Gradle preparation task is registered in
Task 6, after its command implementation exists.

- [ ] **Step 5: Commit fixture preparation**

```bash
git add benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixFixtures.kt \
  benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixFixtureMain.kt \
  benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/CodecMatrixFixturesTest.kt
git commit -m "feat: prepare canonical codec fixtures"
```

### Task 3: Add Strict Runtime Selection and Host Preflight

**Complexity:** High
**Depends on:** Task 1
**Pattern skills:** `bluetape-kotlin-patterns`, `references/testing.md`
**Files:** create `CodecMatrixPreflight.kt`, `CodecMatrixPreflightMain.kt`, and `CodecMatrixRuntimeTest.kt`.

- [ ] **Step 1: Write failing selector/preflight/sanitizer tests**

```kotlin
@Test
fun `unknown selector is rejected`() {
    assertFailsWith<IllegalArgumentException> {
        CodecMatrixBackend.parse("jni")
    }
}

@Test
fun `arm64 host and x86 JNI binary becomes N A without initialization`() {
    val result = preflight(host("arm64"), CodecMatrixBackend.JAVA21, "x86_64")
    result.status.shouldBeEqualTo(CodecMatrixCellStatus.N_A)
    runtimeFactoryCalls.get().shouldBeEqualTo(0)
}
```

Also prove malformed selector/run ID, dirty/git probe errors, and sanitization of
absolute home paths, control/Markdown metacharacters, secret-like keys, and text
beyond the fixed bound. Runtime identity is deliberately absent here because
this task is non-native.

- [ ] **Step 2: Observe RED**

```bash
./gradlew :bluetape4k-images-benchmark:test \
  --tests "io.bluetape4k.images.benchmark.CodecMatrixRuntimeTest" \
  -Pvips.impl=java25 --console=plain
```

- [ ] **Step 3: Implement injected non-native preflight**

`CodecMatrixBackend` is the exact `java21|java25` allowlist and carries only
vips-free identifiers plus expected JDK/runtime metadata. Inject host, JDK, JNI
binary, git, disk, and native-access probes. `CodecMatrixPreflightMain` writes
`build/codec-matrix/<run-id>/preflight-<backend>.json`; a known
architecture/JDK/binary
incompatibility produces structured `N_A` without loading a Vips class. Record
only allowlisted facts and fixed reason codes; omit hostname, user, absolute
home/worktree/temp paths, environment values, and raw tool/native messages.

- [ ] **Step 4: Observe GREEN and commit**

Rerun the focused test, then:

```bash
git add benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixPreflight.kt \
  benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixPreflightMain.kt \
  benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/CodecMatrixRuntimeTest.kt
git commit -m "feat: add codec runtime preflight"
```

### Task 4: Build Directional Capability and Smoke Evidence

**Complexity:** High
**Depends on:** Tasks 1-3
**Pattern skills:** `bluetape-kotlin-patterns`, `references/testing.md`
**Files:** create main-source `CodecMatrixCapability.kt`; create benchmark-source `CodecMatrixRuntimeAdapter.kt`, `CodecMatrixCapabilityMain.kt`, `CodecMatrixExperimentalFixtureMain.kt`; create `CodecMatrixCapabilityTest.kt`.

- [ ] **Step 1: Write failing capability/smoke tests**

With hand-written vips-free fakes, prove encode/decode gates are independent;
decode requires a pinned target input; `UNAVAILABLE -> UNSUPPORTED`; `UNKNOWN ->
SKIPPED`; known incompatibility -> `N_A` without native calls; available
malformed/failed smoke -> blocking `FAILED_SMOKE`; unexpected failure ->
`ERROR`; operation handles close on success and exception. Do not import
`VipsRuntime`, `VipsImage`, `VipsImageFormat`, or a backend exception in tests.

```kotlin
@Test
fun `available smoke failure remains blocking`() {
    codecOps.failure = IllegalStateException("native details")
    val cell = evaluator.evaluateEncode(avifAvailable(), fixture, CodecFormat.AVIF)
    cell.status.shouldBeEqualTo(CodecMatrixCellStatus.FAILED_SMOKE)
    cell.reason.shouldNotContain("native details")
}
```

- [ ] **Step 2: Observe RED**

```bash
./gradlew :bluetape4k-images-benchmark:test \
  --tests "io.bluetape4k.images.benchmark.CodecMatrixCapabilityTest" \
  -Pvips.impl=java25 --console=plain
```

- [ ] **Step 3: Implement exact-boundary smoke**

The main-source evaluator calls only the vips-free `CodecMatrixCodecOps`
interface. `CodecMatrixRuntimeAdapter` lives under `src/benchmark`, loads only
the class named by the exact selector, calls `init(concurrency = 4)`, verifies
requested versus reported backend identity, maps format/options, opens one
image, calls `toBytes`, and closes it with `use`; it never falls back.
`CodecMatrixExperimentalFixtureMain` prepares only eligible target inputs,
validates magic/dimensions/size, hashes them, and records producer
backend/JDK/libvips/codec-library versions, command, and run ID. Decode smoke
consumes that exact pinned input and forces JPEG. A decode-only cell requires an
explicit compatible producer manifest. Supplemental public round-trip smoke is
recorded only when both directions are available.

`CodecMatrixCapabilityMain` writes backend-specific
`eligibility-<backend>.json` and stable `sizes-<backend>.json` under
`build/reports/benchmarks/codec-matrix/<run-id>/` by performing each stable
transcode once outside JMH with the same inputs/options.
`CodecMatrixExperimentalFixtureMain` prepares eligible target inputs, then
appends experimental encode/decode size observations to a staged backend size
artifact using the same boundary and options. Both artifacts are immutable
inputs to finalization.
`UNSUPPORTED/SKIPPED/N_A` exit successfully;
`FAILED_SMOKE/ERROR` write sanitized evidence and exit nonzero.

- [ ] **Step 4: Observe GREEN and commit**

```bash
./gradlew :bluetape4k-images-benchmark:test \
  --tests "io.bluetape4k.images.benchmark.CodecMatrixCapabilityTest" \
  -Pvips.impl=java25 --console=plain
git add benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixCapability.kt \
  benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/CodecMatrixRuntimeAdapter.kt \
  benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/CodecMatrixCapabilityMain.kt \
  benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/CodecMatrixExperimentalFixtureMain.kt \
  benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/CodecMatrixCapabilityTest.kt
git commit -m "feat: gate experimental codec benchmarks"
```

### Task 5: Finalize Append-Only Evidence

**Complexity:** High
**Depends on:** Tasks 1 and 4
**Pattern skills:** `bluetape-kotlin-patterns`, `references/testing.md`
**Files:** create `CodecMatrixFinalizeMain.kt` and `CodecMatrixEvidenceFinalizerTest.kt`.

- [ ] **Step 1: Write failing finalizer tests**

Prove rejection of measured cells missing latency/allocation/size artifacts,
pre-benchmark `MEASURED`, `FAILED_SMOKE/ERROR`, missing/duplicate cells, hash
mismatch, local-path/secret leakage, and overwrite. Prove complete atomic
promotion and that a valid `supersedes` run ID records lineage without replacing
either accepted directory.

- [ ] **Step 2: Observe RED**

```bash
./gradlew :bluetape4k-images-benchmark:test \
  --tests "io.bluetape4k.images.benchmark.CodecMatrixEvidenceFinalizerTest" \
  -Pvips.impl=java25 --console=plain
```

- [ ] **Step 3: Implement validation and promotion**

The command accepts `--run-id`, `--staging-root`, and repository-relative
`--accepted-root`. Require staging below `build/codec-matrix/<run-id>` and
output below `benchmark/images-benchmark/docs/raw/<run-id>`. Parse latency and
GC-profiler JMH JSON, join cells by exact benchmark/scenario/backend key, attach
output sizes collected outside timing, validate protocol, hashes, terminal
coverage, leakage, and comparability metadata, write `run-manifest.json`, and
atomically move a complete staged directory. A `supersedes` value links runs;
it never permits replacement. Never delete, rewrite, or replace accepted
evidence.

- [ ] **Step 4: Observe GREEN and commit**

```bash
./gradlew :bluetape4k-images-benchmark:test \
  --tests "io.bluetape4k.images.benchmark.CodecMatrixEvidenceFinalizerTest" \
  -Pvips.impl=java25 --console=plain
git add benchmark/images-benchmark/src/main/kotlin/io/bluetape4k/images/benchmark/CodecMatrixFinalizeMain.kt \
  benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/CodecMatrixEvidenceFinalizerTest.kt
git commit -m "feat: finalize codec benchmark evidence"
```

### Task 6: Wire the Gradle Task Graph

**Complexity:** Medium
**Depends on:** Tasks 1-5
**Pattern skills:** `bluetape-kotlin-patterns`, `references/module-setup.md`, workflow `repository-hazards.md`
**Files:** modify module `build.gradle.kts`; create `CodecMatrixBenchmarkContractTest.kt`.

- [ ] **Step 1: Write failing Gradle source-contract tests**

Assert selector validation, exact task/config/entrypoint names, timing,
includes/excludes, task dependencies, Java launchers/classpaths, run-ID and
manifest propagation, and non-dependencies for compile/generate/jar/build/check/test.

- [ ] **Step 2: Observe RED**

```bash
./gradlew :bluetape4k-images-benchmark:test \
  --tests "io.bluetape4k.images.benchmark.CodecMatrixBenchmarkContractTest" \
  -Pvips.impl=java25 --console=plain
```

- [ ] **Step 3: Implement the exact Gradle graph**

Validate:

```kotlin
val vipsImpl = providers.gradleProperty("vips.impl").orElse("java25").get()
require(vipsImpl == "java21" || vipsImpl == "java25") {
    "vips.impl must be java21 or java25: $vipsImpl"
}
```

Keep `images-vips-api` as `benchmarkImplementation` and move only the selected
backend implementation to `benchmarkRuntimeOnly`. Configure the plugin's
`main` benchmark configuration to exclude the experimental class;
`codecMatrix` includes only `VipsCodecMatrixBenchmark`; `codecMatrixAvif` and
`codecMatrixHeic` include only their exact two methods. All focused configs use
1 warmup, 3 measurements, 1 second, average time, ms, JSON, 1 fork, and 1
thread. Set benchmark working directory to the repository root and add native
access only to benchmark-runtime launches.

Register the exact contract:

```text
syncCodecMatrixSourceFixtures          Sync / two checked-in inputs
codecMatrixPreflight                   JavaExec / CodecMatrixPreflightMain / main runtime
prepareCodecMatrixFixtures             JavaExec / CodecMatrixFixtureMain / main runtime
codecMatrixCapabilityReport            JavaExec / CodecMatrixCapabilityMain / benchmark runtime
prepareExperimentalCodecMatrixFixtures JavaExec / CodecMatrixExperimentalFixtureMain / benchmark runtime
finalizeCodecMatrixEvidence             JavaExec / CodecMatrixFinalizeMain / main runtime
benchmarkCodecMatrixBenchmark           generated stable JMH task
benchmarkCodecMatrixAvifBenchmark       generated AVIF JMH task
benchmarkCodecMatrixHeicBenchmark       generated HEIC JMH task
```

`prepareCodecMatrixFixtures` depends on `codecMatrixPreflight` and
`syncCodecMatrixSourceFixtures`. Both stable execution tasks
(`benchmarkBenchmark`, `benchmarkCodecMatrixBenchmark`) depend only on stable
preparation. `codecMatrixCapabilityReport` depends on preflight and stable
preparation. Each experimental task depends on capability plus
`prepareExperimentalCodecMatrixFixtures`, which itself consumes eligibility
and stable fixtures. All share the validated run ID and exact manifest paths.
Compile/generate/jar/build/check/test never execute fixture preparation, native
capability probes, or experimental preparation. The default benchmark graph
never reaches capability or experimental preparation.

For each focused benchmark execution task, capture its start instant in
`doFirst`. In `doLast`, require exactly one JSON report below the matching
`build/reports/benchmarks/<configuration>/` directory with a modification time
at or after that instant, validate its JMH configuration/row set, and atomically
stage it as `latency-<backend>-<configuration>.json` below the selected run.
Zero or multiple matching reports fail the task instead of guessing.

- [ ] **Step 4: Verify names, invalid input, and dry-run isolation**

```bash
./gradlew :bluetape4k-images-benchmark:tasks --all --console=plain
./gradlew :bluetape4k-images-benchmark:tasks -Pvips.impl=invalid --console=plain
./gradlew :bluetape4k-images-benchmark:prepareCodecMatrixFixtures \
  -Pcodec.matrix.runId=issue-208-local-fixtures \
  -Pvips.impl=java25 --console=plain
./gradlew :bluetape4k-images-benchmark:codecMatrixPreflight --dry-run \
  -Pcodec.matrix.runId=issue-208-dry-run -Pvips.impl=java25 --console=plain
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmark --dry-run \
  -Pcodec.matrix.runId=issue-208-dry-run -Pvips.impl=java25 --console=plain
./gradlew :bluetape4k-images-benchmark:benchmarkCodecMatrixAvifBenchmark --dry-run \
  -Pcodec.matrix.runId=issue-208-dry-run -Pvips.impl=java25 --console=plain
```

Expected: exact tasks and entrypoints exist; invalid input fails at
configuration time; `codecMatrixPreflight --dry-run` remains main-runtime and
non-native; default execution has sync/preflight/stable preparation but no
capability/experimental execution; AVIF has preflight, preparation,
capability, and experimental preparation. Also dry-run `build`, `check`,
`test`, benchmark compile/generate/jar tasks and prove they reach none of the
six execution/preparation tasks.

- [ ] **Step 5: Verify existing-module registration remains unchanged**

```bash
./gradlew projects --console=plain
git diff -- settings.gradle.kts bom gradle/libs.versions.toml \
  .github/workflows/ci.yml .github/workflows/nightly.yml
```

Expected: the benchmark module is still listed; the scoped diff is empty. This
is concrete N/A evidence for the new-module/BOM/catalog/CI/Nightly registration
chain, not permission to edit those surfaces.

- [ ] **Step 6: Commit Gradle wiring**

```bash
git add benchmark/images-benchmark/build.gradle.kts \
  benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/CodecMatrixBenchmarkContractTest.kt
git commit -m "build: wire codec matrix benchmark tasks"
```

### Task 7: Add the Stable PNG/WebP Matrix

**Complexity:** Medium
**Depends on:** Tasks 2, 3, 6
**Pattern skills:** `bluetape-kotlin-patterns`, `references/testing.md`
**Files:** create `VipsCodecMatrixBenchmark.kt`; extend `CodecMatrixBenchmarkContractTest.kt`.

- [ ] **Step 1: Add failing source-contract assertions**

Assert the exact four methods, `@Threads(1)`, `@Fork(1)`, two scenario
parameters, the explicit `quality=85, effort=4, lossless=false,
stripMetadata=true` profile, manifest/preflight loading, strict adapter use,
and absence of `vipsAvailable`/`bh.consume(null)`.

- [ ] **Step 2: Observe RED**

Run the Task 6 focused test; expect missing stable JMH source assertions to fail.

- [ ] **Step 3: Implement fail-fast state and methods**

Use thread-scoped `VipsCodecMatrixState` with `@Param("web-photo",
"profile")`. Trial setup validates matching run ID, preflight, selector,
backend identity, manifest hashes/dimensions/magic/options, loads pinned
JPEG/PNG/WebP, and opens only `CodecMatrixRuntimeAdapter` at concurrency 4.
Each method opens/closes one image and consumes forced output:

```kotlin
@Benchmark
fun encodeWebpFromJpeg(state: VipsCodecMatrixState, bh: Blackhole) {
    state.open(state.jpegBytes).use { image ->
        bh.consume(image.toBytes(VipsImageFormat.WEBP, state.encodeOptions))
    }
}

@Benchmark
fun decodeWebpToJpeg(state: VipsCodecMatrixState, bh: Blackhole) {
    state.open(state.webpBytes).use { image ->
        bh.consume(image.toBytes(VipsImageFormat.JPEG, state.encodeOptions))
    }
}
```

Implement matching PNG methods. Do not catch runtime/transcode failures. Teardown releases references but never calls `shutdown()`.

- [ ] **Step 4: Test, compile both toolchains, and commit**

```bash
./gradlew :bluetape4k-images-benchmark:test \
  --tests "io.bluetape4k.images.benchmark.CodecMatrixBenchmarkContractTest" \
  -Pvips.impl=java25 --console=plain
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkCompile \
  -Pvips.impl=java25 --console=plain
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkCompile \
  -Pvips.impl=java21 --console=plain
git add benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/VipsCodecMatrixBenchmark.kt \
  benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/CodecMatrixBenchmarkContractTest.kt
git commit -m "perf: add stable codec benchmark matrix"
```

### Task 8: Add Opt-In AVIF/HEIC Methods

**Complexity:** Medium
**Depends on:** Tasks 4, 6, 7
**Pattern skills:** `bluetape-kotlin-patterns`, `references/testing.md`
**Files:** create `VipsExperimentalCodecMatrixBenchmark.kt`; extend `CodecMatrixBenchmarkContractTest.kt`.

- [ ] **Step 1: Add failing experimental assertions**

Assert four method names, `@OptIn(VipsIncubatingApi::class)`, distinct direction states, pinned target decode input, JPEG encode input, eligibility checks, and no no-op/fallback.

- [ ] **Step 2: Observe RED**

Run the Task 6 focused test; expect missing experimental source assertions to fail.

- [ ] **Step 3: Implement direction-specific states and methods**

Create AVIF and HEIC states delegating to common internal state. Setup requires
matching `ELIGIBLE` for the invoked direction, validates pinned input
hash/magic/dimensions/producer manifest, and fails with the exact capability
command when evidence is missing. The generated Gradle execution task reads
eligibility after its dependencies complete and passes JMH an exact include
regex containing only eligible directions. `UNSUPPORTED`, `SKIPPED`, and `N_A`
directions remain manifest statuses and emit no JMH rows; zero eligible
directions completes without launching JMH, while `FAILED_SMOKE`/`ERROR` fail
the task. Implement:

```kotlin
@Benchmark fun encodeAvifFromJpeg(state: VipsAvifCodecMatrixState, bh: Blackhole)
@Benchmark fun decodeAvifToJpeg(state: VipsAvifCodecMatrixState, bh: Blackhole)
@Benchmark fun encodeHeicFromJpeg(state: VipsHeicCodecMatrixState, bh: Blackhole)
@Benchmark fun decodeHeicToJpeg(state: VipsHeicCodecMatrixState, bh: Blackhole)
```

Every invocation opens/closes one image and consumes output bytes.

- [ ] **Step 4: Compile, prove isolation, and commit**

```bash
./gradlew :bluetape4k-images-benchmark:test \
  --tests "io.bluetape4k.images.benchmark.CodecMatrixBenchmarkContractTest" \
  -Pvips.impl=java25 --console=plain
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkCompile \
  -Pvips.impl=java25 --console=plain
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmark --dry-run \
  -Pcodec.matrix.runId=issue-208-default-isolation \
  -Pvips.impl=java25 --console=plain
git add benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/VipsExperimentalCodecMatrixBenchmark.kt \
  benchmark/images-benchmark/src/test/kotlin/io/bluetape4k/images/benchmark/CodecMatrixBenchmarkContractTest.kt
git commit -m "perf: add experimental codec benchmark lanes"
```

### Task 9: Run Sequential Native Evidence

**Complexity:** High
**Depends on:** Tasks 1-8 committed and clean
**Pattern skills:** `bluetape-kotlin-patterns`, benchmark hazards
**Files:** generate `benchmark/images-benchmark/docs/raw/<run-id>/` through finalization only.

- [ ] **Step 1: Prepare one clean accepted run**

Use `issue-208-20260713-macos-arm64` only if the branch is clean and the accepted directory is absent:

```bash
git status --short
./gradlew :bluetape4k-images-benchmark:prepareCodecMatrixFixtures \
  -Pcodec.matrix.runId=issue-208-20260713-macos-arm64 \
  -Pvips.impl=java25 --console=plain
```

- [ ] **Step 2: Run Java 21 preflight first**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) \
  ./gradlew :bluetape4k-images-benchmark:codecMatrixPreflight \
  -Pcodec.matrix.runId=issue-208-20260713-macos-arm64 \
  -Pvips.impl=java21 --console=plain
```

Expected on current macOS arm64: structured `N_A` for x86_64 JNI without initialization. Do not invoke Java 21 capability or native JMH unless preflight proves compatibility.

- [ ] **Step 3: Run Java 25 capability and stable latency**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :bluetape4k-images-benchmark:codecMatrixCapabilityReport \
  -Pcodec.matrix.runId=issue-208-20260713-macos-arm64 \
  -Pvips.impl=java25 --console=plain
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :bluetape4k-images-benchmark:benchmarkCodecMatrixBenchmark \
  -Pcodec.matrix.runId=issue-208-20260713-macos-arm64 \
  -Pvips.impl=java25 --console=plain
```

Expected: no blocking stable status and exactly 8 latency rows.

- [ ] **Step 4: Run only eligible experimental tasks**

Inspect capability JSON with `jq`. Run each command only when its matching
cells are `ELIGIBLE`; otherwise retain the terminal status:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :bluetape4k-images-benchmark:benchmarkCodecMatrixAvifBenchmark \
  -Pcodec.matrix.runId=issue-208-20260713-macos-arm64 \
  -Pvips.impl=java25 --console=plain
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :bluetape4k-images-benchmark:benchmarkCodecMatrixHeicBenchmark \
  -Pcodec.matrix.runId=issue-208-20260713-macos-arm64 \
  -Pvips.impl=java25 --console=plain
```

- [ ] **Step 5: Run the focused GC profiler**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkJar \
  -Pcodec.matrix.runId=issue-208-20260713-macos-arm64 \
  -Pvips.impl=java25 --console=plain
jmh_jar="$(find benchmark/images-benchmark/build/benchmarks/benchmark/jars \
  -maxdepth 1 -type f -name '*-JMH.jar' -print -quit)"
test -n "$jmh_jar"
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  java --enable-native-access=ALL-UNNAMED \
  -Dcodec.matrix.backend=java25 \
  -Dcodec.matrix.runId=issue-208-20260713-macos-arm64 \
  -Dcodec.matrix.preflight=benchmark/images-benchmark/build/codec-matrix/issue-208-20260713-macos-arm64/preflight-java25.json \
  -Dcodec.matrix.fixtureManifest=benchmark/images-benchmark/build/codec-matrix/issue-208-20260713-macos-arm64/fixtures/manifest.json \
  -jar "$jmh_jar" '.*VipsCodecMatrixBenchmark.*' \
  -wi 1 -i 3 -w 1s -r 1s -f 1 -t 1 -bm avgt -tu ms -prof gc \
  -rf json \
  -rff benchmark/images-benchmark/build/codec-matrix/issue-208-20260713-macos-arm64/staging/allocation-java25.json
```

Expected: the same 8 stable rows include `gc.alloc.rate.norm`.

- [ ] **Step 6: Run profiler addenda for eligible experimental formats**

For each format whose timed task ran, execute the matching exact regex in a
fresh JVM and write a distinct staging artifact:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  java --enable-native-access=ALL-UNNAMED \
  -Dcodec.matrix.backend=java25 \
  -Dcodec.matrix.runId=issue-208-20260713-macos-arm64 \
  -Dcodec.matrix.preflight=benchmark/images-benchmark/build/codec-matrix/issue-208-20260713-macos-arm64/preflight-java25.json \
  -Dcodec.matrix.fixtureManifest=benchmark/images-benchmark/build/codec-matrix/issue-208-20260713-macos-arm64/fixtures/manifest.json \
  -Dcodec.matrix.eligibility=benchmark/images-benchmark/build/reports/benchmarks/codec-matrix/issue-208-20260713-macos-arm64/eligibility-java25.json \
  -jar "$jmh_jar" \
  '.*VipsExperimentalCodecMatrixBenchmark.*(encodeAvifFromJpeg|decodeAvifToJpeg).*' \
  -wi 1 -i 3 -w 1s -r 1s -f 1 -t 1 -bm avgt -tu ms -prof gc -rf json \
  -rff benchmark/images-benchmark/build/codec-matrix/issue-208-20260713-macos-arm64/staging/allocation-java25-avif.json
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  java --enable-native-access=ALL-UNNAMED \
  -Dcodec.matrix.backend=java25 \
  -Dcodec.matrix.runId=issue-208-20260713-macos-arm64 \
  -Dcodec.matrix.preflight=benchmark/images-benchmark/build/codec-matrix/issue-208-20260713-macos-arm64/preflight-java25.json \
  -Dcodec.matrix.fixtureManifest=benchmark/images-benchmark/build/codec-matrix/issue-208-20260713-macos-arm64/fixtures/manifest.json \
  -Dcodec.matrix.eligibility=benchmark/images-benchmark/build/reports/benchmarks/codec-matrix/issue-208-20260713-macos-arm64/eligibility-java25.json \
  -jar "$jmh_jar" \
  '.*VipsExperimentalCodecMatrixBenchmark.*(encodeHeicFromJpeg|decodeHeicToJpeg).*' \
  -wi 1 -i 3 -w 1s -r 1s -f 1 -t 1 -bm avgt -tu ms -prof gc -rf json \
  -rff benchmark/images-benchmark/build/codec-matrix/issue-208-20260713-macos-arm64/staging/allocation-java25-heic.json
```

- [ ] **Step 7: Finalize and commit only complete evidence**

```bash
./gradlew :bluetape4k-images-benchmark:finalizeCodecMatrixEvidence \
  -Pcodec.matrix.runId=issue-208-20260713-macos-arm64 \
  -Pvips.impl=java25 --console=plain
jq empty benchmark/images-benchmark/docs/raw/issue-208-20260713-macos-arm64/*.json
git add benchmark/images-benchmark/docs/raw/issue-208-20260713-macos-arm64
git commit -m "perf: record codec runtime benchmark evidence"
```

If `FAILED_SMOKE/ERROR` exists, finalization must fail; diagnose before restarting with a new run ID.

### Task 10: Publish Evidence in Docs and Charts

**Complexity:** Medium
**Depends on:** Task 9
**Pattern skills:** `bluetape-writer`, `bluetape-diagram` with `common.md` and `chart.md`
**Files:** create detailed report; modify both README locales; conditionally create canonical SVG/PNG chart.

- [ ] **Step 1: Write the English report**

Include commands, manifest links, fixture hashes/dimensions, runtime versions, common status legend, full cell matrix, latency, `gc.alloc.rate.norm`, input/output bytes, metric direction, native-allocation limitation, comparability keys, reasons, rerun guidance, and supersession. Do not claim PNG and lossy WebP have equivalent visual quality.

- [ ] **Step 2: Add equivalent README summaries**

Keep English and natural Korean summaries aligned in numbers, statuses, commands, report link, and chart link.

- [ ] **Step 3: Apply the chart trigger**

When at least two rows share scenario, host, fixture hash, backend protocol, and metric, create grouped latency/output-size SVG and PNG with English labels, units, legend, truthful scale, and separate scenario panels. Otherwise record exact comparable-row count and chart N/A.

- [ ] **Step 4: Validate assets and docs**

```bash
xmllint --noout docs/images/readme-charts/images-benchmark-codec-runtime-matrix-chart-01.svg
cairosvg docs/images/readme-charts/images-benchmark-codec-runtime-matrix-chart-01.svg \
  -o docs/images/readme-charts/images-benchmark-codec-runtime-matrix-chart-01.png -s 2
identify docs/images/readme-charts/images-benchmark-codec-runtime-matrix-chart-01.png
rg -n "codec-runtime-matrix-2026-07-13|MEASURED|UNSUPPORTED|SKIPPED|N/A" \
  benchmark/images-benchmark/README.md benchmark/images-benchmark/README.ko.md
rg -n "/Users/|/home/|api[_-]?key|secret|token=" \
  benchmark/images-benchmark/docs/raw/issue-208-20260713-macos-arm64 \
  benchmark/images-benchmark/docs/codec-runtime-matrix-2026-07-13.md \
  benchmark/images-benchmark/README.md benchmark/images-benchmark/README.ko.md
git diff --check
```

For a chart, run the chart audit and inspect full-size PNG after the final coordinate change. Leakage scan must return no matches.

- [ ] **Step 5: Commit docs and triggered assets**

```bash
git add benchmark/images-benchmark/docs/codec-runtime-matrix-2026-07-13.md \
  benchmark/images-benchmark/README.md benchmark/images-benchmark/README.ko.md
git add docs/images/readme-charts/images-benchmark-codec-runtime-matrix-chart-01.svg \
  docs/images/readme-charts/images-benchmark-codec-runtime-matrix-chart-01.png
git commit -m "docs: publish codec runtime benchmark matrix"
```

Omit the two asset paths when the chart trigger is N/A.

### Task 11: Final Verification, Review, and Lesson

**Complexity:** High
**Depends on:** Tasks 1-10
**Pattern skills:** `verification-before-completion`, Kotlin final checklist, full-feature verifier/performance/review references
**Files:** create code-review artifact and `docs/lessons/2026-07-13-issue-208-codec-runtime-matrix.md`.

- [ ] **Step 1: Run fresh validation**

```bash
./gradlew :bluetape4k-images-benchmark:test -Pvips.impl=java25 --console=plain
./gradlew :bluetape4k-images-benchmark:build -Pvips.impl=java25 --console=plain
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkCompile -Pvips.impl=java25 --console=plain
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkCompile -Pvips.impl=java21 --console=plain
./gradlew :bluetape4k-images-benchmark:tasks --all --console=plain
./gradlew detekt --console=plain
git diff --check
```

- [ ] **Step 2: Verify hazards and exact spec/plan**

Prove no module/BOM/catalog/API/CI/Nightly/Kover registration change, default task isolation, append-only raw evidence, locale parity, diagram ledger when triggered, and every acceptance row mapped to source/tests/evidence/docs. Return to the owning task on `NEEDS FIX`; reopen approval on `NEEDS REVIEW SCOPE`.

- [ ] **Step 3: Run six code-review lenses plus integration**

Review performance, stability, security, operator/Ops, developer/API, and user/caller against the exact branch diff. Record `docs/review/2026-07-13-issue-208-codec-runtime-matrix-code-review.md`; fix/revalidate all P0/P1; close only at `P0=0, P1=0`.

- [ ] **Step 4: Write and commit the lesson**

Record context, canonical manifest decision, directional smoke result, runtime N/A handling, measurement outcome, verification, review misses, and future no-op/lazy-row guard.

```bash
git add docs/review/2026-07-13-issue-208-codec-runtime-matrix-code-review.md \
  docs/lessons/2026-07-13-issue-208-codec-runtime-matrix.md
git commit -m "docs: record codec benchmark lessons"
```

- [ ] **Step 5: Stop at the PR boundary**

Confirm clean branch and full `origin/develop...HEAD` diff. Report issue #208 milestone/labels/assignee and DoD. Do not create or merge a PR without explicit authorization.

## Documentation, Compatibility, and Hazard Decisions

- Public KDoc: N/A — all new Kotlin types are internal benchmark harness components.
- Production API: N/A — no published API/backend implementation changes.
- README locales: required and synchronized.
- CHANGELOG/release notes: N/A here; report and later PR carry the evidence.
- Chart: conditional; triggered output requires SVG, PNG, audit, and full-size inspection.
- Module/BOM/catalog/settings/CI/Nightly/Kover: N/A — no module/coordinate/workflow/coverage surface changes.
- Coroutines/Testcontainers/network: N/A — no coroutine API, containers, or external fixture fetch.
- Native concurrency: one JMH thread, libvips concurrency 4, sequential fresh processes.
- Rollback: harness, accepted run, report, README entries, and chart roll back as one issue unit; accepted raw directories are never rewritten.

## Plan Completion Gate

- map every spec acceptance item;
- pass placeholder/type/path/task/command consistency scans;
- converge six plan-review perspectives plus integration at `P0=0, P1=0`;
- obtain explicit user approval;
- commit reviewed spec, plan, and review artifacts before implementation.
