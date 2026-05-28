package io.bluetape4k.images.benchmark

import io.bluetape4k.logging.KLogging
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import org.openjdk.jmh.infra.Blackhole
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
