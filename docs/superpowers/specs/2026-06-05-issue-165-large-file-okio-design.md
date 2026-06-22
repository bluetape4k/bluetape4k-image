# Issue #165 Design — Large-file Okio IO APIs

## Context

Issue #165 asks for memory-conscious large-file image IO APIs that use Okio. The
starting evidence is PR #167 / issue #166, which added a `kotlinx-benchmark`
large streaming benchmark. The benchmark shows that Scrimage `Path`,
`InputStream`, and Okio `Source/Sink` rows are latency- and managed-allocation
similar because Scrimage decode/encode still dominates decoded image heap.
libvips Java 25 FFM `Path` is the strongest measured row for large files.

The user clarified that this work should use `bluetape4k-okio` actively, not
just raw Okio types. Current code already has Scrimage `ImmutableImage`
overloads for `BufferedSource`, `Source`, `BufferedSuspendedSource`,
`SuspendedSource`, `BufferedSink`, `Sink`, `BufferedSuspendedSink`, and
`SuspendedSink` in `images/src/main/kotlin/io/bluetape4k/images/ImmutableImageSupport.kt`.

## Current Evidence

- `images/build.gradle.kts` already exposes `libs.bluetape4k.okio`.
- Scrimage load/write helpers already import:
  - `io.bluetape4k.okio.buffered`
  - `io.bluetape4k.okio.coroutines.buffered`
  - `io.bluetape4k.okio.coroutines.asBlocking`
  - `SuspendedSource`, `SuspendedSink`, `BufferedSuspendedSource`, `BufferedSuspendedSink`
- `images/src/test/.../ImmutableImageSupportTest.kt` covers basic Okio load and
  suspended source/sink cases, but lifecycle/close semantics and large generated
  fixture behavior are thin.
- `images-vips-api` currently exposes `VipsImage.writeTo(Path)` and
  `VipsImage.writeTo(OutputStream)`, plus coroutine wrappers.
- `images-vips-java21` and `images-vips-java25` expose ByteArray, File, Path,
  InputStream load functions. Stream loads are bounded by `VipsLimits.MAX_INPUT_BYTES`.
- `bluetape4k-okio` provides the preferred ecosystem bridge surface:
  - `InputStream.asSource()`, `OutputStream.asSink()`
  - `Source.buffered()`, `Sink.buffered()`
  - `AsynchronousFileChannel.asSuspendedSource()`, `asSuspendedSink()`
  - `SuspendedSource.buffered()`, `SuspendedSink.buffered()`
  - `SuspendedSource.asBlocking()`, `SuspendedSink.asBlocking()`
- Okio upstream documentation uses `use {}` around owning sources/sinks and
  recommends operating through Okio once a source/sink wrapper owns the stream.

## Requirements

1. Provide a clear Okio-first API path for large-file callers without forcing
   `ByteArray` staging at the public API boundary.
2. Use `bluetape4k-okio` bridge helpers and suspended source/sink APIs directly.
3. Keep Scrimage documentation honest: Okio helps lifecycle/integration, not
   Scrimage decoded-image heap allocation.
4. Add binding-neutral vips write extensions for Okio sinks.
5. Add backend-specific vips load overloads for Okio sources and suspended
   sources while preserving existing size/format/pixel safety checks.
6. Keep resource ownership explicit:
   - `BufferedSource` / `BufferedSink` / buffered suspended variants are
     caller-owned and not closed by the helper.
   - raw `Source` / `Sink` / suspended variants are buffered and closed by the helper.
7. README and KDoc must state when vips `Path` is preferred for measured large
   local-file performance.

## Design Options

### Option A — Only document existing Scrimage Okio APIs

This is too small for #165. It would not give vips users a large-file Okio
surface and would leave the benchmark evidence disconnected from API choices.

### Option B — Add only raw Okio overloads

This uses `okio.Source` and `okio.Sink` but does not lean into
`bluetape4k-okio`. It also leaves coroutine file-channel paths up to each
caller and weakens consistency with the rest of bluetape4k.

### Option C — Use bluetape4k-okio as the public IO bridge

