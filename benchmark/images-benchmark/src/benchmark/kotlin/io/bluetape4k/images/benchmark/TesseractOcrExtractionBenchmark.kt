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
 * hash-pinned document fixture로 public Tess4J-backed text extraction path를 측정합니다.
 *
 * fixture resource loading, PNG decoding, traineddata check, one-time recognition check는 trial setup 중 실행합니다.
 * `extractText`는 per-call Tesseract engine setup을 포함한 기본 public API를 측정합니다. `preprocessAndExtract`는
 * rotated input에 대한 grayscale preprocessing과 type-normalizing right rotation까지 추가로 측정합니다.
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
