package io.bluetape4k.images.ocr

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty

@EnabledIfSystemProperty(named = "ocr.enabled", matches = "true")
class TesseractNativeOcrTest {

    @Test
    fun `native Tesseract extracts English text from generated image`() {
        val text = textImage("BLUETAPE OCR 123").extractText(
            OcrOptions(
                languages = listOf("eng"),
                pageSegmentationMode = TesseractPageSegmentationMode.SINGLE_LINE,
                variables = mapOf("tessedit_char_whitelist" to "ABCDEFGHIJKLMNOPQRSTUVWXYZ 0123456789"),
            ),
        )

        text.uppercase() shouldContain "BLUETAPE"
        text.uppercase() shouldContain "OCR"
        text.uppercase() shouldContain "123"
    }

    @Test
    fun `native Tesseract language packs include expected multilingual baseline`() {
        val output = ProcessBuilder("tesseract", "--list-langs")
            .redirectErrorStream(true)
            .start()
            .let { process ->
                val text = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                exitCode shouldBeEqualTo 0
                text
            }

        output shouldContain "eng"
        output shouldContain "kor"
        output shouldContain "jpn"
    }
}