Add vips Okio/suspended overloads using `bluetape4k-okio` adapters and tighten
Scrimage tests/docs around the existing overloads. This keeps dependency and
ownership semantics consistent across `images` and `images-vips-*`.

Selected: Option C.

## API Shape

### Scrimage `images`

Keep existing public functions and add focused tests/docs:

- `immutableImageOf(source: BufferedSource)`
- `immutableImageOf(source: Source)`
- `suspendLoadImage(source: BufferedSuspendedSource)`
- `suspendLoadImage(source: SuspendedSource)`
- `ImmutableImage.suspendWrite(writer, sink: BufferedSink)`
- `ImmutableImage.suspendWrite(writer, sink: Sink)`
- `ImmutableImage.suspendWrite(writer, sink: BufferedSuspendedSink)`
- `ImmutableImage.suspendWrite(writer, sink: SuspendedSink)`

### vips API module

Add binding-neutral extension functions in `images-vips-api`:

- `VipsImage.writeTo(sink: BufferedSink, format, options)`
- `VipsImage.writeTo(sink: Sink, format, options)`
- `VipsImage.suspendWriteTo(sink: BufferedSink, format, options)`
- `VipsImage.suspendWriteTo(sink: Sink, format, options)`
- `VipsImage.suspendWriteTo(sink: BufferedSuspendedSink, format, options)`
- `VipsImage.suspendWriteTo(sink: SuspendedSink, format, options)`

These extensions encode through existing `VipsImage.writeTo(OutputStream)` and
flush sinks. This is not a native streaming encoder guarantee because current
vips implementations still call `toBytes()` internally.

### vips backend modules

Add backend-specific load overloads:

- Java 21:
  - `vipsImageOf(source: BufferedSource)`
  - `vipsImageOf(source: Source)`
  - `suspendVipsImageOf(source: BufferedSource)`
  - `suspendVipsImageOf(source: Source)`
  - `suspendVipsImageOf(source: BufferedSuspendedSource)`
  - `suspendVipsImageOf(source: SuspendedSource)`
- Java 25:
  - same shape under `ffmVipsImageOf` / `suspendFfmVipsImageOf`

All variants delegate to the existing bounded `InputStream` decode path or to
the existing blocking bridge for suspended sources, so existing `MAX_INPUT_BYTES`
and format allowlist behavior remains in force.

## Test Strategy

- `images`: extend `ImmutableImageSupportTest` with generated larger fixtures
  and explicit caller-owned vs helper-owned close/flush behavior.
- `images-vips-api`: add a fake `VipsImage` test for Okio sink extensions,
  including flush and close ownership.
- `images-vips-java21/java25`: add backend tests for Okio source overloads,
  gated by existing runtime availability base classes. Keep JNI/FFM tests serial
  through existing Gradle configuration.
- Add compile checks for all touched modules and run targeted tests. Java 25
  checks must include `--enable-native-access` through existing Gradle config.

## Risks

1. Public wording may imply true bounded-memory transform for Scrimage. Mitigate
   with README/KDoc language tied to #166 allocation evidence.
2. vips `Source` load currently buffers compressed input bytes because the
   backend APIs decode from bytes for non-path sources. Mitigate by recommending
   `Path` for local large files and documenting stream boundaries as integration
   APIs.
3. Suspended source/sink bridge can hide cancellation if implemented with
   `runCatching`. Mitigate by direct `try/finally` cleanup and no broad
   cancellation swallowing.
4. Adding Okio types to `images-vips-api` affects dependency surface. Mitigate
   by explicit `api(libs.bluetape4k.okio)` because the public API contains Okio
   types.

## Acceptance Mapping

| Issue criterion | Design response |
|---|---|
| Clear large-file path without ByteArray boundary | Scrimage/vips Okio source/sink APIs plus Path recommendation |
| Okio APIs isolated | `images` and `images-vips-api` only expose public Okio; backend modules add concrete load overloads |
| Resource closing/failure/cancellation | Ownership split and focused tests |
| README/README.ko limitations | Large-file section with #166 evidence |
| Benchmark evidence link | README and PR body link `benchmark/images-benchmark/docs/large-streaming-2026-06-05.md` |
