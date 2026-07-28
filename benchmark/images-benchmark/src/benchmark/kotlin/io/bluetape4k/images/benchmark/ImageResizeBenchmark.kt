package io.bluetape4k.images.benchmark

import io.bluetape4k.logging.KLogging
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.Warmup
import kotlinx.benchmark.Blackhole
import java.util.concurrent.TimeUnit

/**
 * scrimage와 vips resize throughput을 비교합니다.
 *
 * ## 실행
 * ```bash
 * ./gradlew :bluetape4k-images-benchmark:benchmark
 * ```
 *
 * ## 지표
 * - scrimage: [ImmutableImage.scaleTo] 호출 평균 시간
 * - vips: [VipsImage.resize] 호출 평균 시간. vips를 사용할 수 없으면 skip합니다.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsPrepend = ["--enable-native-access=ALL-UNNAMED"])
@State(Scope.Benchmark)
class ImageResizeBenchmark {

    companion object : KLogging()

    /**
     * target resize resolution입니다. WxH form을 사용합니다.
     *
     * width/height cross product를 만들지 않고 pair를 유지하기 위해 단일 parameter를 사용합니다.
     */
    @Param("1920x1080", "1280x720")
    var resolution: String = "1920x1080"

    private var targetWidth: Int = 1920
    private var targetHeight: Int = 1080

    @Setup
    fun parseResolution() {
        val parts = resolution.split("x")
        targetWidth = parts[0].toInt()
        targetHeight = parts[1].toInt()
    }

    /**
     * natural photo fixture에 대한 scrimage [ImmutableImage.scaleTo] resize throughput을 측정합니다.
     */
    @Benchmark
    fun scrimage_scaleTo(state: VipsBenchmarkState, bh: Blackhole) {
        val resized = BenchmarkImageSets.naturalPhoto(state.imageName).scaleTo(targetWidth, targetHeight)
        bh.consume(resized)
    }

    /**
     * vips [VipsImage.resize] throughput을 측정합니다.
     *
     * 현재 host에서 vips를 사용할 수 없으면 즉시 반환합니다.
     */
    @Benchmark
    fun vips_resize(state: VipsBenchmarkState, bh: Blackhole) {
        if (!state.vipsAvailable) {
            bh.consume(null)
            return
        }
        state.createVipsImage(state.photo4kJpegBytes).use { img ->
            val resized = img.resize(targetWidth, targetHeight)
            bh.consume(resized)
        }
    }
}
