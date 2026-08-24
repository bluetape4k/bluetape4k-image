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
 * hash-pinned OCR corpus v2 fixture로 public Tess4J-backed text extraction path를 측정합니다.
 *
 * fixtureId에 해당하는 v2 manifest의 resource·ground truth·geometry·license receipt 검증,
 * PNG decoding, traineddata check, one-time recognition check는 trial setup 중 실행합니다.
 * `extractText`는 per-call Tesseract engine setup을 포함한 기본 public API를 측정합니다. `preprocessAndExtract`는
 * rotated input에 대한 grayscale preprocessing과 type-normalizing right rotation까지 추가로 측정합니다.
 */
@State(Scope.Benchmark)
@Threads(1)
class TesseractOcrExtractionBenchmark {

    @Param(
        "clean-text-v2-001",
        "clean-v2-002",
        "clean-v2-003",
        "low-resolution-v2-001",
        "low-resolution-v2-002",
        "low-resolution-v2-003",
        "noisy-v2-001",
        "noisy-v2-002",
        "noisy-v2-003",
        "rotated-v2-001",
        "rotated-v2-002",
        "rotated-v2-003",
        "table-v2-001",
        "table-v2-002",
        "table-v2-003",
        "multi-column-v2-001",
        "multi-column-v2-002",
        "multi-column-v2-003",
        "multilingual-v2-001",
        "multilingual-v2-002",
        "multilingual-v2-003",
        "valid-blank-v2-001",
        "valid-blank-v2-002",
        "valid-blank-v2-003",
    )
    lateinit var fixtureId: String

    private lateinit var fixture: OcrBenchmarkCorpusFixture
    private lateinit var options: OcrOptions

    @Setup(Level.Trial)
    fun setup() {
        fixture = OcrBenchmarkCorpusV2.loadFixture(fixtureId)
        require(fixture.entry.expectedOutcome != OcrBenchmarkExpectedOutcome.ERROR) {
            "ERROR OCR fixtures must not be benchmark inputs: $fixtureId"
        }
        OcrBenchmarkEnvironment.requireLanguages(fixture.entry.languages)
        options = OcrOptions(
            languages = fixture.entry.languages,
            tessdataPath = OcrBenchmarkEnvironment.requireTessdataPath(),
        )
        fixture.verifyOutput(fixture.image.extractText(options))
        fixture.verifyOutput(preprocess(fixture).extractText(options))
    }

    @Benchmark
    fun extractText(blackhole: Blackhole) {
        blackhole.consume(fixture.image.extractText(options))
    }

    @Benchmark
    fun preprocessAndExtract(blackhole: Blackhole) {
        blackhole.consume(preprocess(fixture).extractText(options))
    }

    private fun preprocess(source: OcrBenchmarkCorpusFixture) =
        source.image
            .let { image ->
                if (source.entry.scenario == OcrBenchmarkCorpusScenario.ROTATED) normalizeRightRotation(image) else image
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
