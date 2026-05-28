package io.bluetape4k.images.benchmark

import com.sksamuel.scrimage.nio.JpegWriter
import io.bluetape4k.images.vips.VipsImage
import io.bluetape4k.images.vips.VipsRuntime
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.logging.info
import io.bluetape4k.logging.warn
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import org.openjdk.jmh.annotations.Level
import java.nio.file.Paths

/**
 * Thread-scoped state that manages the vips runtime lifecycle and image bytes for JMH benchmarks.
 *
 * The vips implementation is present only on the runtime classpath, so this state initializes it
 * by reflection. When initialization fails, [vipsAvailable] is set to false and benchmark methods
 * must check it before invoking native operations.
 *
 * Example:
 * ```kotlin
 * @Benchmark
 * fun myBenchmark(state: VipsBenchmarkState, bh: Blackhole) {
 *     if (!state.vipsAvailable) { bh.consume(null); return }
 *     state.createVipsImage().use { img -> bh.consume(img.resize(800, 600)) }
 * }
 * ```
 */
@State(Scope.Thread)
class VipsBenchmarkState {

    companion object : KLogging() {
        private const val FFM_RUNTIME_CLASS = "io.bluetape4k.images.vips.java25.FfmVipsRuntime"
        private const val JNI_RUNTIME_CLASS = "io.bluetape4k.images.vips.java21.JVipsRuntime"
        private const val FFM_IMAGE_SUPPORT_CLASS = "io.bluetape4k.images.vips.java25.FfmVipsImageSupportKt"
        private const val JNI_IMAGE_SUPPORT_CLASS = "io.bluetape4k.images.vips.java21.JVipsImageSupportKt"

        // Library path override properties consumed by vips-ffm. See VipsLibLookup.java.
        private const val PROP_VIPS_PATH = "vipsffm.libpath.vips.override"
        private const val PROP_GLIB_PATH = "vipsffm.libpath.glib.override"
        private const val PROP_GOBJECT_PATH = "vipsffm.libpath.gobject.override"

        // Default Homebrew install path.
        private const val HOMEBREW_LIB = "/opt/homebrew/lib"
    }

    /** Whether the vips runtime initialized successfully. vips benchmarks skip when false. */
    var vipsAvailable: Boolean = false

    @Param("cafe", "landscape")
    var imageName: String = "cafe"

    /** JPEG bytes used to create vips images from the selected natural photo fixture. */
    var photo4kJpegBytes: ByteArray = ByteArray(0)

    /** JPEG bytes used to create vips images for thumbnail workloads. */
    var thumbnailJpegBytes: ByteArray = ByteArray(0)

    private var runtime: VipsRuntime? = null
    private var createImageFn: ((ByteArray) -> VipsImage)? = null

    @Setup(Level.Trial)
    fun setup() {
        // Prepare image bytes before each benchmark iteration.
        val jpegWriter = JpegWriter(80, false)
        photo4kJpegBytes = BenchmarkImageSets.naturalPhoto(imageName).bytes(jpegWriter)
        thumbnailJpegBytes = BenchmarkImageSets.thumbnail.bytes(jpegWriter)

        // macOS SIP strips DYLD_LIBRARY_PATH, so provide absolute Homebrew paths.
        applyMacOsVipsLibraryPaths()

        // Initialize the vips runtime by locating the selected implementation through reflection.
        vipsAvailable = tryInitVipsRuntime()
        if (vipsAvailable) {
            log.debug { "VipsBenchmarkState: vips runtime initialized" }
        } else {
            log.warn { "VipsBenchmarkState: vips runtime initialization failed; vips benchmarks will skip" }
        }
    }

    @TearDown(Level.Trial)
    fun tearDown() {
        // VipsRuntime.shutdown() is irreversible, so benchmark trials leave shutdown to JVM exit.
        runtime = null
        createImageFn = null
    }

    /**
     * Creates a vips image from encoded image bytes.
     *
     * @param bytes encoded image bytes
     * @return [VipsImage] instance; callers must close it
     * @throws IllegalStateException when [vipsAvailable] is false
     */
    fun createVipsImage(bytes: ByteArray): VipsImage {
        val fn = requireNotNull(createImageFn) { "vips is unavailable; check vipsAvailable before calling" }
        return fn(bytes)
    }

    /**
     * Registers Homebrew library paths so vips-ffm can locate libvips on macOS.
     *
     * macOS SIP removes DYLD_LIBRARY_PATH from signed JVMs, so SymbolLookup.libraryLookup
     * needs absolute paths injected through the `vipsffm.libpath.*.override` properties.
     */
    private fun applyMacOsVipsLibraryPaths() {
        val os = System.getProperty("os.name", "").lowercase()
        if (!os.contains("mac")) return

        listOf(
            PROP_VIPS_PATH to "$HOMEBREW_LIB/libvips.dylib",
            PROP_GLIB_PATH to "$HOMEBREW_LIB/libglib-2.0.dylib",
            PROP_GOBJECT_PATH to "$HOMEBREW_LIB/libgobject-2.0.dylib",
        ).forEach { (prop, path) ->
            if (System.getProperty(prop) == null && Paths.get(path).toFile().exists()) {
                System.setProperty(prop, path)
                log.info { "Configured macOS vips path: $prop=$path" }
            }
        }
    }

    private fun tryInitVipsRuntime(): Boolean {
        // Prefer Java 25 FFM, then fall back to Java 21 JNI when available.
        return tryInitWithClass(FFM_RUNTIME_CLASS, FFM_IMAGE_SUPPORT_CLASS, "ffmVipsImageOf")
            || tryInitWithClass(JNI_RUNTIME_CLASS, JNI_IMAGE_SUPPORT_CLASS, "vipsImageOf")
    }

    private fun tryInitWithClass(
        runtimeClass: String,
        supportClass: String,
        factoryMethodName: String,
    ): Boolean {
        return try {
            val runtimeKClass = Class.forName(runtimeClass)
            // Access the INSTANCE field generated for the Kotlin object singleton.
            val instance = runtimeKClass.getField("INSTANCE").get(null) as VipsRuntime
            instance.init()
            runtime = instance

            val supportKClass = Class.forName(supportClass)
            val method = supportKClass.getMethod(factoryMethodName, ByteArray::class.java)
            createImageFn = { bytes -> method.invoke(null, bytes) as VipsImage }
            true
        } catch (t: Throwable) {
            // Includes UnsatisfiedLinkError, ClassNotFoundException, and VipsInitializationException.
            log.warn(t) { "vips runtime initialization failed ($runtimeClass): ${t::class.simpleName}: ${t.message}" }
            false
        }
    }
}
