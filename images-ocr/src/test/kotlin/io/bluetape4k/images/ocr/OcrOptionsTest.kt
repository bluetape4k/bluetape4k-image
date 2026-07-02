package io.bluetape4k.images.ocr

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.awt.Rectangle
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
        options.structuredDetail shouldBeEqualTo OcrStructuredDetail.PLAIN_TEXT
        options.regions shouldBeEqualTo emptyList()
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
    fun `bounding boxes and regions validate caller supplied geometry`() {
        val box = OcrBoundingBox(x = 10, y = 20, width = 120, height = 40)
        val region = OcrRegion(boundingBox = box, id = "header")

        region.boundingBox.toAwtRectangle() shouldBeEqualTo Rectangle(10, 20, 120, 40)
        box.intersects(region) shouldBeEqualTo true
        OcrBoundingBox.from(Rectangle(0, 0, 0, 10)).shouldBeNull()
        OcrBoundingBox.from(Rectangle(-1, 0, 10, 10)).shouldBeNull()
        assertFailsWith<IllegalArgumentException> {
            OcrBoundingBox(x = 0, y = 0, width = 0, height = 10)
        }
        assertFailsWith<IllegalArgumentException> {
            OcrRegion(boundingBox = box, id = " ")
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
        val options = OcrOptions(
                languages = listOf("eng", "kor"),
                tessdataPath = "/opt/tessdata",
                variables = mapOf("tessedit_char_whitelist" to "ABC"),
                configs = listOf("quiet"),
                structuredDetail = OcrStructuredDetail.WORD,
                regions = listOf(OcrRegion(OcrBoundingBox(x = 0, y = 0, width = 100, height = 40), id = "header")),
        )
        val result = OcrStructuredResult(
            text = "recognized",
            options = options,
            pages = listOf(OcrPage(pageIndex = 0, text = "recognized")),
            words = listOf(
                OcrWord(
                    pageIndex = 0,
                    text = "recognized",
                    boundingBox = null,
                    confidence = null,
                    sourceRegion = null,
                ),
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
