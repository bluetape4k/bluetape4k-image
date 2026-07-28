package io.bluetape4k.images.benchmark

import com.sksamuel.scrimage.filter.BlurFilter
import com.sksamuel.scrimage.filter.GrayscaleFilter
import com.sksamuel.scrimage.filter.SepiaFilter
import com.sksamuel.scrimage.nio.JpegWriter
import com.sksamuel.scrimage.nio.PngWriter
import io.bluetape4k.logging.KLogging
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State
import kotlinx.benchmark.Warmup
import kotlinx.benchmark.Blackhole
import java.util.concurrent.TimeUnit

/**
 * 대표적인 high-level scrimage operation chain을 benchmark합니다.
 *
 * resize, filter, encode stage 사이에 intermediate [com.sksamuel.scrimage.ImmutableImage] 값을 만드는 API에 대해
 * allocation-sensitive baseline을 유지하는 것이 목적입니다.
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
     * 4K photo preview path입니다. resize, grayscale, JPEG encode 순서로 실행합니다.
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
     * document preview path입니다. resize, blur, sepia, PNG encode 순서로 실행합니다.
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
