package io.bluetape4k.images.filters.dsl

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.images.filters.AbstractFilterTest
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage

class ImageColorSpaceExtensionsTest : AbstractFilterTest() {

    private fun solidColorImage(r: Int, g: Int, b: Int, w: Int = 4, h: Int = 4): ImmutableImage {
        val buf = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val graphics = buf.createGraphics()
        graphics.color = java.awt.Color(r, g, b)
        graphics.fillRect(0, 0, w, h)
        graphics.dispose()
        return ImmutableImage.fromAwt(buf)
    }

    @Test
    fun `toHsvArray returns array of size pixels times 3`() {
        val image = solidColorImage(200, 100, 50, 4, 4)
        val hsv = image.toHsvArray()
        hsv.size shouldBeEqualTo image.width * image.height * 3
    }

    @Test
    fun `toHsvArray has hue component in 0 to 360 range`() {
        // ColorSpaceConverter.rgbToHsvInto stores H in degrees: H ∈ [0, 360)
        val image = solidColorImage(200, 100, 50, 4, 4)
        val hsv = image.toHsvArray()
        // H is at indices 0, 3, 6, ...
        val hues = (hsv.indices step 3).map { hsv[it] }
        hues.forEach { h ->
            (h >= 0f && h < 360f).let { valid ->
                if (!valid) throw AssertionError("H=$h is out of [0, 360)")
            }
        }
    }

    @Test
    fun `toHsvArray for pure red pixel has expected hue near 0 degrees`() {
        val image = solidColorImage(255, 0, 0, 1, 1)
        val hsv = image.toHsvArray()
        // For pure red: H ≈ 0° (or 360° wrapped), S = 1, V = 1
        val h = hsv[0]
        (h < 18f || h > 342f).let { isRedHue ->
            if (!isRedHue) throw AssertionError("Expected hue near 0° for red, got $h")
        }
    }

    @Test
    fun `toYCbCrArray returns array of size pixels times 3`() {
        val image = solidColorImage(100, 150, 200, 4, 4)
        val ycbcr = image.toYCbCrArray()
        ycbcr.size shouldBeEqualTo image.width * image.height * 3
    }

    @Test
    fun `toYCbCrArray has Y component greater than zero for non-black pixel`() {
        val image = solidColorImage(200, 150, 100, 2, 2)
        val ycbcr = image.toYCbCrArray()
        val yValues = (ycbcr.indices step 3).map { ycbcr[it] }
        yValues.forEach { y ->
            if (y <= 0f) throw AssertionError("Y component should be positive for non-black pixel, got $y")
        }
    }

    @Test
    fun `toYCbCrArray for grayscale image has Cb and Cr near neutral`() {
        // For a gray pixel (128, 128, 128) Cb and Cr should be near 128
        val image = solidColorImage(128, 128, 128, 1, 1)
        val ycbcr = image.toYCbCrArray()
        val cb = ycbcr[1]
        val cr = ycbcr[2]
        // Cb, Cr near 128 (neutral) — allow ±5 tolerance
        (kotlin.math.abs(cb - 128f) < 5f).let { if (!it) throw AssertionError("Cb=$cb not near 128") }
        (kotlin.math.abs(cr - 128f) < 5f).let { if (!it) throw AssertionError("Cr=$cr not near 128") }
    }
}
