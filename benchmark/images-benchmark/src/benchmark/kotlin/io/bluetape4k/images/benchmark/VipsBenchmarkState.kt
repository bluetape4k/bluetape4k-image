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
 * JMH benchmark용 vips runtime lifecycle과 image bytes를 관리하는 thread-scoped state입니다.
 *
 * vips implementation은 runtime classpath에만 존재하므로 이 state는 reflection으로 초기화합니다.
 * initialization이 실패하면 [vipsAvailable]을 false로 설정하고, benchmark method는 native operation 호출 전에
 * 반드시 이 값을 확인해야 합니다.
 *
 * 예시:
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

        // vips-ffm이 소비하는 library path override property입니다. VipsLibLookup.java를 참고하십시오.
        private const val PROP_VIPS_PATH = "vipsffm.libpath.vips.override"
        private const val PROP_GLIB_PATH = "vipsffm.libpath.glib.override"
        private const val PROP_GOBJECT_PATH = "vipsffm.libpath.gobject.override"

        // 기본 Homebrew install path입니다.
        private const val HOMEBREW_LIB = "/opt/homebrew/lib"
    }

    /** vips runtime이 성공적으로 초기화됐는지 여부입니다. false이면 vips benchmark는 skip합니다. */
    var vipsAvailable: Boolean = false

    @Param("cafe", "landscape")
    var imageName: String = "cafe"

    /** 선택한 natural photo fixture에서 vips image를 생성하는 데 사용하는 JPEG bytes입니다. */
    var photo4kJpegBytes: ByteArray = ByteArray(0)

    /** thumbnail workload용 vips image를 생성하는 데 사용하는 JPEG bytes입니다. */
    var thumbnailJpegBytes: ByteArray = ByteArray(0)

    private var runtime: VipsRuntime? = null
    private var createImageFn: ((ByteArray) -> VipsImage)? = null

    @Setup(Level.Trial)
    fun setup() {
        // 각 benchmark iteration 전에 image bytes를 준비합니다.
        val jpegWriter = JpegWriter(80, false)
        photo4kJpegBytes = BenchmarkImageSets.naturalPhoto(imageName).bytes(jpegWriter)
        thumbnailJpegBytes = BenchmarkImageSets.thumbnail.bytes(jpegWriter)

        // macOS SIP가 DYLD_LIBRARY_PATH를 제거하므로 absolute Homebrew path를 제공합니다.
        applyMacOsVipsLibraryPaths()

        // reflection으로 선택된 implementation을 찾아 vips runtime을 초기화합니다.
        vipsAvailable = tryInitVipsRuntime()
        if (vipsAvailable) {
            log.debug { "VipsBenchmarkState: vips runtime initialized" }
        } else {
            log.warn { "VipsBenchmarkState: vips runtime initialization failed; vips benchmarks will skip" }
        }
    }

    @TearDown(Level.Trial)
    fun tearDown() {
        // VipsRuntime.shutdown()은 irreversible이므로 benchmark trial은 shutdown을 JVM exit에 맡깁니다.
        runtime = null
        createImageFn = null
    }

    /**
     * encoded image bytes에서 vips image를 생성합니다.
 *
     * @param bytes encoded image bytes입니다.
     * @return [VipsImage] instance입니다. caller가 반드시 close해야 합니다.
     * @throws IllegalStateException [vipsAvailable]이 false이면 던집니다.
     */
    fun createVipsImage(bytes: ByteArray): VipsImage {
        val fn = requireNotNull(createImageFn) { "vips is unavailable; check vipsAvailable before calling" }
        return fn(bytes)
    }

    /**
     * macOS에서 vips-ffm이 libvips를 찾을 수 있도록 Homebrew library path를 등록합니다.
 *
     * macOS SIP는 signed JVM에서 DYLD_LIBRARY_PATH를 제거합니다. 따라서 SymbolLookup.libraryLookup에는
     * `vipsffm.libpath.*.override` property를 통해 absolute path를 주입해야 합니다.
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
        // Java 25 FFM을 선호하고, 가능하면 Java 21 JNI로 fallback합니다.
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
            // Kotlin object singleton에 대해 생성된 INSTANCE field에 접근합니다.
            val instance = runtimeKClass.getField("INSTANCE").get(null) as VipsRuntime
            instance.init()
            runtime = instance

            val supportKClass = Class.forName(supportClass)
            val method = supportKClass.getMethod(factoryMethodName, ByteArray::class.java)
            createImageFn = { bytes -> method.invoke(null, bytes) as VipsImage }
            true
        } catch (t: Throwable) {
            // UnsatisfiedLinkError, ClassNotFoundException, VipsInitializationException을 포함합니다.
            log.warn(t) { "vips runtime initialization failed ($runtimeClass): ${t::class.simpleName}: ${t.message}" }
            false
        }
    }
}
