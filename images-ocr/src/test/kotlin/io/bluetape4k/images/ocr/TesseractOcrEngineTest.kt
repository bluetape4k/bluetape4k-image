package io.bluetape4k.images.ocr

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldContain
import java.awt.Rectangle
import java.awt.image.BufferedImage
import kotlinx.coroutines.CancellationException
import net.sourceforge.tess4j.ITessAPI
import net.sourceforge.tess4j.TesseractException
import org.junit.jupiter.api.Test

class TesseractOcrEngineTest {

    @Test
    fun `recognize configures a fresh Tess4J instance per call`() {
        val instances = mutableListOf<RecordingTesseractClient>()
        val engine = TesseractOcrEngine.withClientFactory {
            RecordingTesseractClient("  configured text  ").also(instances::add)
        }
        val firstOptions = OcrOptions(
            languages = listOf("eng", "kor"),
            tessdataPath = "/opt/tessdata",
            engineMode = TesseractEngineMode.LSTM_ONLY,
            pageSegmentationMode = TesseractPageSegmentationMode.SINGLE_LINE,
            variables = mapOf("tessedit_char_whitelist" to "ABC"),
            configs = listOf("quiet"),
        )

        val first = engine.recognize(textImage(), firstOptions)
        val second = engine.recognize(textImage(), OcrOptions(languages = listOf("jpn"), trimText = false))

        first.text shouldBeEqualTo "configured text"
        second.text shouldBeEqualTo "  configured text  "
        instances.size shouldBeEqualTo 2
        (instances[0] === instances[1]) shouldBeEqualTo false
        instances[0].recordedLanguage shouldBeEqualTo "eng+kor"
        instances[0].recordedDatapath shouldBeEqualTo "/opt/tessdata"
        instances[0].recordedEngineMode shouldBeEqualTo TesseractEngineMode.LSTM_ONLY.value
        instances[0].recordedPageSegmentationMode shouldBeEqualTo TesseractPageSegmentationMode.SINGLE_LINE.value
        instances[0].variables shouldBeEqualTo mapOf("tessedit_char_whitelist" to "ABC")
        instances[0].recordedConfigs shouldBeEqualTo listOf("quiet")
        instances[1].recordedLanguage shouldBeEqualTo "jpn"
    }

    @Test
    fun `TesseractException is wrapped with sanitized OCR message`() {
        val engine = TesseractOcrEngine.withClientFactory {
            RecordingTesseractClient(error = TesseractException("native path /secret/tessdata failed"))
        }

        val error = assertFailsWith<OcrException> {
            engine.recognize(textImage(), OcrOptions(languages = listOf("eng")))
        }

        error.message.orEmpty() shouldContain "Tesseract OCR failed for languages=eng"
        error.message.orEmpty().contains("/secret") shouldBeEqualTo false
    }

    @Test
    fun `factory failure is normalized as configuration error with cause`() {
        val cause = UnsatisfiedLinkError("/secret/libtesseract.so")
        val error = assertFailsWith<OcrConfigurationException> {
            TesseractOcrEngine.withClientFactory { throw cause }
                .recognize(textImage(), OcrOptions(languages = listOf("eng")))
        }

        error.cause shouldBeEqualTo cause
        error.message.orEmpty().contains("/secret") shouldBeEqualTo false
    }

    @Test
    fun `configuration failure is normalized without swallowing cancellation`() {
        val cause = IllegalStateException("/secret/tessdata is missing")
        val configurationError = assertFailsWith<OcrConfigurationException> {
            TesseractOcrEngine.withClientFactory {
                RecordingTesseractClient(configurationError = cause)
            }.recognize(
                textImage(),
                OcrOptions(languages = listOf("eng"), tessdataPath = "/secret/tessdata"),
            )
        }
        configurationError.cause shouldBeEqualTo cause
        configurationError.message.orEmpty().contains("/secret") shouldBeEqualTo false

        val cancellation = CancellationException("caller cancelled")
        assertFailsWith<CancellationException> {
            TesseractOcrEngine.withClientFactory { throw cancellation }
                .recognize(textImage(), OcrOptions(languages = listOf("eng")))
        }

        val ocrCancellation = CancellationException("OCR call cancelled")
        assertFailsWith<CancellationException> {
            TesseractOcrEngine.withClientFactory {
                RecordingTesseractClient(ocrFailure = ocrCancellation)
            }.recognize(textImage(), OcrOptions(languages = listOf("eng")))
        }
    }

