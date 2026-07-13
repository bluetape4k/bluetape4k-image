# Issue #208 Codec/Runtime Matrix Benchmark Design

- Date: 2026-07-13
- Issue: [#208](https://github.com/bluetape4k/bluetape4k-image/issues/208)
- Milestone: `0.4.0`
- Work type: Type A - Full Feature
- Scope: `bluetape4k-images-benchmark` harness and benchmark evidence

## 1. Problem

The current benchmark module provides JPEG-oriented encode evidence and geometry
comparisons for the Java 21 JVips/JNI and Java 25 vips-ffm backends. It does not
provide a reproducible codec matrix for the stable PNG/WebP paths or explicit
evidence for the incubating AVIF/HEIC paths.

The missing evidence makes three questions difficult to answer:

1. How do the stable PNG and WebP codec pipelines behave for common web-photo
   and profile-image workloads?
2. Which AVIF/HEIC and runtime combinations were measured, unsupported, or
   skipped because the backend could not prove capability?
3. What latency, managed allocation, and byte-size trade-offs were observed on
   the measured host without presenting a local snapshot as a universal ranking?

## 2. Goals

- Add a default, reproducible PNG/WebP codec matrix to the existing benchmark
  module.
- Measure codec boundaries that force pixel evaluation instead of mistaking
  lazy image opening or header parsing for full decode work.
- Keep AVIF/HEIC measurements opt-in and absent from the default benchmark path.
- Record latency, managed allocation, input/output bytes, fixture dimensions,
  backend, JVM, libvips version, and capability status.
- Keep Java 21 JNI and Java 25 FFM measurements sequential and compare them only
  when the workload semantics and fixture bytes are equivalent.

## 3. Non-goals

- Do not change published image or Vips APIs.
- Do not add a new backend, codec dependency, or benchmark module.
- Do not benchmark browser delivery, network transfer, CDN behavior, visual
  quality, SSIM, PSNR, or perceptual quality.
- Do not claim cross-host or production-wide rankings from the local result.
- Do not force AVIF/HEIC into CI or the default benchmark smoke path.
- Do not replace the historical `vips_encodeJpeg` result.

## 4. Current Evidence

### 4.1 Repository anchors

- `VipsBackendEncodeBenchmark` currently exposes only `vips_encodeJpeg`.
- `VipsBenchmarkState` selects the backend with `-Pvips.impl=java21|java25`,
  owns runtime initialization, and creates binding-neutral `VipsImage` values by
  reflection.
- `VipsRuntime.codecCapabilityReport()` reports PNG/WebP as stable and reports
  AVIF/HEIC with backend-specific `AVAILABLE`, `UNAVAILABLE`, or `UNKNOWN`
  states.
- Java 21 JVips cannot inspect native HEIF operations and cannot encode HEIC.
- Java 25 FFM probes `heifload_buffer` and `heifsave_buffer` through libvips.
- The benchmark plugin is `kotlinx-benchmark` 0.4.17. Its named configurations
  support both `include(pattern)` and `exclude(pattern)`, so experimental JMH
  classes can be excluded from the default `main` configuration.
- Existing allocation reports use a generated JMH jar with `-prof gc` and read
  `gc.alloc.rate.norm` as managed bytes per operation.

### 4.2 Baseline environment

- Worktree base: `feb75001a35fceb53f976a982e7d44a1eb28e204`
- Benchmark compilation:
  `./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkCompile --console=plain`
  passes in the isolated worktree.
- Available local JDKs: Java 21.0.11 and Java 25.0.3.
- Local native stack: libvips 8.18.4, WebP 1.6.0, libavif 1.4.2,
  libheif 1.23.1, and aom 3.14.1.
- Prior repository evidence records that the bundled JVips dylib is x86_64 and
  cannot produce Java 21 JNI measurements on this macOS arm64 host. This is an
  environment limitation, not a synthetic benchmark row.

### 4.3 Upstream basis

- JMH is the JVM microbenchmark execution surface used by kotlinx-benchmark:
  <https://github.com/openjdk/jmh>
- libvips exposes buffer-based WebP and HEIF load/save operations:
  <https://libvips.github.io/pyvips/vimage.html>

## 5. Considered Approaches

### 5.1 Recommended: binding-neutral transcode matrix

Use the public `VipsImage` boundary and force evaluation with a common output
codec:

- JPEG input -> PNG output (`encodePngFromJpeg`)
- JPEG input -> WebP output (`encodeWebpFromJpeg`)
- PNG input -> JPEG output (`decodePngToJpeg`)
- WebP input -> JPEG output (`decodeWebpToJpeg`)

This is intentionally a transcode pipeline rather than a claim of pure codec
CPU time. The input side identifies the decode codec and the output side forces
libvips to evaluate pixels. The same binding-neutral operations run against
both backends.

### 5.2 Rejected: header/open timing as decode timing

Measuring only `vipsImageOf(bytes)` or reading dimensions can measure lazy open
and header parsing without evaluating all pixels. Labeling that result as decode
latency would be misleading.

### 5.3 Rejected: backend-specific raw-pixel hooks

Adding JNI- and FFM-specific benchmark adapters could isolate a lower-level
decode boundary, but it would couple the harness to backend internals and make
cross-backend semantics harder to keep equivalent. Issue #208 does not justify
new production SPI or native adapter APIs.

### 5.4 Rejected: all codecs in the default task

Putting AVIF/HEIC methods in the default task would make the normal benchmark
path depend on optional native codecs. Unsupported hosts could fail or, worse,
emit no-op measurements. Experimental codecs remain a separate opt-in lane.

## 6. Fixture Design

Use two repository-managed source images and derive realistic workload shapes
once in a canonical preparation step before any JMH process starts:

| Scenario | Source | Derived raster | Purpose |
|---|---|---:|---|
| `web-photo` | `cafe.jpg` (4032x3024) | center-cropped/resized 1920x1080 | common large web content |
| `profile` | `homer.jpg` (1248x702) | center-cropped/resized 512x512 | common profile/avatar content |

Rules:

- Resolve the checked-in sources from
  `benchmark/images-benchmark/src/main/resources/bench/cafe.jpg` and
  `images/src/test/resources/images/homer.jpg`. Record their SHA-256 values in
  the result report.
- A cacheable `syncCodecMatrixSourceFixtures` Gradle `Sync` task declares those
  files as inputs and copies them into
  `build/generated/codec-matrix-source-fixtures/`. All harness code consumes
  only this generated directory; it never resolves another module's test tree
  from the process working directory.
- The transformation recipe is deterministic and happens outside the measured
  loop: scale uniformly until both target dimensions are covered, then take the
  centered target rectangle using integer pixel coordinates. Do not stretch the
  source or select a random crop.
- The derived raster is encoded once into the JPEG, PNG, and WebP inputs needed
  by the stable matrix.
- A canonical preparation command writes the derived rasters and JPEG/PNG/WebP
  inputs once under `build/codec-matrix/<run-id>/fixtures/`. It also writes a
  manifest containing logical fixture IDs, source and derived SHA-256 values,
  dimensions, magic-byte result, byte count, transform recipe, and codec
  options. Backend benchmark JVMs only read this manifest and fail if any hash,
  dimension, or magic byte differs.
- The command is the `prepareCodecMatrixFixtures` Gradle task. It accepts a
  validated `-Pcodec.matrix.runId=<run-id>` and refuses to overwrite an existing
  run directory with different content. When the property is absent, local
  smoke runs use a generated non-publishable run ID; accepted evidence always
  uses one explicit run ID across preparation, backend, profiler, and
  finalization commands.
- JMH trial setup verifies the manifest and loads its bytes; it never regenerates
  or re-encodes canonical inputs.
- Stable runs use identical manifest-pinned input bytes for Java 21 and Java 25.
- A missing source fixture is a setup failure. The codec matrix must not fall
  back to a synthetic image because that would silently change the workload.
- The matrix loader accepts only the two fixed repository resources above. It
  does not accept a caller path, follow a symlink, or reuse the synthetic
  fallback in `BenchmarkImageSets`.
- Results stay separated by scenario. Values from different dimensions are not
  averaged into one ranking.
- The report records source identity, derived dimensions, encoded input bytes,
  and measured output bytes.

## 7. Benchmark Architecture

### 7.1 Stable matrix

Add a focused `VipsCodecMatrixBenchmark` and a fail-fast
`VipsCodecMatrixState` under `src/benchmark`. The state reuses the current
binding-neutral runtime selection and reads the prepared fixture manifest. It
does not change the historical, skip-capable `VipsBenchmarkState` or its JPEG
benchmark behavior.

Put deterministic fixture preparation, run-manifest serialization, capability
status DTOs/mapping, and diagnostic sanitization in vips-free `internal`
components under `src/main`. These components must not reference `VipsRuntime`
or `VipsImage`, so `images-vips-api` remains `benchmarkImplementation` and no
production dependency changes. A `CodecMatrixRuntimeAdapter` under
`src/benchmark` maps the selected Vips runtime/image operations to the internal
DTO/factory seam. Unit tests inject vips-free fakes; only JMH annotations,
runtime adapters, and measured calls live under `src/benchmark`. Tests of
annotations/configuration may use focused source contract assertions where the
Gradle source-set graph offers no behavioral seam.

All measured rows use one explicit option profile:

```text
quality=85, effort=4, lossless=false, stripMetadata=true
```

PNG ignores `quality` and maps `effort` to its compression level. WebP is the
common lossy web profile, not a lossless-quality peer to PNG. The report must
therefore present latency and byte-size trade-offs without claiming equivalent
visual quality or compression efficiency. JPEG input and forcing-output bytes
also use quality 85 with metadata stripped.

The stable class exposes four method families for each scenario:

```text
encodePngFromJpeg
encodeWebpFromJpeg
decodePngToJpeg
decodeWebpToJpeg
```

Every invocation creates and closes its own `VipsImage`. Output bytes are
consumed by `Blackhole`. Runtime initialization failure is a benchmark failure;
the class must not use `bh.consume(null)` or publish a no-op timing row.

Add a named `codecMatrix` benchmark configuration. Its expected Gradle task is:

```text
:bluetape4k-images-benchmark:benchmarkCodecMatrixBenchmark
```

Both `benchmarkCodecMatrixBenchmark` and the default `benchmarkBenchmark`
execution task depend on `prepareCodecMatrixFixtures` and receive the selected
run manifest path as a JVM system property. Compile, generate, jar, `build`,
`check`, and `test` tasks do not execute fixture preparation or native
capability probes.

Add a non-native `codecMatrixPreflight` JavaExec task using
`CodecMatrixPreflightMain` on the main runtime classpath. The existing
`prepareCodecMatrixFixtures` JavaExec task uses `CodecMatrixFixtureMain` on the
main runtime classpath and depends on preflight plus
`syncCodecMatrixSourceFixtures`. The stable benchmark depends on preparation
and consumes their shared run ID and fixture manifest. Missing/mismatched
preflight, selector, host compatibility, or manifest evidence fails before
native initialization.

The configuration uses one fork, one benchmark thread, libvips concurrency 4,
one one-second warmup iteration, and three one-second measurement iterations in
`AverageTime` mode with `ms` output. The focused GC-profiler addendum uses the
same thread count, runtime concurrency, warmup, measurement, fork, fixture, and
codec option profile.

The existing JPEG benchmark remains unchanged as historical evidence and is
referenced beside the new matrix rather than duplicated in the focused task.

### 7.2 Experimental matrix

Add a separate `VipsExperimentalCodecMatrixBenchmark` for AVIF/HEIC. Exclude
this class from the plugin's default `main` configuration and include it only in
the explicit `codecMatrixAvif` and `codecMatrixHeic` configurations. Their
expected Gradle tasks are:

```text
:bluetape4k-images-benchmark:benchmarkCodecMatrixAvifBenchmark
:bluetape4k-images-benchmark:benchmarkCodecMatrixHeicBenchmark
```

Add a `codecMatrixCapabilityReport` JavaExec task backed by
`CodecMatrixCapabilityMain` on the benchmark runtime classpath.
It depends on `codecMatrixPreflight` and `prepareCodecMatrixFixtures`, then
initializes only the selected `-Pvips.impl` runtime and writes a structured
JSON snapshot under `build/reports/benchmarks/codec-matrix/`. Each entry records
backend, scenario, format, direction, capability, eligibility status, sanitized
reason, JVM, architecture, and observed libvips version. `UNAVAILABLE` and
`UNKNOWN` are successful observations from this task; runtime initialization,
fixture corruption, or malformed output fails the task.

`vips.impl` is an exact allowlist: only `java21` and `java25` are accepted.
Missing input keeps the existing `java25` default, but any other value fails at
Gradle configuration time. Evidence records both the requested selector and the
identity reported by the initialized runtime; a mismatch fails preflight.

The shared non-native preflight records requested backend, actual JDK
vendor/version, OS/kernel/architecture, CPU model, JNI
binary architecture when applicable, the FFM native-access flag, sanitized
loader-path availability, available disk space, git SHA/dirty state, and a
generated run ID. A known JDK/architecture/native-binary incompatibility becomes
a structured `N/A` observation without attempting runtime initialization.
Unexpected initialization or probe failure is `ERROR` and fails the task.

The experimental class has four exact method families per scenario:

```text
encodeAvifFromJpeg
decodeAvifToJpeg
encodeHeicFromJpeg
decodeHeicToJpeg
```

The AVIF and HEIC configurations include only their matching two methods and
reuse the stable option/timing profile. A canonical preparation step uses an
eligible backend to encode a manifest-pinned JPEG raster to the target format,
validates target magic bytes/dimensions/positive size, and stores that exact
AVIF/HEIC input plus its SHA-256 in the fixture manifest. Decode rows consume
the pinned target-format bytes; encode rows consume the pinned JPEG bytes.
The target-format manifest records the producer backend, JDK, libvips and codec
library versions, preparation command, and producer run ID. Experimental rows
from different hosts, producer manifests, or input hashes are never compared.

Before an experimental run:

1. Initialize the selected backend.
2. Read `codecCapabilityReport()`.
3. Evaluate the capability required by each direction independently. An encode
   row requires encode `AVAILABLE`; a decode row requires decode `AVAILABLE` and
   a pinned target-format input.
4. Run a harness-local directional smoke with the exact timed boundary and
   option profile: encode uses pinned JPEG -> target format and validates target
   magic/dimensions; decode uses pinned target format -> JPEG and validates JPEG
   magic/dimensions. The public round-trip `smokeTestCodec` may be recorded as
   supplemental evidence only when both directions are available; it is not a
   gate for a single-direction row.
5. If capability is `UNAVAILABLE`, record `UNSUPPORTED` and the sanitized
   report reason.
6. If capability is `UNKNOWN`, record `SKIPPED` and the backend limitation;
   do not infer support from installed package names.
7. If capability is available but fixture preparation or smoke fails, record
   `FAILED_SMOKE` with the failed stage, fail the experimental task, and block
   accepted evidence. Do not downgrade an observed failure to `SKIPPED`.

The experimental benchmark tasks do not fabricate JMH skip rows. Each task has
a Gradle dependency on `codecMatrixCapabilityReport` and consumes its
format/direction eligibility plus the fixture manifest. Direct invocation with
an ineligible or missing preflight fails immediately and prints the exact
capability command to run; it does not emit a partial or no-op JMH row. The
capability output remains an ephemeral eligibility manifest under
`build/reports` while measurement is running. After eligible rows finish, a
finalization step combines eligibility with numeric latency/allocation/size
artifacts or a terminal unmeasured status, validates their hashes, and atomically
promotes the finalized snapshot to the tracked raw-evidence directory. No
pre-benchmark file may claim `MEASURED` or accepted final status.

The `codecMatrixCapabilityReport` task depends on
`prepareCodecMatrixFixtures`. The two experimental benchmark tasks depend on
that capability task and `prepareExperimentalCodecMatrixFixtures`, a JavaExec
task using `CodecMatrixExperimentalFixtureMain` on the benchmark runtime
classpath. That task generates manifest-pinned target inputs only for eligible
encode formats; a decode-only row must consume an explicitly supplied compatible
producer manifest or fail before timing. All tasks receive the same explicit
run ID. The non-native `finalizeCodecMatrixEvidence` JavaExec task uses
`CodecMatrixFinalizeMain` on the main runtime classpath and is the only task
allowed to promote a staged run into
`benchmark/images-benchmark/docs/raw/<run-id>/`; it verifies the run manifest,
cell coverage, artifact hashes, terminal statuses, and absence of blocking
states before an atomic directory move. Reinvoking preparation or finalization
for an existing accepted run never overwrites tracked evidence.

The Gradle task contract is exact:

| Task | Type / entrypoint | Declared inputs | Output / dependency |
|---|---|---|---|
| `syncCodecMatrixSourceFixtures` | `Sync` | the two checked-in source fixtures | `build/generated/codec-matrix-source-fixtures/` |
| `codecMatrixPreflight` | `JavaExec` / `CodecMatrixPreflightMain`, main runtime | selector, explicit run ID, git/host/JDK facts | `build/codec-matrix/<run-id>/preflight.json` |
| `prepareCodecMatrixFixtures` | `JavaExec` / `CodecMatrixFixtureMain`, main runtime | synced sources, preflight, transform/options | stable fixtures plus `fixtures/manifest.json` |
| `codecMatrixCapabilityReport` | `JavaExec` / `CodecMatrixCapabilityMain`, benchmark runtime | preflight, stable manifest, selected backend | ephemeral `eligibility.json` |
| `prepareExperimentalCodecMatrixFixtures` | `JavaExec` / `CodecMatrixExperimentalFixtureMain`, benchmark runtime | eligibility, stable manifest, producer manifest when supplied | AVIF/HEIC inputs plus updated fixture manifest |
| focused benchmark tasks | generated JMH tasks | preflight, exact fixture/eligibility manifests, run ID | staged latency JSON; direct calls enforce dependencies |
| `finalizeCodecMatrixEvidence` | `JavaExec` / `CodecMatrixFinalizeMain`, main runtime | eligibility, staged latency/GC/size/status artifacts and hashes | atomic tracked `docs/raw/<run-id>/` |

Only the stable `benchmarkBenchmark` and
`benchmarkCodecMatrixBenchmark` tasks join the non-experimental preparation
path. `build`, `check`, `test`, compile/generate/jar tasks, and the default
benchmark graph never depend on capability or experimental-fixture tasks; AVIF
and HEIC work starts only from their explicit focused task names.

Status semantics are per matrix cell and scoped to backend, JVM, architecture,
host environment, libvips build, direction, scenario, and input hash:

- `MEASURED`: numeric latency/allocation/size evidence exists.
- `UNSUPPORTED`: the required operation is explicitly `UNAVAILABLE`.
- `SKIPPED`: capability is `UNKNOWN` or an explicit policy hold prevented a run.
- `N/A`: preflight proves the requested runtime cannot execute on this host.
- `FAILED_SMOKE`: capability said available but preparation/smoke failed; blocks acceptance.
- `ERROR`: unexpected setup/runtime/evidence failure; blocks acceptance.

Java 21 JVips therefore does not receive fabricated AVIF/HEIC rows on this host.
Java 25 FFM rows are measured only after capability and smoke gates pass.

### 7.3 Measurement and raw evidence

Use `AverageTime` in `ms/op`; lower is better. Produce two raw evidence files
per measured backend:

- the normal kotlinx-benchmark/JMH JSON for latency;
- a focused JMH `-prof gc` JSON for `gc.alloc.rate.norm` (`B/op`).

Managed allocation does not represent native libvips memory. The report states
this limitation next to the allocation table.

Output size is collected once outside the timed loop with the exact same
fixture, codec, and options used by the benchmark. The report includes both
input and output byte counts so a decode-to-JPEG row is not confused with an
encode-from-JPEG row.

Each backend/configuration/profiler command runs in a fresh JVM. Timed,
preparation, smoke, and output-size paths close every `VipsImage` on success and
failure; no lane calls irreversible `VipsRuntime.shutdown()` between trials. A
failed or interrupted attempt retains its sanitized log, discards partial
measurements, and reruns the complete affected lane only in a new process after
the failure is diagnosed. The failed attempt retains `ERROR` or `FAILED_SMOKE`,
diagnosis, mitigation, its run ID, and a link from the replacement attempt; an
unexplained retry is never accepted.

## 8. Failure Handling

1. **Missing fixture:** fail setup with the attempted path; never synthesize a
   replacement.
2. **Runtime initialization failure:** fail the selected stable task; do not
   emit a no-op row.
3. **Experimental codec unavailable:** record `UNSUPPORTED` with the sanitized
   capability reason.
4. **Experimental capability unknown:** record `SKIPPED`; do not run by
   assumption.
5. **Capability available but smoke fails:** record blocking `FAILED_SMOKE`
   with decode or encode stage and keep the long benchmark unexecuted.
6. **Java 21 host incompatibility:** record the JVM, architecture, and native
   binding limitation as `N/A`; compilation alone is not a measurement.
7. **Backend runs overlap:** invalidate the evidence and rerun Java 21 and Java
   25 sequentially.
8. **Retry-only success:** investigate native lifecycle or timing behavior
   before accepting the rerun.

## 9. Documentation and Result Artifacts

Add an English detailed report under:

```text
benchmark/images-benchmark/docs/codec-runtime-matrix-2026-07-13.md
```

Store accepted raw evidence under
`benchmark/images-benchmark/docs/raw/<run-id>/`. The directory is append-only:
an interrupted/retried run gets a new ID, accepted evidence is never
overwritten, and replacement runs declare `supersedes` links. A run manifest
records git SHA/dirty state, start/end time, exact command and exit status,
Gradle/JMH settings and JVM arguments, sanitized OS/kernel/architecture and CPU
identity, JDK and native library versions/probes, fixture/input hashes, actual
backend identity, artifact SHA-256 values, attempt status, and any superseded
run. It omits hostname, user name, absolute home/worktree/temp paths,
environment values, and secrets. Capability, latency, allocation, and size
artifacts link back to this manifest. The report contains:

- exact commands and metric direction;
- fixture source and derived dimensions;
- runtime and native dependency versions;
- measured, unsupported, skipped, and N/A combinations;
- latency, managed allocation, input bytes, and output bytes;
- interpretation limits and non-comparable rows.

The report and both README locales use the same status legend. Every matrix cell
contains either measured values or one scoped status, sanitized reason, and
rerun guidance. Sanitization maps failures to fixed reason codes/allowlisted
messages, strips control/Markdown metacharacters and absolute paths, and bounds
message length. A pre-commit scan rejects raw exception text, local path
prefixes, or secret-like values in raw JSON, reports, README files, and command
examples.

Update both benchmark README locales with a concise table and a link to the
detailed report. When at least two comparable rows are measured for the same
scenario and host, generate matching latency/output-size SVG and PNG assets
through `bluetape-diagram`, embed the PNG, and validate both formats. If fewer
than two comparable rows exist, keep a table and record the evidence-backed
chart N/A. Do not create a chart that combines non-comparable scenarios or
hosts.

## 10. Test and Validation Strategy

### 10.1 Contract tests

- Verify the stable matrix contains the four named transcode boundaries.
- Verify the default benchmark configuration excludes the experimental class.
- Verify the focused configuration names and one-warmup/three-measurement
  timing profile.
- Verify the codec matrix contains no unavailable-runtime no-op branch.
- Verify `web-photo` derives 1920x1080 and `profile` derives 512x512.
- Verify the derived rasters use deterministic cover-and-center-crop semantics.
- Verify missing fixtures fail instead of using a synthetic fallback.
- Verify prepared PNG/WebP/JPEG inputs have valid magic bytes and positive
  sizes.
- Verify eligibility and finalized cell states map to `MEASURED`,
  `UNSUPPORTED`, `SKIPPED`, or `N/A` without raw native exception leakage.
- Verify `FAILED_SMOKE` and `ERROR` block acceptance and cannot become
  `SKIPPED`.
- Verify exact `vips.impl` validation, requested/actual backend equality, known
  host `N/A`, and unexpected initialization `ERROR` with injected fakes.
- Verify canonical fixture and run manifests, hashes, append-only run IDs,
  supersession links, and atomic promotion into tracked raw evidence.
- Verify stable fixture preparation and benchmark tasks depend on the shared
  preflight/run ID and fail before native initialization when it is missing,
  mismatched, or incompatible.
- Verify eligibility manifests cannot contain `MEASURED`, and finalization
  requires either complete numeric artifacts or one terminal unmeasured status
  for every cell.
- Verify experimental row direction gates, exact-boundary smoke bytes,
  direct-task fail-fast behavior, and close tracking on success and exception
  paths.
- Verify `build`, `check`, `test`, the default `benchmark` task, and CI task
  graphs do not depend on capability or AVIF/HEIC tasks; experimental work runs
  only through explicit focused task names.
- Verify the capability task produces the required JSON fields, treats
  unsupported/unknown as observations, and fails malformed evidence.

### 10.2 Compile and task validation

```text
./gradlew :bluetape4k-images-benchmark:test --console=plain
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkCompile -Pvips.impl=java25 --console=plain
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkCompile -Pvips.impl=java21 --console=plain
./gradlew :bluetape4k-images-benchmark:tasks --all --console=plain
./gradlew :bluetape4k-images-benchmark:codecMatrixPreflight -Pvips.impl=java25 --console=plain
./gradlew :bluetape4k-images-benchmark:prepareCodecMatrixFixtures -Pvips.impl=java25 --console=plain
./gradlew :bluetape4k-images-benchmark:codecMatrixCapabilityReport -Pvips.impl=java25 --console=plain
./gradlew :bluetape4k-images-benchmark:prepareExperimentalCodecMatrixFixtures -Pvips.impl=java25 --console=plain
```

Dry-run the default and focused tasks to prove experimental isolation before
any native measurement.

### 10.3 Native benchmark validation

- Run the shared non-native preflight and fixture preparation, then the Java 25
  stable codec matrix with the same run ID.
- Run Java 25 capability/smoke checks, followed only by supported experimental
  tasks.
- Build the focused JMH jar and run the matching rows with `-prof gc`.
- Attempt Java 21 native measurement only on a compatible host. On this macOS
  arm64 host, preserve `N/A` rather than inventing a row.
- Run all native/JNI/FFM commands sequentially.
- Compare Java 21 and Java 25 only when commit, dirty state, OS/kernel/CPU/arch,
  libvips and codec-library versions, fixture and producer-manifest hashes,
  option profile, benchmark threads, runtime concurrency, and JMH protocol
  match. Otherwise publish separate non-comparable rows.

### 10.4 Documentation validation

- Parse every raw JSON file with `jq`.
- Cross-check every manifest/artifact hash and reject leakage patterns.
- Verify README English/Korean parity and report links.
- Validate any SVG with `xmllint` and PNG with `identify`.
- Run `git diff --check`.

## 11. Compatibility and Repository Hazards

- No production API or artifact coordinate changes are expected.
- No module registration or BOM change is expected because the harness remains
  in `bluetape4k-images-benchmark`.
- Keep `atomicfu transformJvm = false` in the Java 25 backend unchanged.
- Keep Java and Kotlin toolchains selected by `-Pvips.impl`.
- Keep `--enable-native-access=ALL-UNNAMED` for the FFM benchmark fork.
- The benchmark source set remains excluded from production coverage.
- README locale parity, raw evidence paths, chart assets, and benchmark task
  names are required hazard checks.
- No API/BOM/version coordinate changes are expected. The issue #208 operator
  captures the native run; the implementation reviewer validates manifests and
  report interpretation; PR/merge approval remains with `debop`. Report,
  README/chart, and raw evidence roll back together as one change unit.

## 12. Acceptance Criteria Traceability

| Issue criterion | Design proof |
|---|---|
| Distinguish measured, skipped, and unsupported combinations | Experimental capability/smoke gate and explicit status table |
| Include latency, allocation, output bytes, dimensions, backend, JVM, and libvips | Raw latency/GC JSON plus result metadata and byte-size capture |
| Keep experimental codecs from making default paths flaky | Default `main` exclusion plus opt-in experimental configurations |
| Link README to the codec matrix report | English/Korean README updates and detailed report path |
| Compare Java 21 and Java 25 only when semantics match | Binding-neutral transcode boundaries, identical fixture bytes, sequential runs |

## 13. Definition of Done

- The approved stable and experimental task boundaries compile under the
  selected Java 21 and Java 25 toolchains.
- PNG/WebP stable rows run for both approved fixture scenarios on Java 25.
- Experimental rows run only after direction-specific capability and smoke
  gates; every omitted combination has an evidence-backed status and reason,
  and no accepted run contains `FAILED_SMOKE` or `ERROR`.
- Latency, managed allocation, input/output size, dimensions, environment, and
  limitations are committed in raw and human-readable evidence.
- Default benchmark execution excludes experimental codecs.
- Targeted tests, benchmark compile, applicable native runs, JSON validation,
  documentation parity, asset validation when triggered, and
  `git diff --check` pass.
- Spec review and later implementation review converge at P0=0 and P1=0.
- The PR remains unmerged until explicit user approval.
