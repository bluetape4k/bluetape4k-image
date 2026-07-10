# Module bluetape4k-images-vips-java21

English | [한국어](./README.ko.md)

JVips (JNI) backend implementation for libvips image processing. Provides fast, memory-efficient image manipulation on Java 21+ via native bindings. On Linux, JVips bundles native `.so` libraries. On macOS, system libvips is required.

## Architecture

### JVips Processing Pipeline

![JVips Processing Pipeline diagram](../docs/images/readme-diagrams/images-vips-java21-architecture-01.png)

### Class Diagram

![images vips java21 Class Structure 2 diagram](../docs/images/readme-diagrams/images-vips-java21-class-02.png)

## Setup

### macOS

Install system libvips via Homebrew:

```bash
brew install vips
```

Verify installation:

```bash
vips --version
```

### Linux

On most distributions, install libvips-tools:

```bash
# Debian / Ubuntu
sudo apt-get install libvips-tools

# RHEL / CentOS / Fedora
sudo yum install vips-tools

# Alpine
apk add vips
```

The JVips library bundles native `.so` files, so no additional setup is needed beyond installing the system package.

### Gradle Dependency

Add to `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.bluetape4k.image:bluetape4k-images-vips-java21:<version>")
}
```

Or use the BOM:

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k.image:bluetape4k-image-bom:<version>"))
    implementation("io.github.bluetape4k.image:bluetape4k-images-vips-java21")
}
```

## Features

- **JNI Native Bindings**: Direct access to libvips C library via JVips JNI
- **Fast & Memory-Efficient**: Scales 4000x3000 images in <100ms
- **Security by Default**: Format allowlist (JPEG/PNG/WebP/AVIF/HEIC), 50 MB input limit, maxPixels validation
- **Immutable Operations**: All image operations return new instances (no in-place mutation)
- **Coroutine Support**: Async variants wrap blocking JNI calls with `Dispatchers.IO`
- **Multiple Output Formats**: JPEG (lossy), PNG (lossless), WebP (best compression), capability-gated AVIF
- **Virtual Thread Safe**: Uses `AtomicReference<State>` CAS instead of `@Synchronized` blocks

## Usage Examples

### Basic Initialization and Image Loading

```kotlin
import io.bluetape4k.images.vips.java21.JVipsRuntime
import io.bluetape4k.images.vips.java21.vipsImageOf
import io.bluetape4k.images.vips.VipsImageFormat
import java.nio.file.Paths

fun main() {
    // Initialize JVips runtime (required once per application)
    JVipsRuntime.init(concurrency = 4, maxPixels = 150_000_000L)
    
    try {
        // Load image from file
        val imagePath = Paths.get("sample.jpg")
        vipsImageOf(imagePath).use { image ->
            println("Image dimensions: ${image.width}x${image.height}, bands: ${image.bands}")
        }
    } finally {
        // Shutdown before process exit
        JVipsRuntime.shutdown()
    }
}
```

### Resize and Convert to WebP

```kotlin
import io.bluetape4k.images.vips.java21.vipsImageOf
import io.bluetape4k.images.vips.VipsImageFormat
import java.nio.file.Paths

fun resizeAndConvert(inputPath: String, outputPath: String) {
    vipsImageOf(Paths.get(inputPath)).use { original ->
        // Resize to 800x600, preserving aspect ratio
        original.resize(800, 600).use { resized ->
            // Convert to WebP and write
            resized.writeTo(
                Paths.get(outputPath),
                format = VipsImageFormat.WEBP
            )
        }
    }
}
```

### Thumbnail Generation

```kotlin
import io.bluetape4k.images.vips.java21.vipsImageOf
import io.bluetape4k.images.vips.VipsImageFormat
import io.bluetape4k.images.vips.VipsEncodeOptions
import java.nio.file.Paths

fun generateThumbnail(inputPath: String, outputPath: String) {
    vipsImageOf(Paths.get(inputPath)).use { original ->
        // Create thumbnail with longest side = 300px
        original.thumbnail(300).use { thumbnail ->
            // Encode as JPEG with quality 85
            thumbnail.writeTo(
                Paths.get(outputPath),
                format = VipsImageFormat.JPEG,
                options = VipsEncodeOptions.JpegOptions(quality = 85)
            )
        }
    }
}
```

### Loading from ByteArray with Security

```kotlin
import io.bluetape4k.images.vips.java21.vipsImageOf
import io.bluetape4k.images.vips.VipsDecodeException
import java.io.File

