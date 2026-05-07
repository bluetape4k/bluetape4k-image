# AGENTS.md - bluetape4k-image

Image processing library with two backend families: scrimage/Java2D and
libvips through JNI or FFM Panama.

- Group: `io.github.bluetape4k.image`
- Base version: `0.1.0-SNAPSHOT`

## Modules

| Module | Purpose |
|---|---|
| `images` | Scrimage-based image processing, coroutine writers, filters, analysis, similarity |
| `images-vips-api` | Binding-neutral `VipsImage` and `VipsRuntime` contracts |
| `images-vips-java21` | JVips JNI backend; Java 21 toolchain; system libvips required |
| `images-vips-java25` | vips-ffm FFM backend; Java 25 toolchain; native access required |
| `images-benchmark` | JMH benchmarks for scrimage vs libvips |

## Commands

```bash
./gradlew clean build
./gradlew build -x test
./gradlew :images:build
./gradlew :images:test
./gradlew :images-vips-java21:test
./gradlew :images-vips-java25:test
./gradlew test --tests "io.bluetape4k.images.ImmutableImageSupportTest"
./gradlew detekt
./gradlew publishAggregationToCentralPortalSnapshots
./gradlew publishAggregationToCentralPortal
```

## libvips Prerequisites

```bash
brew install vips
sudo apt-get install libvips-dev
```

On macOS, Gradle tests auto-set `DYLD_LIBRARY_PATH` for `/opt/homebrew/lib`
when present. Outside Gradle, set it manually if needed.

## Image Rules

### `images`

- Use `immutableImageOf(bytes/file/path/stream)` factories.
- Operations return new instances; never mutate the source image.
- Use `withGraphics { }`; `useGraphics { }` is deprecated.

### `images-vips-*`

- `VipsImage` implementations own native memory. Always use `use { }` or
  explicit `close()`.
- Incubating AVIF/HEIC APIs require `@IncubatingImageApi`.

### `images-vips-java25`

- Keep `atomicfu transformJvm = false`; vips-ffm uses Java 25 class files and
  atomicfu transformation can fail on a Java 21 build JVM.
- Configure both Java and Kotlin toolchains for Java 25.
- Add `--enable-native-access=ALL-UNNAMED` for FFM API usage.
- Class names: `FfmVipsImage`, `FfmVipsRuntime`.

### `images-vips-java21`

- Uses JVips JNI binding.
- JNI tests run isolated: `forkEvery = 1`, `maxParallelForks = 1`.
- Class names: `JVipsImage`, `JVipsRuntime`.
