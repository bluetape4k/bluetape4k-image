# AGENTS.md - bluetape4k-image

Image processing library with two backend families: scrimage/Java2D and
libvips through JNI or FFM Panama.

- Group: `io.github.bluetape4k.image`
- Base version: `0.3.0`

## Modules

| Module | Purpose |
|---|---|
| `bluetape4k-images` | Scrimage-based image processing, coroutine writers, filters, analysis, similarity |
| `bluetape4k-images-captcha` | Java2D CAPTCHA image challenge generation |
| `bluetape4k-images-ktor` | Ktor route helpers for CAPTCHA issue and verification |
| `bluetape4k-images-spring-boot` | Spring Boot 4 auto-configuration for local/S3 image storage, CDN, health, and metrics |
| `bluetape4k-images-vips-api` | Binding-neutral `VipsImage` and `VipsRuntime` contracts |
| `bluetape4k-images-vips-java21` | JVips JNI backend; Java 21 toolchain; system libvips required |
| `bluetape4k-images-vips-java25` | vips-ffm FFM backend; Java 25 toolchain; native access required |
| `bluetape4k-images-benchmark` | kotlinx-benchmark results for scrimage vs libvips |
| `examples/basic-processing` | Non-published pure JVM image processing quickstart |
| `examples/ktor-image-api` | Non-published Ktor CAPTCHA and image thumbnail API quickstart |
| `examples/spring-boot-image-api` | Non-published Spring Boot local-storage image API quickstart |
| `bom/` | Consumer BOM for aligned image artifacts |

Root README visual assets live under `docs/assets/` and should be shared by
`README.md` and `README.ko.md` through the same relative path.

## Commands

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

### `bluetape4k-images`

- Use `immutableImageOf(bytes/file/path/stream)` factories.
- Operations return new instances; never mutate the source image.
- Use `withGraphics { }` for `ImmutableImage` drawing. `BufferedImage.useGraphics { }` remains available for mutable `BufferedImage` operations.

### `images-vips-*`

- `VipsImage` implementations own native memory. Always use `use { }` or
  explicit `close()`.
- Incubating AVIF/HEIC APIs require `@IncubatingImageApi`.

### `bluetape4k-images-vips-java25`

- Keep `atomicfu transformJvm = false`; vips-ffm uses Java 25 class files and
  atomicfu transformation can fail on a Java 21 build JVM.
- Configure both Java and Kotlin toolchains for Java 25.
- Add `--enable-native-access=ALL-UNNAMED` for FFM API usage.
- Class names: `FfmVipsImage`, `FfmVipsRuntime`.

### `bluetape4k-images-vips-java21`

- Uses JVips JNI binding.
- JNI tests run isolated: `forkEvery = 1`, `maxParallelForks = 1`.
- Class names: `JVipsImage`, `JVipsRuntime`.

## Cross-Repo Lesson Guards

- Before issue, PR, workflow, release, or module-registration work, query GNO
  for this repo in both `bluetape4k-github` and `bluetape4k-docs`.
- For image module additions or artifact moves, update README locale sets,
  repo-local module lists, CI/Nightly coverage, coverage artifacts, and
  BOM/catalog constraints together.
- Keep Kover XML/Codecov visible without hard gates unless explicitly decided.
  Run libvips/native/JNI and Testcontainers-backed checks sequentially.