fun loadImageFromBytes(bytes: ByteArray): Int {
    return try {
        vipsImageOf(bytes).use { image ->
            println("Loaded ${image.width}x${image.height} image")
            image.width * image.height
        }
    } catch (e: VipsDecodeException) {
        System.err.println("Format not allowed or image too large: ${e.message}")
        0
    }
}
```

### Coroutine-Based Async Loading

```kotlin
import io.bluetape4k.images.vips.java21.suspendVipsImageOf
import io.bluetape4k.images.vips.VipsImageFormat
import kotlinx.coroutines.runBlocking
import java.nio.file.Paths

fun main() = runBlocking {
    // Load image asynchronously on Dispatchers.IO
    val image = suspendVipsImageOf(Paths.get("large.png"))
    
    image.use { img ->
        val thumbnail = img.thumbnail(500)
        
        thumbnail.use { thumb ->
            thumb.writeTo(
                Paths.get("thumbnail.webp"),
                format = VipsImageFormat.WEBP
            )
        }
    }
}
```

### Loading from Okio Sources

```kotlin
import io.bluetape4k.images.vips.java21.suspendVipsImageOf
import io.bluetape4k.images.vips.java21.vipsImageOf
import io.bluetape4k.okio.asSource
import io.bluetape4k.okio.buffered
import io.bluetape4k.okio.coroutines.asSuspendedSource
import io.bluetape4k.okio.coroutines.buffered as bufferedSuspended
import java.io.FileInputStream
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Paths
import java.nio.file.StandardOpenOption.READ

// Raw Source is helper-owned: vipsImageOf buffers and closes it.
val sourceImage = vipsImageOf(FileInputStream("image.webp").asSource())
sourceImage.close()

// BufferedSource is caller-owned: close it at the call site.
FileInputStream("image.webp").asSource().buffered().use { source ->
    vipsImageOf(source).use { image ->
        println("${image.width}x${image.height}")
    }
}

suspend fun loadFromSuspendedSource() {
    AsynchronousFileChannel.open(Paths.get("image.webp"), READ).use { channel ->
        val source = channel.asSuspendedSource().bufferedSuspended()
        try {
            suspendVipsImageOf(source).use { image ->
                println("${image.width}x${image.height}")
            }
        } finally {
            source.close()
        }
    }
}
```

For local large files, `Path` remains the preferred JVips entry point. Use
Okio sources when the service already receives image bytes through a stream,
pipe, or `bluetape4k-okio` suspended boundary. Non-Path loads are still subject
to the 50 MB compressed input guard.

### Image Crop and Output

```kotlin
import io.bluetape4k.images.vips.java21.vipsImageOf
import io.bluetape4k.images.vips.VipsImageFormat
import java.io.ByteArrayOutputStream
import java.nio.file.Paths

fun cropAndExportBytes(imagePath: String): ByteArray {
    return vipsImageOf(Paths.get(imagePath)).use { original ->
        // Crop 200x200 region starting at (50, 50)
        original.crop(left = 50, top = 50, width = 200, height = 200).use { cropped ->
            // Export to PNG (lossless)
            cropped.toBytes(VipsImageFormat.PNG)
        }
    }
}
```

## Security Considerations

All public `vipsImageOf*` functions enforce security checks in order:

1. **Format Allowlist**: JPEG, PNG, WebP, AVIF, and HEIC headers are accepted
   - JPEG: magic bytes `FF D8 FF`
   - PNG: magic bytes `89 50 4E 47`
   - WebP: RIFF header with `WEBP` marker at offset 8
   - AVIF/HEIC: ISO BMFF `ftyp` brand (`avif`, `avis`, `heic`, `heix`, `hevc`, `hevx`, `mif1`, `msf1`)

2. **Input Size Limit**: Maximum 50 MB per input stream

3. **Max Pixels Validation**: `width × height × bands` must not exceed the configured threshold (default: 150 million pixels)

Unsupported formats or violations raise `VipsDecodeException` with descriptive error messages.

AVIF encoding requires a libvips/JVips build with libheif and an AV1 encoder such as libaom.
HEIC encoding is not exposed by the JVips binding; use the Java 25 FFM backend when HEIC output is required.

### AVIF / HEIC Capability Matrix

| Format | Decode | Encode | Native dependency |
|--------|--------|--------|-------------------|
| AVIF | Capability-gated | Capability-gated | libvips with libheif and an AV1 encoder such as libaom |
| HEIC | Capability-gated | N/A | libvips with libheif for decode; JVips does not expose HEIC encode |

The Java 21 backend allowlists AVIF/HEIC ISO BMFF brands before decoding.
Unsupported bytes fail before libvips is called. Valid AVIF/HEIC containers still
depend on the host libvips codec set; missing native support is reported as a
sanitized `VipsDecodeException` or `VipsEncodeException`.

Inspect codec status before enabling AVIF/HEIC routes:

```kotlin
import io.bluetape4k.images.vips.VipsImageFormat
import io.bluetape4k.images.vips.VipsIncubatingApi
import io.bluetape4k.images.vips.java21.JVipsRuntime

