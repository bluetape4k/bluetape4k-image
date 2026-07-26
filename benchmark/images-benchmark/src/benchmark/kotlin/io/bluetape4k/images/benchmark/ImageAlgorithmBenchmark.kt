package io.bluetape4k.images.benchmark

import io.bluetape4k.images.analysis.dominantColors
import io.bluetape4k.images.similarity.histogramSimilarityTo
import io.bluetape4k.images.similarity.phashDistanceTo
import io.bluetape4k.images.svg.BatikSvgRasterizer
import io.bluetape4k.images.svg.SvgRasterizeOptions
import io.bluetape4k.images.tiles.TileProcessor
import io.bluetape4k.images.tiles.TileSize
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
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

/** Focused measurements for image utilities outside the resize/encode path. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
class ImageAlgorithmBenchmark {

    @Param("photo", "document")
    var fixture: String = "photo"

    private lateinit var image: com.sksamuel.scrimage.ImmutableImage
    private lateinit var comparison: com.sksamuel.scrimage.ImmutableImage

    @Setup
    fun setup() {
        image = if (fixture == "photo") BenchmarkImageSets.photo4k else BenchmarkImageSets.document
        comparison = image.scaleTo(512, 512)
    }

    @Benchmark
    fun crop(bh: Blackhole) {
        val width = minOf(image.width, 1024)
        val height = minOf(image.height, 768)
        bh.consume(image.subimage(0, 0, width, height))
    }

    @Benchmark
    fun tileSplit(bh: Blackhole) {
        bh.consume(TileProcessor(maxTileCount = 256).split(image, TileSize(512, 512)))
    }

    @Benchmark
    fun dominantColors(bh: Blackhole) {
        bh.consume(image.dominantColors(count = 5))
    }

    @Benchmark
    fun histogramSimilarity(bh: Blackhole) {
        bh.consume(image.histogramSimilarityTo(comparison))
    }

    @Benchmark
    fun phashDistance(bh: Blackhole) {
        bh.consume(image.phashDistanceTo(comparison))
    }

    @Benchmark
    fun svgRasterize(bh: Blackhole) {
        val rendered = runBlocking {
            BatikSvgRasterizer().rasterize(
                ByteArrayInputStream(SVG_FIXTURE.toByteArray()),
                SvgRasterizeOptions(width = 512, height = 512),
            )
        }
        bh.consume(rendered)
    }

    private companion object {
        private val SVG_FIXTURE = """
            <svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024">
              <rect width="1024" height="1024" fill="#eef2f7"/>
              <circle cx="512" cy="512" r="320" fill="#276ef1"/>
              <path d="M256 700 L768 700 L512 220 Z" fill="#f59e0b"/>
            </svg>
        """.trimIndent()
    }
}
