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
once during JMH trial setup:

| Scenario | Source | Derived raster | Purpose |
|---|---|---:|---|
| `web-photo` | `cafe.jpg` (4032x3024) | center-cropped/resized 1920x1080 | common large web content |
| `profile` | `homer.jpg` (1248x702) | center-cropped/resized 512x512 | common profile/avatar content |

Rules:

- The transformation recipe is deterministic and happens outside the measured
  loop.
- The derived raster is encoded once into the JPEG, PNG, and WebP inputs needed
  by the stable matrix.
- Stable runs use identical input bytes for Java 21 and Java 25.
- A missing source fixture is a setup failure. The codec matrix must not fall
  back to a synthetic image because that would silently change the workload.
- Results stay separated by scenario. Values from different dimensions are not
  averaged into one ranking.
- The report records source identity, derived dimensions, encoded input bytes,
  and measured output bytes.

## 7. Benchmark Architecture

### 7.1 Stable matrix

Add a focused `VipsCodecMatrixBenchmark` and codec-specific thread-scoped state
under `src/benchmark`. The state reuses the current binding-neutral runtime
selection and prepares the two derived fixture families.

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

The existing JPEG benchmark remains unchanged as historical evidence and is
referenced beside the new matrix rather than duplicated in the focused task.

### 7.2 Experimental matrix

Add a separate `VipsExperimentalCodecMatrixBenchmark` for AVIF/HEIC. Exclude
this class from the plugin's default `main` configuration and include it only in
explicit experimental configurations.

Before an experimental run:

1. Initialize the selected backend.
2. Read `codecCapabilityReport()`.
3. Require both decode and encode capability to be `AVAILABLE` for automatic
   measurement.
4. Run a short smoke operation with the selected fixture before the timed run.
5. If capability is `UNAVAILABLE`, record `UNSUPPORTED` and the sanitized
   report reason.
6. If capability is `UNKNOWN`, record `SKIPPED` and the backend limitation;
   do not infer support from installed package names.
7. If capability is available but the smoke operation fails, record `SKIPPED`
   with the failed stage and do not run a long benchmark.

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

## 8. Failure Handling

1. **Missing fixture:** fail setup with the attempted path; never synthesize a
   replacement.
2. **Runtime initialization failure:** fail the selected stable task; do not
   emit a no-op row.
3. **Experimental codec unavailable:** record `UNSUPPORTED` with the sanitized
   capability reason.
4. **Experimental capability unknown:** record `SKIPPED`; do not run by
   assumption.
5. **Capability available but smoke fails:** record `SKIPPED` with decode or
   encode stage and keep the long benchmark unexecuted.
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

Store raw JSON files under `benchmark/images-benchmark/docs/raw/` with date,
OS, architecture, JVM, and backend in the filename. The report contains:

- exact commands and metric direction;
- fixture source and derived dimensions;
- runtime and native dependency versions;
- measured, unsupported, skipped, and N/A combinations;
- latency, managed allocation, input bytes, and output bytes;
- interpretation limits and non-comparable rows.

Update both benchmark README locales with a concise table and a link to the
detailed report. If a chart materially improves comparison, generate matching
SVG and PNG assets through `bluetape-diagram`, embed the PNG, and validate both
formats. Do not create a chart that combines non-comparable scenarios or hosts.

## 10. Test and Validation Strategy

### 10.1 Contract tests

- Verify the stable matrix contains the four named transcode boundaries.
- Verify the default benchmark configuration excludes the experimental class.
- Verify the codec matrix contains no unavailable-runtime no-op branch.
- Verify `web-photo` derives 1920x1080 and `profile` derives 512x512.
- Verify missing fixtures fail instead of using a synthetic fallback.
- Verify prepared PNG/WebP/JPEG inputs have valid magic bytes and positive
  sizes.
- Verify capability states map to measured, `UNSUPPORTED`, or `SKIPPED` without
  raw native exception leakage.

### 10.2 Compile and task validation

```text
./gradlew :bluetape4k-images-benchmark:test --console=plain
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkCompile -Pvips.impl=java25 --console=plain
./gradlew :bluetape4k-images-benchmark:benchmarkBenchmarkCompile -Pvips.impl=java21 --console=plain
./gradlew :bluetape4k-images-benchmark:tasks --all --console=plain
```

Dry-run the default and focused tasks to prove experimental isolation before
any native measurement.

### 10.3 Native benchmark validation

- Run Java 25 stable codec matrix.
- Run Java 25 capability/smoke checks, followed only by supported experimental
  tasks.
- Build the focused JMH jar and run the matching rows with `-prof gc`.
- Attempt Java 21 native measurement only on a compatible host. On this macOS
  arm64 host, preserve `N/A` rather than inventing a row.
- Run all native/JNI/FFM commands sequentially.

### 10.4 Documentation validation

- Parse every raw JSON file with `jq`.
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
- Experimental rows run only after capability and smoke gates; every omitted
  combination has an evidence-backed status and reason.
- Latency, managed allocation, input/output size, dimensions, environment, and
  limitations are committed in raw and human-readable evidence.
- Default benchmark execution excludes experimental codecs.
- Targeted tests, benchmark compile, applicable native runs, JSON validation,
  documentation parity, asset validation when triggered, and
  `git diff --check` pass.
- Spec review and later implementation review converge at P0=0 and P1=0.
- The PR remains unmerged until explicit user approval.