@OptIn(VipsIncubatingApi::class)
fun inspectCodecStatus() {
val report = JVipsRuntime.codecCapabilityReport()
val avif = report.codec(VipsImageFormat.AVIF)
val heic = report.codec(VipsImageFormat.HEIC)
}
```

JVips cannot inspect native libvips operations directly, so AVIF/HEIC decode and
AVIF encode report `UNKNOWN`; use `JVipsRuntime.smokeTestCodec(...)` with
caller-provided samples on the deployment host. HEIC encode reports
`UNAVAILABLE` because the JVips binding does not expose it.

## Concurrency & Thread Safety

- **JVipsRuntime singleton**: Thread-safe via `AtomicReference<State>` compare-and-swap
- **Concurrent initialization**: Spin-waits without blocking (no `@Synchronized`, Virtual Thread safe)
- **VipsImage instances**: Single-threaded. Do not share across coroutines or threads without synchronization
- **JNI calls**: Isolated per test via `forkEvery = 1` in Gradle

## Testing

Tests require libvips to be installed. Run with:

```bash
# Full test suite (requires libvips)
./gradlew :bluetape4k-images-vips-java21:test -Dvips.enabled=true

# Include vips tests in tagged execution
./gradlew test -PincludeTags=vips-required

# Skip vips tests (default)
./gradlew test
```

Test classes are tagged with `@Tag("vips-required")` and skipped unless explicitly enabled.

### Golden Image Tests

Compares vips operation results against golden images stored in `images-vips-api` testFixtures (`src/testFixtures/resources/golden/vips/`).

- Run with `-Dvips.enabled=true` on Linux with libvips installed
- Golden images are generated exclusively by the java25 module (`@EnabledForJreRange(min = JRE.JAVA_25)` guard prevents regeneration here)
- Comparison uses configurable per-channel pixel delta tolerance

### Property-Based Tests

5 invariants × 3 formats (JPEG/PNG/WebP) verified via `@ParameterizedTest`.

| Invariant | Description |
|-----------|-------------|
| Dimensions preserved | Resize output matches requested width/height |
| Output is non-empty | Encoded bytes are always produced |
| Format round-trip | Decode → encode → decode yields same dimensions |
| Crop bounds | Cropped region never exceeds original bounds |
| Thumbnail proportionality | Thumbnail longest side fits the requested max dimension |

## Troubleshooting

### "UnsatisfiedLinkError: Can't load library: libvips"

**macOS**: Install system libvips
```bash
brew install vips
```

**Linux**: Install libvips-tools package (JVips bundles native libs)
```bash
sudo apt-get install libvips-tools
```

### "Image exceeds maximum pixel count"

The `maxPixels` threshold (default 150 million) was exceeded. Either:
- Resize input before processing
- Increase `maxPixels` in `JVipsRuntime.init()`

### "libvips has been shut down — restart the process"

`JVipsRuntime.shutdown()` is irreversible. The process must be restarted to re-initialize.

**Do not use `@PreDestroy` hooks** with Spring Boot devtools — it causes restart-induced exceptions. Use `Runtime.addShutdownHook()` instead.

## See Also

- [bluetape4k-images](../images/) — Scrimage-based image processing (Coroutine async)
- [bluetape4k-images-vips-api](../images-vips-api/) — VipsRuntime and VipsImage contracts
- [bluetape4k-images-vips-java25](../images-vips-java25/) — Panama FFM backend (macOS + Linux, recommended)
- [bluetape4k-images-benchmark](../benchmark/images-benchmark/) — JMH benchmarks: scrimage vs vips performance comparison
