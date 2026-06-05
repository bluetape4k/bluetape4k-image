package io.bluetape4k.images.ocr

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import net.sourceforge.tess4j.ITessAPI
import org.junit.jupiter.api.Test

class OcrOptionsTest {

    @Test
    fun `default options use English Tesseract baseline`() {
        val options = OcrOptions()

        options.languages shouldBeEqualTo listOf("eng")
        options.languageExpression shouldBeEqualTo "eng"
        options.engineMode shouldBeEqualTo TesseractEngineMode.DEFAULT
        options.pageSegmentationMode shouldBeEqualTo TesseractPageSegmentationMode.AUTO
        options.trimText shouldBeEqualTo true
    }

    @Test
    fun `languages are validated and joined for Tess4J`() {
        val options = OcrOptions(languages = listOf("eng", "kor", "jpn"))

        options.languageExpression shouldBeEqualTo "eng+kor+jpn"
        assertFailsWith<IllegalArgumentException> {
            OcrOptions(languages = emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            OcrOptions(languages = listOf("eng", " "))
        }
    }

    @Test
    fun `path variable and config names are validated`() {
        assertFailsWith<IllegalArgumentException> {
            OcrOptions(tessdataPath = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            OcrOptions(variables = mapOf(" " to "1"))
        }
        assertFailsWith<IllegalArgumentException> {
            OcrOptions(configs = listOf("digits", " "))
        }
    }

    @Test
    fun `enum wrappers expose Tess4J constants without leaking callers to ITessAPI`() {
        TesseractEngineMode.DEFAULT.value shouldBeEqualTo ITessAPI.TessOcrEngineMode.OEM_DEFAULT
        TesseractEngineMode.LSTM_ONLY.value shouldBeEqualTo ITessAPI.TessOcrEngineMode.OEM_LSTM_ONLY
        TesseractPageSegmentationMode.AUTO.value shouldBeEqualTo ITessAPI.TessPageSegMode.PSM_AUTO
        TesseractPageSegmentationMode.SINGLE_LINE.value shouldBeEqualTo ITessAPI.TessPageSegMode.PSM_SINGLE_LINE
    }

    @Test
    fun `serializable models round trip`() {
        val result = OcrResult(
            text = "recognized",
            options = OcrOptions(
                languages = listOf("eng", "kor"),
                tessdataPath = "/opt/tessdata",
                variables = mapOf("tessedit_char_whitelist" to "ABC"),
                configs = listOf("quiet"),
            ),
        )

        val restored = roundTrip(result)

        restored shouldBeEqualTo result
        restored.options.languageExpression shouldBeEqualTo "eng+kor"
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> roundTrip(value: T): T {
        val bytes = ByteArrayOutputStream().use { output ->
            ObjectOutputStream(output).use { it.writeObject(value) }
            output.toByteArray()
        }
        return ObjectInputStream(ByteArrayInputStream(bytes)).use { input ->
            input.readObject() as T
        }
    }
}
