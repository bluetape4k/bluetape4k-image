# AGENTS.md - bluetape4k-image

This repository inherits the workspace guidance from `../AGENTS.md`.
Read and follow the workspace root guide first. This file only adds
repo-specific layout, commands, domain rules, and local exceptions.


Image processing library with two backend families: scrimage/Java2D and
libvips through JNI or FFM Panama.

- Group: `io.github.bluetape4k.image`
- Base version: `0.3.0`

## Modules

| Module | Purpose |
|---|---|
| `bluetape4k-images` | Scrimage-based image processing, coroutine writers, filters, analysis, similarity |
| `bluetape4k-images-barcode-api` | Provider-neutral barcode and QR extraction contracts for `ImmutableImage` |
| `bluetape4k-images-captcha` | Java2D CAPTCHA image challenge generation |
| `bluetape4k-images-ocr` | Tess4J/Tesseract OCR text extraction for `ImmutableImage` |
| `bluetape4k-images-ktor` | Ktor route helpers for CAPTCHA issue and verification |
| `bluetape4k-images-spring-boot` | Spring Boot 4 auto-configuration for local/S3 image storage, CDN, health, and metrics |
| `bluetape4k-images-vips-api` | Binding-neutral `VipsImage` and `VipsRuntime` contracts |
| `bluetape4k-images-vips-java21` | JVips JNI backend; Java 21 toolchain; system libvips required |
| `bluetape4k-images-vips-java25` | vips-ffm FFM backend; Java 25 toolchain; native access required |
| `bluetape4k-images-benchmark` | kotlinx-benchmark results for scrimage vs libvips |
| `examples/basic-processing` | Non-published pure JVM image processing quickstart |
| `examples/ktor-image-api` | Non-published Ktor CAPTCHA and image thumbnail API quickstart |
| `examples/ktor-ocr-api` | Non-published Ktor OCR extraction API quickstart |
| `examples/spring-boot-image-api` | Non-published Spring Boot local-storage image API quickstart |
| `examples/spring-boot-ocr-api` | Non-published Spring Boot OCR extraction API quickstart |
| `bom/` | Consumer BOM for aligned image artifacts |

## Commands

```bash
./gradlew clean build
./gradlew build -x test
./gradlew :bluetape4k-images:build
./gradlew :bluetape4k-images:test
./gradlew :bluetape4k-images-barcode-api:test
./gradlew :bluetape4k-images-ocr:test
./gradlew :bluetape4k-images-ocr:test -Docr.enabled=true
./gradlew :bluetape4k-images-ocr:test -Docr.container.enabled=true
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

### `bluetape4k-images-barcode-api`

- Keep this module provider-neutral; do not add ZXing, BoofCV, OpenCV, ZBar,
  commercial SDK, or native decoder dependencies here.
- Providers implement the `BarcodeReader` contract from separate modules.
- Use `immutableImageOf(bytes/path/stream/source)` helpers for input
  conversions instead of duplicating image loading code.

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

### `bluetape4k-images-ocr`

- Uses Tess4J and host Tesseract; do not add OCR dependencies to
  `bluetape4k-images`.
- Host-native OCR tests are gated by `-Docr.enabled=true` and require
  Tesseract plus requested traineddata packages.
- Container OCR tests are gated by `-Docr.container.enabled=true` and require
  Docker. Run native/container OCR checks sequentially.

## Repo-Specific Guards

- For image module additions or artifact moves, update image-specific benchmark
  evidence and BOM/catalog constraints together.
- Run libvips/native/JNI, OCR, and Testcontainers-backed checks sequentially.
