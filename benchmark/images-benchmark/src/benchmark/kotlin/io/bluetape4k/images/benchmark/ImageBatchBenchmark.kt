package io.bluetape4k.images.benchmark

import com.sksamuel.scrimage.nio.JpegWriter
import io.bluetape4k.images.vips.VipsImage
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.Warmup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

/** Batch thumbnail and fan-out measurements for CPU-bound image pipelines. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
class ImageBatchBenchmark {

    @Param("1", "4", "8")
    var fixtureCount: Int = 1

    private lateinit var fixtures: List<com.sksamuel.scrimage.ImmutableImage>

    @Setup
    fun setup() {
        val source = listOf(BenchmarkImageSets.photo4k, BenchmarkImageSets.cafe)
        fixtures = List(fixtureCount) { source[it % source.size] }
    }

    @Benchmark
    fun scrimage_thumbnailFanout(bh: Blackhole) {
        bh.consume(fixtures.flatMap { image ->
            THUMBNAIL_SIZES.map { size -> image.scaleTo(size, size) }
        })
    }

    @Benchmark
    fun scrimage_batchSequential(bh: Blackhole) {
        bh.consume(fixtures.map { it.scaleTo(640, 480).bytes(JPEG_WRITER) })
    }

    @Benchmark
    fun scrimage_batchBoundedConcurrency(bh: Blackhole) {
        val outputs = runBlocking {
            coroutineScope {
                fixtures.map { image ->
                    async(Dispatchers.Default.limitedParallelism(2)) {
                        image.scaleTo(640, 480).bytes(JPEG_WRITER)
                    }
                }.awaitAll()
            }
        }
        bh.consume(outputs)
    }

    @Benchmark
    fun vips_thumbnailFanout(state: VipsBenchmarkState, bh: Blackhole) {
        if (!state.vipsAvailable) {
            bh.consume(null)
            return
        }
        var outputCount = 0
        repeat(fixtureCount) {
            state.createVipsImage(state.photo4kJpegBytes).use { image ->
                THUMBNAIL_SIZES.forEach { size ->
                    image.thumbnail(size).use { outputCount++ }
                }
            }
        }
        bh.consume(outputCount)
    }

    private companion object {
        private val THUMBNAIL_SIZES = intArrayOf(320, 640, 1280)
        private val JPEG_WRITER = JpegWriter(80, false)
    }
}
