package io.bluetape4k.images.benchmark

import com.sksamuel.scrimage.filter.GrayscaleFilter
import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.images.ocr.OcrOptions
import io.bluetape4k.images.ocr.extractText
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Threads
import java.awt.image.BufferedImage

/**
 * Measures the public Tess4J-backed text extraction path with hash-pinned document fixtures.
 *
 * Fixture resource loading, PNG decoding, traineddata checks, and a one-time
 * recognition check run during trial setup. `extractText` measures the default
 * public API, including its per-call Tesseract engine setup. `preprocessAndExtract`
 * additionally measures grayscale preprocessing and a type-normalizing right rotation
 * for the rotated input.
 */
@State(Scope.Benchmark)
@Threads(1)
class TesseractOcrExtractionBenchmark {

    @Param("clean-text", "noisy-scan", "rotated-document", "multilingual-text")
    lateinit var scenario: String

    private lateinit var fixture: OcrBenchmarkFixture
    private lateinit var options: OcrOptions

    @Setup(Level.Trial)
    fun setup() {
        val benchmarkScenario = OcrBenchmarkScenario.entries.single { it.value == scenario }
        fixture = OcrBenchmarkFixtures.load(benchmarkScenario)
        OcrBenchmarkEnvironment.requireLanguages(fixture.entry.languages)
        options = OcrOptions(
            languages = fixture.entry.languages,
            tessdataPath = OcrBenchmarkEnvironment.requireTessdataPath(),
        )
        fixture.verify(fixture.image.extractText(options))
    }

    @Benchmark
    fun extractText(blackhole: Blackhole) {
        blackhole.consume(fixture.image.extractText(options))
    }

    @Benchmark
    fun preprocessAndExtract(blackhole: Blackhole) {
        blackhole.consume(preprocess(fixture).extractText(options))
    }

    private fun preprocess(source: OcrBenchmarkFixture) =
        source.image
            .let { image ->
                if (source.entry.scenario == OcrBenchmarkScenario.ROTATED_DOCUMENT) normalizeRightRotation(image) else image
            }
            .filter(GrayscaleFilter())

    private fun normalizeRightRotation(image: ImmutableImage): ImmutableImage {
        val source = image.awt()
        val normalized = BufferedImage(source.height, source.width, BufferedImage.TYPE_INT_RGB)
        val graphics = normalized.createGraphics()
        try {
            graphics.translate(source.height.toDouble(), 0.0)
            graphics.rotate(Math.PI / 2.0)
            graphics.drawImage(source, 0, 0, null)
        } finally {
            graphics.dispose()
        }
        return ImmutableImage.fromAwt(normalized)
    }
}
