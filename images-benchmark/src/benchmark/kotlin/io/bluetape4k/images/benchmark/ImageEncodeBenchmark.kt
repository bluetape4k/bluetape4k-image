package io.bluetape4k.images.benchmark

import com.sksamuel.scrimage.nio.JpegWriter
import com.sksamuel.scrimage.nio.PngWriter
import io.bluetape4k.images.vips.VipsImageFormat
import io.bluetape4k.logging.KLogging
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

/**
 * Compares scrimage and vips encode throughput for JPEG and PNG.
 *
 * Natural photo fixtures are encoded to JPEG and PNG.
 *
 * ## Run
 * ```bash
 * ./gradlew :bluetape4k-images-benchmark:benchmark
 * ```
 *
 * ## Metrics
 * - scrimage_encodeJpeg: average JPEG encode time with [JpegWriter]
 * - scrimage_encodePng: average PNG encode time with [PngWriter]
 * - vips_encodeJpeg: average vips JPEG encode time, skipped when vips is unavailable
 * - vips_encodePng: average vips PNG encode time, skipped when vips is unavailable
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsPrepend = ["--enable-native-access=ALL-UNNAMED"])
@State(Scope.Benchmark)
class ImageEncodeBenchmark {

    companion object : KLogging() {
        private val JPEG_WRITER = JpegWriter(80, false)
        private val PNG_WRITER = PngWriter(6)
    }

    /**
     * Measures scrimage JPEG encode throughput for a natural photo fixture at quality=80.
     */
    @Benchmark
    fun scrimage_encodeJpeg(state: VipsBenchmarkState, bh: Blackhole) {
        val bytes = BenchmarkImageSets.naturalPhoto(state.imageName).bytes(JPEG_WRITER)
        bh.consume(bytes)
    }

    /**
     * Measures scrimage PNG encode throughput for a natural photo fixture at compression=6.
     */
    @Benchmark
    fun scrimage_encodePng(state: VipsBenchmarkState, bh: Blackhole) {
        val bytes = BenchmarkImageSets.naturalPhoto(state.imageName).bytes(PNG_WRITER)
        bh.consume(bytes)
    }

    /**
     * Measures vips JPEG encode throughput.
     *
     * Returns immediately when vips is unavailable on the current host.
     */
    @Benchmark
    fun vips_encodeJpeg(state: VipsBenchmarkState, bh: Blackhole) {
        if (!state.vipsAvailable) {
            bh.consume(null)
            return
        }
        state.createVipsImage(state.photo4kJpegBytes).use { img ->
            val bytes = img.toBytes(VipsImageFormat.JPEG)
            bh.consume(bytes)
        }
    }

    /**
     * Measures vips PNG encode throughput.
     *
     * Returns immediately when vips is unavailable on the current host.
     */
    @Benchmark
    fun vips_encodePng(state: VipsBenchmarkState, bh: Blackhole) {
        if (!state.vipsAvailable) {
            bh.consume(null)
            return
        }
        state.createVipsImage(state.photo4kJpegBytes).use { img ->
            val bytes = img.toBytes(VipsImageFormat.PNG)
            bh.consume(bytes)
        }
    }
}
