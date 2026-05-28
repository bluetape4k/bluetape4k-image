package io.bluetape4k.images.benchmark

import com.sksamuel.scrimage.filter.BlurFilter
import com.sksamuel.scrimage.filter.GrayscaleFilter
import com.sksamuel.scrimage.filter.SepiaFilter
import com.sksamuel.scrimage.nio.JpegWriter
import com.sksamuel.scrimage.nio.PngWriter
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
 * Benchmarks representative high-level scrimage operation chains.
 *
 * The goal is to keep an allocation-sensitive baseline for APIs that create
 * intermediate [com.sksamuel.scrimage.ImmutableImage] values between resize,
 * filter, and encode stages.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsPrepend = ["--enable-native-access=ALL-UNNAMED"])
@State(Scope.Benchmark)
class ImagePipelineBenchmark {

    companion object : KLogging() {
        private val GRAYSCALE_FILTER = GrayscaleFilter()
        private val BLUR_FILTER = BlurFilter()
        private val SEPIA_FILTER = SepiaFilter()
        private val JPEG_WRITER = JpegWriter(80, false)
        private val PNG_WRITER = PngWriter(6)
    }

    /**
     * 4K photo preview path: resize, grayscale, then JPEG encode.
     */
    @Benchmark
    fun scrimage_photoPreviewJpeg(bh: Blackhole) {
        val bytes = BenchmarkImageSets.photo4k
            .scaleTo(1280, 720)
            .filter(GRAYSCALE_FILTER)
            .bytes(JPEG_WRITER)

        bh.consume(bytes)
    }

    /**
     * Document preview path: resize, blur, sepia, then PNG encode.
     */
    @Benchmark
    fun scrimage_documentPreviewPng(bh: Blackhole) {
        val bytes = BenchmarkImageSets.document
            .scaleTo(640, 905)
            .filter(BLUR_FILTER)
            .filter(SEPIA_FILTER)
            .bytes(PNG_WRITER)

        bh.consume(bytes)
    }
}
