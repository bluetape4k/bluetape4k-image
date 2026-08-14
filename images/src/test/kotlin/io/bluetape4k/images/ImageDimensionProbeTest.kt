package io.bluetape4k.images

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotBeNull
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.imageio.IIOException

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
    fun `probeImageDimensionsDetailed reports successful header probe`() {
        val result = probeImageDimensionsDetailed(pngBytes(width = 120, height = 80))

        val success = result.shouldBeInstanceOf<ImageDimensionProbeResult.Success>()
        success.dimensions shouldBeEqualTo ImageDimensions(width = 120, height = 80)
    }

    @Test
    fun `probeImageDimensionsDetailed distinguishes unavailable and malformed input`() {
        probeImageDimensionsDetailed(ByteArray(32) { 0x7F.toByte() }) shouldBeEqualTo
            ImageDimensionProbeResult.Unavailable

        val truncatedPng = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
        )
        val malformed = probeImageDimensionsDetailed(truncatedPng)
            .shouldBeInstanceOf<ImageDimensionProbeResult.Malformed>()
        malformed.cause.shouldBeInstanceOf<IIOException>()
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
