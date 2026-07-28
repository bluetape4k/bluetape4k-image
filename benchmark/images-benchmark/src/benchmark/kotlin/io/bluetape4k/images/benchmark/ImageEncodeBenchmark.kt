package io.bluetape4k.images.benchmark

import com.sksamuel.scrimage.nio.JpegWriter
import com.sksamuel.scrimage.nio.PngWriter
import io.bluetape4k.images.vips.VipsImageFormat
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
 * JPEG와 PNG에 대한 scrimage/vips encode throughput을 비교합니다.
 *
 * natural photo fixture를 JPEG와 PNG로 encode합니다.
 *
 * ## 실행
 * ```bash
 * ./gradlew :bluetape4k-images-benchmark:benchmark
 * ```
 *
 * ## 지표
 * - scrimage_encodeJpeg: [JpegWriter]를 사용한 JPEG encode 평균 시간
 * - scrimage_encodePng: [PngWriter]를 사용한 PNG encode 평균 시간
 * - vips_encodeJpeg: vips JPEG encode 평균 시간. vips를 사용할 수 없으면 skip합니다.
 * - vips_encodePng: vips PNG encode 평균 시간. vips를 사용할 수 없으면 skip합니다.
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
     * quality=80에서 natural photo fixture의 scrimage JPEG encode throughput을 측정합니다.
     */
    @Benchmark
    fun scrimage_encodeJpeg(state: VipsBenchmarkState, bh: Blackhole) {
        val bytes = BenchmarkImageSets.naturalPhoto(state.imageName).bytes(JPEG_WRITER)
        bh.consume(bytes)
    }

    /**
     * compression=6에서 natural photo fixture의 scrimage PNG encode throughput을 측정합니다.
     */
    @Benchmark
    fun scrimage_encodePng(state: VipsBenchmarkState, bh: Blackhole) {
        val bytes = BenchmarkImageSets.naturalPhoto(state.imageName).bytes(PNG_WRITER)
        bh.consume(bytes)
    }

    /**
     * vips JPEG encode throughput을 측정합니다.
     *
     * 현재 host에서 vips를 사용할 수 없으면 즉시 반환합니다.
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
     * vips PNG encode throughput을 측정합니다.
     *
     * 현재 host에서 vips를 사용할 수 없으면 즉시 반환합니다.
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