    @Test
    fun `recognizeStructured returns requested block line and word entries`() {
        val region = OcrRegion(OcrBoundingBox(x = 0, y = 0, width = 200, height = 100), id = "header")
        val client = RecordingTesseractClient(
            text = "  page text  ",
            wordsByLevel = mapOf(
                ITessAPI.TessPageIteratorLevel.RIL_BLOCK to listOf(
                    TesseractWord(
                        text = "block text",
                        confidence = 88.5,
                        boundingBox = OcrBoundingBox(x = 10, y = 10, width = 120, height = 50),
                    ),
                ),
                ITessAPI.TessPageIteratorLevel.RIL_TEXTLINE to listOf(
                    TesseractWord(
                        text = "line without box",
                        confidence = null,
                        boundingBox = null,
                    ),
                ),
                ITessAPI.TessPageIteratorLevel.RIL_WORD to listOf(
                    TesseractWord(
                        text = "word",
                        confidence = 91.0,
                        boundingBox = OcrBoundingBox(x = 20, y = 15, width = 40, height = 20),
                    ),
                    TesseractWord(
                        text = "outside",
                        confidence = 50.0,
                        boundingBox = OcrBoundingBox(x = 300, y = 15, width = 40, height = 20),
                    ),
                ),
            ),
        )
        val engine = TesseractOcrEngine.withClientFactory { client }
        val options = OcrOptions(
            structuredDetail = OcrStructuredDetail.WORD,
            regions = listOf(region),
        )

        val result = engine.recognizeStructured(textImage(), options)

        result.text shouldBeEqualTo "page text"
        result.pages.single().text shouldBeEqualTo "page text"
        result.blocks.single().sourceRegion shouldBeEqualTo region
        result.blocks.single().confidence shouldBeEqualTo 88.5
        result.lines.size shouldBeEqualTo 0
        result.words.single().text shouldBeEqualTo "word"
        result.words.single().sourceRegion shouldBeEqualTo region
        result.words.single().confidence shouldBeEqualTo 91.0
        client.requestedRegions shouldBeEqualTo listOf(Rectangle(0, 0, 200, 100))
        client.requestedLevels shouldBeEqualTo listOf(
            ITessAPI.TessPageIteratorLevel.RIL_BLOCK,
            ITessAPI.TessPageIteratorLevel.RIL_TEXTLINE,
            ITessAPI.TessPageIteratorLevel.RIL_WORD,
        )
    }

    @Test
    fun `recognizeStructured keeps missing metadata explicit without regions`() {
        val client = RecordingTesseractClient(
            text = "line text",
            wordsByLevel = mapOf(
                ITessAPI.TessPageIteratorLevel.RIL_TEXTLINE to listOf(
                    TesseractWord(
                        text = "line without metadata",
                        confidence = null,
                        boundingBox = null,
                    ),
                ),
            ),
        )
        val engine = TesseractOcrEngine.withClientFactory { client }

        val result = engine.recognizeStructured(
            textImage(),
            OcrOptions(structuredDetail = OcrStructuredDetail.LINE),
        )

        result.blocks.size shouldBeEqualTo 0
        result.lines.single().text shouldBeEqualTo "line without metadata"
        result.lines.single().confidence.shouldBeNull()
        result.lines.single().boundingBox.shouldBeNull()
        result.words.size shouldBeEqualTo 0
    }

    private class RecordingTesseractClient(
        private val text: String = "",
        private val error: TesseractException? = null,
        private val configurationError: RuntimeException? = null,
        private val ocrFailure: RuntimeException? = null,
        private val wordsByLevel: Map<Int, List<TesseractWord>> = emptyMap(),
    ): TesseractClient {

        var recordedDatapath: String? = null
        var recordedLanguage: String? = null
        var recordedEngineMode: Int? = null
        var recordedPageSegmentationMode: Int? = null
        val variables: MutableMap<String, String> = linkedMapOf()
        var recordedConfigs: List<String> = emptyList()
        var requestedRegions: List<Rectangle> = emptyList()
        val requestedLevels: MutableList<Int> = mutableListOf()

        override fun setDatapath(datapath: String) {
            this.recordedDatapath = datapath
        }

        override fun setLanguage(language: String) {
            configurationError?.let { throw it }
            this.recordedLanguage = language
        }

        override fun setOcrEngineMode(ocrEngineMode: Int) {
            this.recordedEngineMode = ocrEngineMode
        }

        override fun setPageSegMode(mode: Int) {
            this.recordedPageSegmentationMode = mode
        }

        override fun setVariable(key: String, value: String) {
            variables[key] = value
        }

        override fun setConfigs(configs: List<String>) {
            this.recordedConfigs = configs.toList()
        }

        override fun doOCR(image: BufferedImage): String {
            error?.let { throw it }
            ocrFailure?.let { throw it }
            return text
        }

        override fun doOCR(image: BufferedImage, regions: List<Rectangle>): String {
            error?.let { throw it }
            ocrFailure?.let { throw it }
            requestedRegions = regions
            return text
        }

        override fun getWords(image: BufferedImage, level: Int): List<TesseractWord> {
            error?.let { throw it }
            ocrFailure?.let { throw it }
            requestedLevels += level
            return wordsByLevel[level].orEmpty()
        }
    }
}
