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
 * Measures ZXing extraction from one immutable image that was decoded during trial setup.
 *
 * The `benchmarkBarcodeLatencyBenchmark` task reports average milliseconds per
 * extraction (lower is better). The `benchmarkBarcodeThroughputBenchmark` task
 * reports extractions per second (higher is better). PNG loading, image decode,
 * fixture validation, and reader construction stay outside the timed method.
 * ZXing's current single-result provider path is measured for QR, Code 128, and
 * an image with no barcode result.
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
