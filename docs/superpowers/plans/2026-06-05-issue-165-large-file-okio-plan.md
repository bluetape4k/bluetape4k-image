# Issue #165 Plan — Large-file Okio IO APIs

## Scope

Implement the selected design from
`docs/superpowers/specs/2026-06-05-issue-165-large-file-okio-design.md`.

The key constraint is to use `bluetape4k-okio` actively:

- import and reuse `io.bluetape4k.okio.buffered`
- import and reuse `io.bluetape4k.okio.coroutines.buffered`
- import and reuse `io.bluetape4k.okio.coroutines.asBlocking`
- test with `InputStream.asSource()`, `OutputStream.asSink()`
- test suspended IO through `AsynchronousFileChannel.asSuspendedSource()` and
  `asSuspendedSink()`

## Tasks

| Task | Description | Files |
|---|---|---|
| T1 | Add explicit `bluetape4k-okio` API dependency to vips API module | `images-vips-api/build.gradle.kts` |
| T2 | Add binding-neutral vips Okio write extensions | `images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/VipsImageOkioSupport.kt`, `.../coroutines/SuspendVipsOkioOps.kt` |
| T3 | Add Java 21 vips Okio and suspended source load overloads | `images-vips-java21/src/main/kotlin/io/bluetape4k/images/vips/java21/JVipsImageSupport.kt` |
| T4 | Add Java 25 FFM vips Okio and suspended source load overloads | `images-vips-java25/src/main/kotlin/io/bluetape4k/images/vips/java25/FfmVipsImageSupport.kt` |
| T5 | Add lifecycle and generated-large-fixture tests for Scrimage Okio APIs | `images/src/test/kotlin/io/bluetape4k/images/ImmutableImageSupportTest.kt` |
| T6 | Add vips API fake tests for sink ownership and flush behavior | `images-vips-api/src/test/kotlin/io/bluetape4k/images/vips/VipsImageOkioSupportTest.kt` |
| T7 | Add backend Okio source tests for Java 21 and Java 25 | existing backend test files or new focused tests |
| T8 | Update README locale set with large-file Okio guidance and #166 benchmark link | `README.md`, `README.ko.md`, module READMEs if source names require |
| T9 | Add lesson and PR body DoD evidence | `docs/lessons/...`, PR body |

## Implementation Rules

- Do not add new third-party dependencies.
- Do not change existing `ByteArray`, `Path`, `InputStream`, or `OutputStream`
  behavior.
- Public KDoc and GitHub artifacts are English.
- Buffered sources/sinks are caller-owned and must not be closed by helpers.
- Raw sources/sinks are buffered and closed by helpers.
- Suspended source/sink bridge must not use `runCatching` around suspend code.
- Preserve `CancellationException` propagation.
- vips non-Path source loads remain bounded compressed-byte loads; document them
  as integration APIs, not native streaming decode.

## Verification

Run sequentially:

1. `./gradlew :bluetape4k-images:compileKotlin :bluetape4k-images:compileTestKotlin --console=plain`
2. `./gradlew :bluetape4k-images:test --tests "io.bluetape4k.images.ImmutableImageSupportTest" --console=plain`
3. `./gradlew :bluetape4k-images-vips-api:compileKotlin :bluetape4k-images-vips-api:compileTestKotlin --console=plain`
4. `./gradlew :bluetape4k-images-vips-api:test --console=plain`
5. `./gradlew :bluetape4k-images-vips-java21:compileKotlin :bluetape4k-images-vips-java21:compileTestKotlin --console=plain`
6. `./gradlew :bluetape4k-images-vips-java25:compileKotlin :bluetape4k-images-vips-java25:compileTestKotlin --console=plain`
7. Backend tests only when libvips/runtime availability allows:
   - `./gradlew :bluetape4k-images-vips-java21:test --tests "*Vips*Okio*" --console=plain`
   - `./gradlew :bluetape4k-images-vips-java25:test --tests "*Vips*Okio*" --console=plain`
8. `git diff --check`
9. Step 6-R local 7-tier code review with P0=0 and P1=0.

## Step 3-R Review

| Tier | Findings | Verdict |
|---|---|---|
| Security | No new auth/trust boundary. Keep vips format allowlist and max input limit via existing `InputStream` delegate. | P0=0/P1=0 |
| Ops/SRE | Ownership/close behavior is explicit and testable. JNI/FFM tests remain serial. | P0=0/P1=0 |
| Structural | Okio public types require explicit vips API dependency. Backend overloads stay in backend modules. | P0=0/P1=0 |
| Kotlin/API | Extension functions avoid changing `VipsImage` interface ABI. | P0=0/P1=0 |
| Tests | Fake vips image plus backend smoke tests cover the new API surface. | P0=0/P1=0 |
| Performance | README must avoid claiming Scrimage allocation wins. vips Path remains recommended for local large files. | P0=0/P1=0 |
| Docs/Evidence | README locale set and #166 benchmark link are required before PR. | P0=0/P1=0 |
