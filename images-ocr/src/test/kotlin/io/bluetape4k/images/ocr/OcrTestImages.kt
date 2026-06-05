package io.bluetape4k.images.ocr

import com.sksamuel.scrimage.ImmutableImage
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage

internal fun textImage(
    text: String = "BLUETAPE OCR 123",
    width: Int = 640,
    height: Int = 160,
): ImmutableImage {
    val buffered = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = buffered.createGraphics()
    try {
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, width, height)
        graphics.color = Color.BLACK
        graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 48)
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        graphics.drawString(text, 48, 96)
    } finally {
        graphics.dispose()
    }
    return ImmutableImage.fromAwt(buffered)
}
