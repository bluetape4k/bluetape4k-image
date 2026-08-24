package io.bluetape4k.images.benchmark

import com.sksamuel.scrimage.ImmutableImage
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
import kotlinx.benchmark.State
import kotlinx.benchmark.Warmup
import org.openjdk.jmh.annotations.Fork
import java.util.concurrent.TimeUnit

/**
 * 파생 이미지의 chain/fan-out ownership 비용을 backend별로 비교합니다.
 *
 * 모든 파생 결과는 생성 직후 [use]로 닫습니다. 이 benchmark는 ownership semantics를
 * 바꾸지 않고 FFM의 materialization 비용을 관측하기 위한 것이며, native RSS는 별도
 * receipt runner에서 측정합니다.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsPrepend = ["--enable-native-access=ALL-UNNAMED"])
@State(Scope.Benchmark)
class VipsTransformBenchmark {

    @Param("cafe", "landscape")
    var imageName: String = "cafe"

    @Param("1280x720", "640x480")
    var outputSize: String = "1280x720"

    @Param("3")
    var chainLength: Int = 3

    @Param("4")
    var fanOut: Int = 4

    private lateinit var source: ImmutableImage
    private lateinit var vipsBytes: ByteArray
    private var width: Int = 1280
    private var height: Int = 720

    @org.openjdk.jmh.annotations.Setup
    fun setup() {
        val (parsedWidth, parsedHeight) = outputSize.split('x').map(String::toInt)
        width = parsedWidth
        height = parsedHeight
        source = BenchmarkImageSets.naturalPhoto(imageName)
        vipsBytes = source.bytes(JPEG_WRITER)
    }

    @Benchmark
    fun scrimage_chain(bh: Blackhole) {
        var current = source
        repeat(chainLength) { index ->
            current = when (index % 3) {
                0 -> current.scaleTo(width, height)
                1 -> current.scaleTo((width * 0.75).toInt(), (height * 0.75).toInt())
                else -> current.scaleTo(width, height)
            }
        }
        bh.consume(current.bytes(JPEG_WRITER))
    }

    @Benchmark
    fun scrimage_fanOut(bh: Blackhole) {
        val outputs = List(fanOut) { index ->
            source.scaleTo((width - index * 16).coerceAtLeast(64), (height - index * 16).coerceAtLeast(64))
                .bytes(JPEG_WRITER)
        }
        bh.consume(outputs)
    }

    @Benchmark
    fun vips_chain(state: VipsBenchmarkState, bh: Blackhole) {
        if (!state.vipsAvailable) {
            bh.consume(null)
            return
        }
        state.createVipsImage(vipsBytes).use { sourceImage ->
            var current: VipsImage = sourceImage.resize(width, height)
            try {
                repeat((chainLength - 1).coerceAtLeast(0)) { index ->
                    val next = if (index % 2 == 0) {
                        current.thumbnail((width * 0.75).toInt().coerceAtLeast(64))
                    } else {
                        current.resize(width, height)
                    }
                    current.close()
                    current = next
                }
                bh.consume(current.toBytes().size)
            } finally {
                current.close()
            }
        }
    }

    @Benchmark
    fun vips_fanOut(state: VipsBenchmarkState, bh: Blackhole) {
        if (!state.vipsAvailable) {
            bh.consume(null)
            return
        }
        var outputBytes = 0L
        state.createVipsImage(vipsBytes).use { sourceImage ->
            repeat(fanOut) { index ->
                sourceImage.thumbnail((width - index * 16).coerceAtLeast(64)).use { derived ->
                    outputBytes += derived.toBytes().size
                }
            }
        }
        bh.consume(outputBytes)
    }

    private companion object {
        private val JPEG_WRITER = JpegWriter(80, false)
    }
}
