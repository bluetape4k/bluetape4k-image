# CLAUDE.md - bluetape4k-image

Image processing library with two backend families: scrimage/Java2D and libvips
through JNI or FFM Panama.

- **Group**: `io.github.bluetape4k.image`
- **Base version**: `0.1.0-SNAPSHOT`

## Repository Layout

| Module | Description |
|---|---|
| `images` | Scrimage-based image processing, coroutine writers, filters, analysis, similarity, and batch utilities |
| `images-vips-api` | Binding-neutral `VipsImage` and `VipsRuntime` contracts |
| `images-vips-java21` | JVips JNI backend; Java 21 toolchain; system libvips required |
| `images-vips-java25` | vips-ffm FFM backend; Java 25 toolchain; native access required |
| `images-benchmark` | JMH benchmarks for scrimage vs libvips resize/encode/thumbnail paths |
| `bom/` | `bluetape4k-image-bom` consumer BOM |

## Build Commands

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

- Use `immutableImageOf(bytes/file/path/stream)` factories in `images`.
- Operations return new instances; never mutate the source image.
- Use `withGraphics { }`; `useGraphics { }` is deprecated.
- `VipsImage` implementations own native memory. Always use `use { }` or
  explicit `close()`.
- Incubating AVIF/HEIC APIs require `@IncubatingImageApi`.
- Keep `atomicfu transformJvm = false` for `images-vips-java25`.
- Configure Java and Kotlin toolchains for Java 25 in `images-vips-java25`.
- Add `--enable-native-access=ALL-UNNAMED` for FFM API usage.
- `images-vips-java21` JNI tests run isolated with `forkEvery = 1` and
  `maxParallelForks = 1`.

## Documentation Rules

- Keep `README.md` and `README.ko.md` structurally aligned.
- Store shared README images under `docs/assets/` and reference them with the
  same relative path from both locales.
- Keep this file and other agent-facing guidance in English.
