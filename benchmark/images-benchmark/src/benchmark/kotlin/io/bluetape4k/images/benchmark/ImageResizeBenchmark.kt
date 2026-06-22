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
 * Compares scrimage and vips resize throughput.
 *
 * ## Run
 * ```bash
 * ./gradlew :bluetape4k-images-benchmark:benchmark
 * ```
 *
 * ## Metrics
 * - scrimage: average [ImmutableImage.scaleTo] call time
 * - vips: average [VipsImage.resize] call time, skipped when vips is unavailable
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
     * Target resize resolution in WxH form.
     *
     * A single parameter keeps width and height paired instead of creating a cross product.
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
     * Measures scrimage [ImmutableImage.scaleTo] resize throughput for a natural photo fixture.
     */
    @Benchmark
    fun scrimage_scaleTo(state: VipsBenchmarkState, bh: Blackhole) {
        val resized = BenchmarkImageSets.naturalPhoto(state.imageName).scaleTo(targetWidth, targetHeight)
        bh.consume(resized)
    }

    /**
     * Measures vips [VipsImage.resize] throughput.
     *
     * Returns immediately when vips is unavailable on the current host.
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
