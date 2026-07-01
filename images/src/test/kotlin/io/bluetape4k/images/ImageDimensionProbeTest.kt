package io.bluetape4k.images

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotBeNull
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class ImageDimensionProbeTest {

    @Test
    fun `probeImageDimensions reads dimensions from encoded bytes without full decode`() {
        val dimensions = probeImageDimensions(pngBytes(width = 120, height = 80))

        dimensions.shouldNotBeNull()
        dimensions.width shouldBeEqualTo 120
        dimensions.height shouldBeEqualTo 80
        dimensions.pixelCount shouldBeEqualTo 9_600L
    }

    @Test
    fun `requireMaxPixels rejects oversized decoded pixel count`() {
        val dimensions = ImageDimensions(width = 120, height = 80)

        val error = assertFailsWith<IllegalArgumentException> {
            dimensions.requireMaxPixels(maxPixels = 1_000L, subject = "upload")
        }

        error.message shouldContain "decodedPixels=9600"
        error.message shouldContain "maxInputPixels=1000"
    }

    @Test
    fun `requireMaxSide rejects oversized decoded width or height`() {
        val dimensions = ImageDimensions(width = 9_000, height = 100)

        val error = assertFailsWith<IllegalArgumentException> {
            dimensions.requireMaxSide(maxSide = 8_192, subject = "upload")
        }

        error.message shouldContain "decodedDimensions=9000x100"
        error.message shouldContain "maxInputSide=8192"
    }

    private fun pngBytes(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = Color(42, 120, 220)
            graphics.fillRect(0, 0, width, height)
        } finally {
            graphics.dispose()
        }

        return ByteArrayOutputStream().use { output ->
            ImageIO.write(image, "png", output)
            output.toByteArray()
        }
    }
}
