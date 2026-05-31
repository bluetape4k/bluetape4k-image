# CLAUDE.md - bluetape4k-image

Image processing library with two backend families: scrimage/Java2D and libvips
through JNI or FFM Panama.

- **Group**: `io.github.bluetape4k.image`
- **Base version**: `0.2.0`

## Repository Layout

| Module | Description |
|---|---|
| `bluetape4k-images` | Scrimage-based image processing, coroutine writers, filters, analysis, similarity, and batch utilities |
| `bluetape4k-images-spring-boot` | Spring Boot 4 auto-configuration: S3/local storage, CDN signing, health, metrics |
| `bluetape4k-images-vips-api` | Binding-neutral `VipsImage` and `VipsRuntime` contracts |
| `bluetape4k-images-vips-java21` | JVips JNI backend; Java 21 toolchain; system libvips required |
| `bluetape4k-images-vips-java25` | vips-ffm FFM backend; Java 25 toolchain; native access required |
| `bluetape4k-images-benchmark` | JMH benchmarks for scrimage vs libvips resize/encode/thumbnail paths |
| `bom/` | `bluetape4k-image-bom` consumer BOM |

## Build Commands

```bash
./gradlew clean build
./gradlew build -x test
./gradlew :bluetape4k-images:build
./gradlew :bluetape4k-images:test
./gradlew :bluetape4k-images-vips-java21:test
./gradlew :bluetape4k-images-vips-java25:test
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

- Use `immutableImageOf(bytes/file/path/stream)` factories in `bluetape4k-images`.
- Operations return new instances; never mutate the source image.
- Use `withGraphics { }` for `ImmutableImage` drawing. `BufferedImage.useGraphics { }` remains available for mutable `BufferedImage` operations.
- `VipsImage` implementations own native memory. Always use `use { }` or
  explicit `close()`.
- Incubating AVIF/HEIC APIs require `@IncubatingImageApi`.
- Keep `atomicfu transformJvm = false` for `bluetape4k-images-vips-java25`.
- Configure Java and Kotlin toolchains for Java 25 in `bluetape4k-images-vips-java25`.
- Add `--enable-native-access=ALL-UNNAMED` for FFM API usage.
- `bluetape4k-images-vips-java21` JNI tests run isolated with `forkEvery = 1` and
  `maxParallelForks = 1`.

## Documentation Rules

- Keep `README.md` and `README.ko.md` structurally aligned.
- Store shared README images under `docs/assets/` and reference them with the
  same relative path from both locales.
- Keep this file and other agent-facing guidance in English.
