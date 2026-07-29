package io.bluetape4k.images.benchmark

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.images.barcode.BarcodeOptions
import io.bluetape4k.images.barcode.BarcodeResult
import io.bluetape4k.images.barcode.zxing.ZxingBarcodeReader
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Threads

/**
 * trial setup 중 decode된 immutable image 하나에서 ZXing extraction을 측정합니다.
 *
 * `benchmarkBarcodeLatencyBenchmark` task는 extraction당 평균 millisecond를 보고합니다(낮을수록 좋음).
 * `benchmarkBarcodeThroughputBenchmark` task는 초당 extraction 수를 보고합니다(높을수록 좋음). PNG loading,
 * image decode, fixture validation, reader construction은 timed method 밖에 둡니다. ZXing의 현재 single-result
 * provider path를 QR, Code 128, barcode result가 없는 image에 대해 측정합니다.
 */
@State(Scope.Benchmark)
@Threads(1)
class ZxingBarcodeExtractionBenchmark {

    @Param("qr", "code-128", "no-result")
    var scenario: String = BarcodeBenchmarkScenario.QR.value

    private lateinit var reader: ZxingBarcodeReader
    private lateinit var image: ImmutableImage
    private lateinit var options: BarcodeOptions

    @Setup(Level.Trial)
    fun setup() {
        val fixture = BarcodeBenchmarkFixtures.load(
            BarcodeBenchmarkScenario.entries.single { it.value == scenario },
        )
        reader = ZxingBarcodeReader()
        image = fixture.image
        options = fixture.options()
        fixture.verify(reader.readBarcodes(image, options))
    }

    @Benchmark
    fun extractBarcodes(): List<BarcodeResult> =
        reader.readBarcodes(image, options)
}
