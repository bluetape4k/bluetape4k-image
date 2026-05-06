# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Project Overview

`bluetape4k-image` is a Kotlin/JVM image processing library, part of the bluetape4k ecosystem.
It provides two processing backends:

- **scrimage / Java2D** (`images`) — pure JVM, JPEG/PNG/WebP/GIF/TIFF/SVG, coroutine async writers
- **libvips** (`images-vips-*`) — JNI (Java 21) and FFM/Panama (Java 25) high-performance bindings,
  4–10× faster than Java2D with ~1/10 heap usage

**Group**: `io.github.bluetape4k.image`  **Base version**: `0.1.0-SNAPSHOT`

## Repository Layout

| Module                | Description                                                                   |
|-----------------------|-------------------------------------------------------------------------------|
| `images`              | Scrimage-based image processing; coroutine writers; filters; analysis; similarity |
| `images-vips-api`     | Binding-neutral `VipsImage` / `VipsRuntime` interfaces shared by both backends |
| `images-vips-java21`  | JVips JNI backend — Java 21 toolchain; system libvips required on macOS        |
| `images-vips-java25`  | vips-ffm FFM backend — Java 25 toolchain; `--enable-native-access=ALL-UNNAMED` required |
| `images-benchmark`    | JMH benchmarks comparing scrimage and libvips (resize / encode / thumbnail)    |

## Build Commands

```bash
# Full build (all modules)
./gradlew clean build

# Build without tests
./gradlew build -x test

# Build single module
./gradlew :images:build
./gradlew :images-vips-java25:build

# Tests
./gradlew :images:test
./gradlew :images-vips-java21:test
./gradlew :images-vips-java25:test

# Single test class
./gradlew test --tests "io.bluetape4k.images.ImmutableImageSupportTest"

# Static analysis
./gradlew detekt

# Publish SNAPSHOT to Sonatype Central Portal
./gradlew publishAggregationToCentralPortalSnapshots

# Publish RELEASE to Sonatype Central Portal
./gradlew publishAggregationToCentralPortal
```

### libvips Prerequisite (vips modules)

The `images-vips-java21` and `images-vips-java25` modules require a system-installed libvips.

```bash
# macOS (Homebrew)
brew install vips

# Ubuntu/Debian
sudo apt-get install libvips-dev
```

On macOS, `images-vips-java25` tests automatically set `DYLD_LIBRARY_PATH=/opt/homebrew/lib`
when that path exists. Consumer applications running outside Gradle must set this variable themselves:

```bash
export DYLD_LIBRARY_PATH=/opt/homebrew/lib
```

## Kotlin Edit Workflow (MANDATORY)

Before modifying a class: use `ide_find_references` or `get_impact_radius_tool` to identify affected files.

After every `.kt` edit:

1. `ide_diagnostics` — check import errors and `@Deprecated` warnings
2. Import errors → fix with `ide_optimize_imports`
3. `@Deprecated` → apply Quick Fix via `lsp_code_actions` — never leave unresolved
4. Build/compile only after passing the above steps

## Key Design Patterns

**Coroutines-First**: All async work uses Coroutines.

- `suspendImmutableImageOf()` / `suspendLoadImage()` for async image loading
- `ImmutableImage.suspendBytes(writer)` / `ImmutableImage.suspendWrite(writer, path)` for async encoding
- `VipsImage.suspendToBytes()` / `VipsImage.suspendWriteTo()` for vips async I/O
- Blocking vips calls are wrapped with `withContext(Dispatchers.IO)`

**ImmutableImage wrapper**: Scrimage's `ImmutableImage` is the core type in the `images` module.
Use `immutableImageOf(bytes/file/path/stream)` factory functions. Operations return new instances; the
original is never mutated. Use `withGraphics { }` (not the deprecated `useGraphics { }`) for drawing.

**Closeable + use{} for native memory**: All `VipsImage` implementations hold native memory via
`AutoCloseable`. Always wrap vips operations in `.use { }` or ensure `close()` is called:

```kotlin
vipsImageOf(file).use { image ->
    val thumb = image.thumbnail(800)
    thumb.writeTo(outputPath, VipsImageFormat.WEBP)
}
```

**Assert vs Require (CRITICAL — do NOT change exception types)**:

- `assertXxx()` → `AssertionError` (internal invariants, `@Deprecated`)
- `requireXxx()` → `IllegalArgumentException` (parameter validation — always use this)

**No `@Synchronized`**: Use `reentrantLock()` or `AtomicReference` for thread safety.

**`@IncubatingImageApi`**: AVIF/HEIC interfaces in `images` are marked incubating. Implementations
live in the vips modules. Annotate any new incubating API with `@IncubatingImageApi`.

## images-vips-java25 Specifics

- **atomicfu `transformJvm = false`** — The vips-ffm library is compiled for Java 25 (class file
  version 66.0). The atomicfu byte-code transformer runs on the build JVM (Java 21) and cannot
  load Java 25 class files. This override is mandatory and must not be removed.
- **Java 25 toolchain** — both `java { toolchain { languageVersion.set(JavaLanguageVersion.of(25)) } }`
  and `kotlin { jvmToolchain(25) }` are set.
- **`--enable-native-access=ALL-UNNAMED`** — required at JVM startup for the FFM API.
  Set automatically by the test task; consumer apps must add it to their JVM arguments.
- The class is `FfmVipsImage` / `FfmVipsRuntime` (not `JVips*`).

## images-vips-java21 Specifics

- Uses **JVips JNI** bindings (`jvips` artifact).
- Each test class runs in its own JVM fork (`forkEvery = 1`, `maxParallelForks = 1`) to isolate
  the JNI native library.
- On Linux, libvips bundled native libraries are used. On macOS, system libvips is required.
- The class is `JVipsImage` / `JVipsRuntime`.

## After Code Changes

- [ ] Run compile + tests for changed module: `./gradlew :<module>:test`
- [ ] `ide_diagnostics` — zero errors/warnings
- [ ] Update both `README.md` and `README.ko.md` for every changed module
- [ ] KDoc added/updated for all new or modified public APIs

## Before Creating a PR (MANDATORY)

- [ ] All module tests pass: `./gradlew :<module>:test` (report passing count + duration)
- [ ] Code review: run `oh-my-claudecode:code-reviewer` — resolve all HIGH/CRITICAL issues before push
- [ ] PR description includes test results, fix rationale, and verification commands
- [ ] `README.md` and `README.ko.md` updated for every changed module
- [ ] KDoc added/updated for all new or modified public APIs
- [ ] Work was done inside a git worktree (`.worktrees/<branch>/`)
- [ ] vips modules: confirm `brew install vips` (macOS) or `apt-get install libvips-dev` (Linux)
- [ ] `images-vips-java25`: confirm `DYLD_LIBRARY_PATH` or `LD_LIBRARY_PATH` set if running outside Gradle
- [ ] `images-vips-java25`: confirm `--enable-native-access=ALL-UNNAMED` is present in app JVM args

## Git Workflow

- Base branch: `develop`
- Commits: Korean + prefix (`feat: ...`, `fix: ...`, `docs: ...`)
- Worktree: `git worktree add .worktrees/<branch> -b <branch>`
- After merging PR: `./bin/clean-branches` (if present) or `git branch -d <branch>`
