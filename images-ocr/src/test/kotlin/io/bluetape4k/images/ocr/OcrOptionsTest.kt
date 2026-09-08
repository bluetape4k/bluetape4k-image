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
    fun `constructor and copy snapshot mutable OCR collections`() {
        val languages = mutableListOf("eng", "kor")
        val variables = linkedMapOf("tessedit_char_whitelist" to "ABC")
        val configs = mutableListOf("quiet")
        val regions = mutableListOf(OcrRegion(OcrBoundingBox(x = 0, y = 0, width = 100, height = 40), id = "header"))
        val options = OcrOptions(
            languages = languages,
            variables = variables,
            configs = configs,
            regions = regions,
        )

        languages.clear()
        variables.clear()
        configs.clear()
        regions.clear()

        options.languages shouldBeEqualTo listOf("eng", "kor")
        options.variables shouldBeEqualTo mapOf("tessedit_char_whitelist" to "ABC")
        options.configs shouldBeEqualTo listOf("quiet")
        options.regions shouldBeEqualTo listOf(
            OcrRegion(OcrBoundingBox(x = 0, y = 0, width = 100, height = 40), id = "header"),
        )
        options.languageExpression shouldBeEqualTo "eng+kor"

        val copiedLanguages = mutableListOf("jpn")
        val copiedVariables = linkedMapOf("preserve_interword_spaces" to "1")
        val copiedConfigs = mutableListOf("digits")
        val copiedRegions = mutableListOf(OcrRegion(OcrBoundingBox(x = 10, y = 20, width = 30, height = 40)))
        val copied = options.copy(
            languages = copiedLanguages,
            variables = copiedVariables,
            configs = copiedConfigs,
            regions = copiedRegions,
        )

        copiedLanguages.clear()
        copiedVariables.clear()
        copiedConfigs.clear()
        copiedRegions.clear()

        copied.languages shouldBeEqualTo listOf("jpn")
        copied.variables shouldBeEqualTo mapOf("preserve_interword_spaces" to "1")
        copied.configs shouldBeEqualTo listOf("digits")
        copied.regions shouldBeEqualTo listOf(
            OcrRegion(OcrBoundingBox(x = 10, y = 20, width = 30, height = 40)),
        )
        copied.languageExpression shouldBeEqualTo "jpn"
    }

    @Test
    fun `manual value contract keeps source compatibility while changing data reflection`() {
        val options = OcrOptions(
            languages = listOf("eng", "kor"),
            tessdataPath = "/opt/tessdata",
            engineMode = TesseractEngineMode.LSTM_ONLY,
            pageSegmentationMode = TesseractPageSegmentationMode.SINGLE_LINE,
            variables = mapOf("tessedit_char_whitelist" to "ABC"),
            configs = listOf("quiet"),
            trimText = false,
            structuredDetail = OcrStructuredDetail.WORD,
            regions = listOf(OcrRegion(OcrBoundingBox(x = 0, y = 0, width = 100, height = 40))),
        )

        options.component1() shouldBeEqualTo options.languages
        options.component2() shouldBeEqualTo options.tessdataPath
        options.component3() shouldBeEqualTo options.engineMode
        options.component4() shouldBeEqualTo options.pageSegmentationMode
        options.component5() shouldBeEqualTo options.variables
        options.component6() shouldBeEqualTo options.configs
        options.component7() shouldBeEqualTo options.trimText
        options.component8() shouldBeEqualTo options.structuredDetail
        options.component9() shouldBeEqualTo options.regions
        OcrOptions::class.isData shouldBeEqualTo false
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
