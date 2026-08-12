# Module bluetape4k-images-vips-java25

English | [한국어](./README.ko.md)

FFM (Foreign Function & Memory) API backend for libvips image processing on JDK 25+. Uses zero JNI, pure FFM bindings via `vips-ffm`. Requires a system-installed libvips library.

> **CRITICAL:** This module requires the JVM flag `--enable-native-access=ALL-UNNAMED` to be set at startup. Without this flag, the FFM API will fail. See [JVM Configuration](#jvm-configuration) below.

## Architecture

### Class Diagram

![images vips java25 Class Structure diagram](../docs/images/readme-diagrams/images-vips-java25-class-01.png)

## Prerequisites

### Java Version
- **Minimum:** JDK 25
- **Recommended:** JDK 25

### System Requirements
- **macOS:** `brew install vips`
- **Ubuntu/Debian:** `apt-get install libvips-tools libvips-dev`
- **RHEL/CentOS:** `yum install vips-devel vips-tools`
- **Windows:** Download from [libvips releases](https://libvips.github.io/libvips/) and set PATH

### JVM Configuration

The `--enable-native-access=ALL-UNNAMED` flag MUST be set when running your application. This enables FFM API access to native memory.

#### In Gradle Tests

```kotlin
tasks.withType<Test>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
```

#### In Spring Boot or Container Launchers

Set the flag on the JVM command line or through your process manager. For
containerized apps, `JAVA_TOOL_OPTIONS` is usually the simplest portable option:

```bash
export JAVA_TOOL_OPTIONS="--enable-native-access=ALL-UNNAMED"
java -jar myapp.jar
```

For Gradle `bootRun`:

```kotlin
tasks.named<JavaExec>("bootRun") {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
```

#### In Java Command Line

```bash
java --enable-native-access=ALL-UNNAMED -jar myapp.jar
```

#### In IDE (IntelliJ IDEA)

1. Run → Edit Configurations
2. Find your test configuration
3. Add VM options: `--enable-native-access=ALL-UNNAMED`

Without this flag, `FfmVipsRuntime.init()` will log a warning and FFM operations may fail.

## Setup

### Add Dependency

```kotlin
dependencies {
    implementation("io.github.bluetape4k.image:bluetape4k-images-vips-java25:<version>")
}
```

### Initialize at Application Startup

```kotlin
import io.bluetape4k.images.vips.java25.FfmVipsRuntime

// In your main function or Spring Boot @PostConstruct
FfmVipsRuntime.init(
    concurrency = Runtime.getRuntime().availableProcessors(),
    maxPixels = 150_000_000L
)

// At application shutdown
Runtime.getRuntime().addShutdownHook(Thread {
    FfmVipsRuntime.shutdown()
})
```

## Features

- **FFM-based (JDK 25+):** No JNI, pure Foreign Function & Memory API
- **Thread-safe initialization:** CAS-based state machine prevents race conditions
- **Image decoding:** JPEG, PNG, WebP, capability-gated AVIF/HEIC (magic byte allowlist)
- **Image operations:** Resize, thumbnail, crop
- **Image encoding:** JPEG, PNG, WebP, capability-gated AVIF/HEIC with configurable quality
- **Coroutine support:** Suspend variants for async processing (`suspendFfmVipsImageOf`)
- **Security:** Format allowlist, maxPixels limit, bounded input stream (50 MB)
- **Memory safety:** Each operation uses isolated FFM Arena

## Usage

### Basic: Load, Resize, Encode

```kotlin
import io.bluetape4k.images.vips.java25.FfmVipsRuntime
import io.bluetape4k.images.vips.java25.ffmVipsImageOf
import io.bluetape4k.images.vips.VipsImageFormat
import io.bluetape4k.images.vips.VipsEncodeOptions
import java.nio.file.Paths

// 1. Initialize runtime (once at startup)
FfmVipsRuntime.init(
    concurrency = 4,
    maxPixels = 150_000_000L
)

// 2. Load image from file
val image = ffmVipsImageOf(Paths.get("input.jpg"))

image.use { img ->
    // 3. Resize to 800x600
    val resized = img.resize(800, 600)
    resized.use { rs ->
        // 4. Save as WebP
        rs.writeTo(
            Paths.get("output.webp"),
            format = VipsImageFormat.WEBP,
            options = VipsEncodeOptions.WebpOptions(quality = 85)
        )
    }
}
```

### Thumbnail with Coroutines

```kotlin
import io.bluetape4k.images.vips.java25.suspendFfmVipsImageOf
import kotlinx.coroutines.runBlocking
import java.nio.file.Paths

runBlocking {
    // Load asynchronously via IO dispatcher
    val image = suspendFfmVipsImageOf(Paths.get("large.jpg"))
    
    image.use { img ->
        // Fit to 300px on longest side
        val thumbnail = img.thumbnail(300)
        thumbnail.use { thumb ->
            thumb.writeTo(
                Paths.get("thumb.png"),
                format = VipsImageFormat.PNG
            )
        }
    }
}
```

### From ByteArray

```kotlin
import io.bluetape4k.images.vips.java25.ffmVipsImageOf
import io.bluetape4k.images.vips.VipsImageFormat

val bytes = readImageBytes() // Your image bytes

val image = ffmVipsImageOf(bytes)
image.use { img ->
    println("Image dimensions: ${img.width}x${img.height}")
    println("Channels: ${img.bands}")
    
    // Get encoded bytes
    val jpegBytes = img.toBytes(
        format = VipsImageFormat.JPEG,
        options = VipsEncodeOptions.JpegOptions(quality = 90)
    )
}
```

### From InputStream

```kotlin
import io.bluetape4k.images.vips.java25.ffmVipsImageOf
import java.io.FileInputStream

FileInputStream("image.webp").use { stream ->
    val image = ffmVipsImageOf(stream)
    image.use { img ->
        // Max 50 MB enforced automatically
        val cropped = img.crop(left = 0, top = 0, width = 400, height = 300)
        cropped.use { crop ->
            // Process cropped region
        }
    }
}
```

### From Okio Sources

```kotlin
import io.bluetape4k.images.vips.java25.ffmVipsImageOf
import io.bluetape4k.images.vips.java25.suspendFfmVipsImageOf
import io.bluetape4k.okio.asSource
import io.bluetape4k.okio.buffered
import io.bluetape4k.okio.coroutines.asSuspendedSource
import io.bluetape4k.okio.coroutines.buffered as bufferedSuspended
import java.io.FileInputStream
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Paths
import java.nio.file.StandardOpenOption.READ

// Raw Source is helper-owned: ffmVipsImageOf buffers and closes it.
val sourceImage = ffmVipsImageOf(FileInputStream("image.webp").asSource())
sourceImage.close()

// BufferedSource is caller-owned: close it at the call site.
FileInputStream("image.webp").asSource().buffered().use { source ->
    ffmVipsImageOf(source).use { image ->
        println("${image.width}x${image.height}")
    }
}

suspend fun loadFromSuspendedSource() {
    AsynchronousFileChannel.open(Paths.get("image.webp"), READ).use { channel ->
        val source = channel.asSuspendedSource().bufferedSuspended()
        try {
            suspendFfmVipsImageOf(source).use { image ->
                println("${image.width}x${image.height}")
            }
        } finally {
            source.close()
        }
    }
}
```

For local large files, `Path` remains the preferred FFM entry point and has the
best measured JVM allocation profile. Use Okio sources when the caller already
owns a stream, pipe, or `bluetape4k-okio` suspended boundary. Non-Path loads are
still subject to the 50 MB compressed input guard.

### Crop Region

```kotlin
import io.bluetape4k.images.vips.java25.ffmVipsImageOf

val image = ffmVipsImageOf(Paths.get("input.jpg"))
image.use { img ->
    // Extract 400x300 region starting at (50, 100)
    val region = img.crop(left = 50, top = 100, width = 400, height = 300)
    region.use { r ->
        r.writeTo(Paths.get("cropped.jpg"))
    }
}
```

## Security

### Image Format Allowlist

JPEG, PNG, WebP, AVIF, and HEIC are permitted. AVIF/HEIC require a libvips build with libheif and the relevant encoder:

```kotlin
try {
    ffmVipsImageOf(unsafeBytes)
} catch (e: VipsDecodeException) {
    // Handle unsupported format or missing native codec support
    logger.error("Format not allowed: ${e.message}")
}
```

AVIF output uses HEIF compression `AV1`; HEIC output uses HEIF compression `HEVC`.
If the native libvips build lacks `heifload_buffer` or `heifsave_buffer`, the API fails early with a sanitized `VipsDecodeException` or `VipsEncodeException`.

#### AVIF / HEIC Capability Matrix

| Format | Decode | Encode | Native dependency |
|--------|--------|--------|-------------------|
| AVIF | Capability-gated | Capability-gated | libvips with libheif and an AV1 encoder such as libaom |
| HEIC | Capability-gated | Capability-gated | libvips with libheif and HEVC encoder support |

The Java 25 backend maps AVIF output to HEIF `AV1` compression and HEIC output
to HEIF `HEVC` compression. Both paths require the matching native libvips
loader/saver support on the deployment host.

Inspect codec status before enabling AVIF/HEIC routes:

```kotlin
import io.bluetape4k.images.vips.VipsImageFormat
import io.bluetape4k.images.vips.VipsIncubatingApi
import io.bluetape4k.images.vips.java25.FfmVipsRuntime

@OptIn(VipsIncubatingApi::class)
fun inspectCodecStatus() {
val report = FfmVipsRuntime.codecCapabilityReport()
val avif = report.codec(VipsImageFormat.AVIF)
val heic = report.codec(VipsImageFormat.HEIC)
}
```

The report checks `heifload_buffer` for decode and `heifsave_buffer` for encode.
Use `FfmVipsRuntime.smokeTestCodec(...)` with caller-provided AVIF/HEIC samples
to verify the exact deployment host.

### Maximum Pixel Count

Image dimensions are validated against `FfmVipsRuntime.maxPixels`. Exceeding this limit throws `VipsDecodeException`:

```kotlin
// Default: 150,000,000 pixels
// Customizable via init()
FfmVipsRuntime.init(concurrency = 4, maxPixels = 100_000_000L)
```

For a 5000x5000 image with 3 channels: 75,000,000 pixels (under default limit).

### Input Stream Limit

Streams are bounded to 50 MB. Larger inputs throw `VipsDecodeException`:

```kotlin
val stream: InputStream = // ... large file
try {
    ffmVipsImageOf(stream) // Will fail if > 50 MB
} catch (e: VipsDecodeException) {
    // Handle size violation
}
```

### Path Traversal Warning

When loading from `Path`, callers must validate that the path is within an allowed directory:

```kotlin
import java.nio.file.Paths
import java.io.File

fun loadImage(userProvidedPath: String): VipsImage {
    val base = Paths.get("/allowed/uploads")
    val requested = base.resolve(userProvidedPath).normalize()
    
    // Prevent path traversal: ../../../etc/passwd
    check(requested.startsWith(base)) {
        "Path traversal attempt: $requested"
    }
    
    return ffmVipsImageOf(requested)
}
```

## Error Handling

```kotlin
import io.bluetape4k.images.vips.VipsDecodeException
import io.bluetape4k.images.vips.VipsEncodeException

try {
    val image = ffmVipsImageOf(bytes)
    image.use { img ->
        img.resize(800, 600).use { resized ->
            resized.writeTo(path, VipsImageFormat.JPEG)
        }
    }
} catch (e: VipsDecodeException) {
    // Decoding failed: unsupported format, corruption, or maxPixels exceeded
    logger.error("Failed to decode image", e)
} catch (e: VipsEncodeException) {
    // Encoding failed: invalid dimensions or I/O error
    logger.error("Failed to encode image", e)
}
```

## Runtime Lifecycle

```kotlin
import io.bluetape4k.images.vips.VipsInitializationException

// Check state at any time
if (!FfmVipsRuntime.isInitialized) {
    FfmVipsRuntime.init(concurrency = 4)
}

if (FfmVipsRuntime.isShutdown) {
    throw VipsInitializationException(
        "libvips has been shut down — restart the process to re-initialize"
    )
}

// Shutdown (optional; process exit handles cleanup)
FfmVipsRuntime.shutdown()

// After shutdown, re-initialization requires process restart
FfmVipsRuntime.init() // VipsInitializationException
```

## Virtual Thread Compatibility

`FfmVipsRuntime` uses `AtomicReference` for thread-safe state without monitors. Safe for use with Virtual Threads:

```kotlin
import java.util.concurrent.Executors

Thread.ofVirtual().factory().newThread {
    val image = ffmVipsImageOf(bytes)
    // Safe under Virtual Thread
}.start()
```

The `suspendFfmVipsImageOf*` variants use `withContext(Dispatchers.IO)` for non-blocking loading.

## Spring Boot Integration

### Configuration

```yaml
app:
  images:
    vips:
      concurrency: 4
      maxPixels: 150000000
      enableNativeAccess: true  # Ensure this is set
```

### Component

```kotlin
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy

@Component
class VipsImageService(
    @Value("\${app.images.vips.concurrency:4}")
    private val concurrency: Int,
    
    @Value("\${app.images.vips.maxPixels:150000000}")
    private val maxPixels: Long,
) {
    @PostConstruct
    fun init() {
        FfmVipsRuntime.init(concurrency, maxPixels)
        log.info("FfmVipsRuntime initialized: concurrency=$concurrency")
    }
    
    @PreDestroy
    fun shutdown() {
        FfmVipsRuntime.shutdown()
        log.info("FfmVipsRuntime shut down")
    }
    
    suspend fun resizeImage(bytes: ByteArray, width: Int, height: Int): ByteArray {
        val image = suspendFfmVipsImageOf(bytes)
        return image.use { img ->
            img.resize(width, height).use { resized ->
                resized.toBytes(VipsImageFormat.JPEG)
            }
        }
    }
}
```

### Controller Example

```kotlin
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.http.MediaType

@RestController
@RequestMapping("/api/images")
class ImageController(
    private val vipsService: VipsImageService
) {
    @PostMapping("/resize", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun resize(
        @RequestParam file: MultipartFile,
        @RequestParam width: Int,
        @RequestParam height: Int,
    ): ByteArray {
        val bytes = file.bytes
        return vipsService.resizeImage(bytes, width, height)
    }
}
```

## Comparison with the JDK 25 JVips JNI backend (legacy `java21` module name)

| Feature | java25 (FFM) | java21 (JNI, legacy name) |
|---------|------|------|
| **Binding** | vips-ffm (FFM API) | libjvips (JNI) |
| **Java Version** | 25+ | 25+ |
| **JVM Flag** | `--enable-native-access=ALL-UNNAMED` | None |
| **Memory Model** | Arena-based auto-cleanup | JNI reference counting |
| **Platform** | macOS + Linux | Linux only (no macOS native binary) |
| **API** | Same VipsImage interface | Same VipsImage interface |

Both modules implement the same `VipsImage` interface and are interchangeable at the API level.

### Performance vs scrimage

![Performance vs scrimage diagram](../docs/images/readme-diagrams/images-vips-java25-architecture-02.png)

**CI Linux (Ubuntu 24.04, GraalVM 25, libvips 8.15.1)**

| Operation | scrimage (ms/op) | vips-ffm (ms/op) | Speedup |
|-----------|-----------------|------------------|---------|
| resize 4K→1920×1080 | 187.29 | **0.591** | **317×** |
| resize 4K→1280×720  | 119.45 | **0.626** | **191×** |
| encode JPEG         | 171.16 | **37.20** | **4.6×** |
| encode PNG          | 249.01 | **137.95** | **1.8×** |

**macOS (Apple Silicon, GraalVM 25.0.3, libvips 8.18.2)**

| Operation | scrimage (ms/op) | vips-ffm (ms/op) | Speedup |
|-----------|-----------------|------------------|---------|
| resize 4K→1920×1080 | 71.16 | **0.202** | **352×** |
| encode JPEG         | 52.49 | **15.67** | **3.3×** |
| encode PNG          | 94.87 | **49.88** | **1.9×** |

Full details: [`benchmark/images-benchmark/docs/benchmark-results-2026-04-29.md`](../benchmark/images-benchmark/docs/benchmark-results-2026-04-29.md)

## Testing

Tests are skipped automatically if libvips is unavailable:

```bash
./gradlew :bluetape4k-images-vips-java25:test
# Tests skipped if System.getProperty("vips.enabled") != "true"

# Force test execution (requires system libvips installed)
./gradlew :bluetape4k-images-vips-java25:test -Dvips.enabled=true
```

### Golden Image Tests (Master Source)

java25 is the **authoritative source** for vips golden images stored in `images-vips-api/src/testFixtures/resources/golden/vips/`.

- Update mode enabled only on Java 25+ — guarded by `@EnabledForJreRange(min = JRE.JAVA_25)`
- Regenerate goldens: `-Dbluetape4k.images.golden.update=true -Dvips.enabled=true`
- CI guard prevents accidental regeneration in CI environments

```bash
# Regenerate golden images (must run on Java 25+)
./gradlew :bluetape4k-images-vips-java25:test \
    -Dvips.enabled=true \
    -Dbluetape4k.images.golden.update=true
```

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

### "FFM API requires --enable-native-access"

**Error:** UnsupportedOperationException when calling FFM methods.

**Solution:** Add `--enable-native-access=ALL-UNNAMED` to JVM arguments. See [JVM Configuration](#jvm-configuration).

### "libvips not found" or "Cannot find vips library"

**Error:** UnsatisfiedLinkError or similar.

**Solution:** Install system libvips:
```bash
# macOS
brew install vips

# Ubuntu
apt-get install libvips-tools libvips-dev

# Verify installation
vips --version
```

On Homebrew macOS, export `DYLD_LIBRARY_PATH=/opt/homebrew/lib` before starting
consumer applications if the JVM cannot find `libvips`.

### "Unsupported image format"

**Error:** VipsDecodeException with "only JPEG, PNG, WebP, AVIF, and HEIC are allowed".

**Solution:** Convert your image to a supported format, or install libvips with libheif/libaom when using AVIF/HEIC:
```bash
# Using ImageMagick
convert input.gif output.jpg

# Or online tools
```

### "Image exceeds maximum pixel count"

**Error:** VipsDecodeException with dimensions.

**Solution:** Either:
1. Increase `maxPixels` during init (if safe)
2. Resize the input image first
3. Reject oversized uploads in your service layer

```kotlin
if (width * height > SAFE_LIMIT) {
    throw BadRequestException("Image too large")
}
```

## References

- [vips-ffm on GitHub](https://github.com/criteo-forks/vips-ffm)
- [libvips Official Documentation](https://libvips.github.io/)
- [FFM API (JEP 454)](https://openjdk.org/jeps/454)
- [Parent VipsImage Interface](../images-vips-api/src/main/kotlin/io/bluetape4k/images/vips/VipsImage.kt)
