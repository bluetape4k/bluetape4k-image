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
 * Common libvips geometry benchmark used for backend-to-backend comparison.
 *
 * Run this class twice with the same parameters:
 *
 * ```bash
 * ./gradlew :bluetape4k-images-benchmark:benchmarkBenchmark -Pvips.impl=java21
 * ./gradlew :bluetape4k-images-benchmark:benchmarkBenchmark -Pvips.impl=java25
 * ```
 *
 * The Gradle property swaps the runtime backend while the benchmark method names
 * remain stable, so the generated JSON can be joined side by side by backend.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsPrepend = ["--enable-native-access=ALL-UNNAMED"])
@State(Scope.Benchmark)
class VipsBackendBenchmark {

    companion object : KLogging()

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

    @Benchmark
    fun vips_resize(state: VipsBenchmarkState, bh: Blackhole) {
        if (!state.vipsAvailable) {
            bh.consume(null)
            return
        }
        state.createVipsImage(state.photo4kJpegBytes).use { img ->
            img.resize(targetWidth, targetHeight).use { resized ->
                bh.consume(resized.width)
                bh.consume(resized.height)
            }
        }
    }

    @Benchmark
    fun vips_thumbnail(state: VipsBenchmarkState, bh: Blackhole) {
        if (!state.vipsAvailable) {
            bh.consume(null)
            return
        }
        state.createVipsImage(state.photo4kJpegBytes).use { img ->
            img.thumbnail(targetWidth).use { thumbnail ->
                bh.consume(thumbnail.width)
                bh.consume(thumbnail.height)
            }
        }
    }

    @Benchmark
    fun vips_crop(state: VipsBenchmarkState, bh: Blackhole) {
        if (!state.vipsAvailable) {
            bh.consume(null)
            return
        }
        state.createVipsImage(state.photo4kJpegBytes).use { img ->
            img.crop(0, 0, targetWidth, targetHeight).use { cropped ->
                bh.consume(cropped.width)
                bh.consume(cropped.height)
            }
        }
    }

}
