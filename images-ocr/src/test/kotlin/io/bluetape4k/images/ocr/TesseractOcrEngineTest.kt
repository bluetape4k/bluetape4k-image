package io.bluetape4k.images.ocr

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import java.awt.image.BufferedImage
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

    private class RecordingTesseractClient(
        private val text: String = "",
        private val error: TesseractException? = null,
    ): TesseractClient {

        var recordedDatapath: String? = null
        var recordedLanguage: String? = null
        var recordedEngineMode: Int? = null
        var recordedPageSegmentationMode: Int? = null
        val variables: MutableMap<String, String> = linkedMapOf()
        var recordedConfigs: List<String> = emptyList()

        override fun setDatapath(datapath: String) {
            this.recordedDatapath = datapath
        }

        override fun setLanguage(language: String) {
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
            return text
        }
    }
}
