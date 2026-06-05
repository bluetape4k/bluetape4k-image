package io.bluetape4k.images.ocr

import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.images.immutableImageOf
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty

@EnabledIfSystemProperty(named = "ocr.enabled", matches = "true")
class OcrQuickstartExampleTest {

    @Test
    fun `extracts text from a local image file`() {
        val imageFile = createQuickstartImage("BLUETAPE OCR 123")
        val image = immutableImageOf(imageFile.toFile())

        val text = image.extractText(
            OcrOptions(
                languages = listOf("eng"),
                tessdataPath = defaultTessdataPath(),
                pageSegmentationMode = TesseractPageSegmentationMode.SINGLE_LINE,
                variables = mapOf("tessedit_char_whitelist" to "ABCDEFGHIJKLMNOPQRSTUVWXYZ 0123456789"),
            ),
        )

        val normalized = text.uppercase()
        normalized shouldContain "BLUETAPE"
        normalized shouldContain "OCR"
        normalized shouldContain "123"
    }

    private fun createQuickstartImage(text: String) =
        Files.createTempFile("images-ocr-quickstart-", ".png").also { path ->
            val buffered = BufferedImage(760, 180, BufferedImage.TYPE_INT_RGB)
            val graphics = buffered.createGraphics()
            try {
                graphics.color = Color.WHITE
                graphics.fillRect(0, 0, buffered.width, buffered.height)
                graphics.color = Color.BLACK
                graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 58)
                graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                graphics.drawString(text, 48, 110)
            } finally {
                graphics.dispose()
            }
            ImageIO.write(buffered, "png", path.toFile())
        }
}
