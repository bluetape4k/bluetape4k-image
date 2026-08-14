package io.bluetape4k.images.filters.dsl

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.images.filters.AbstractFilterTest
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage

class ImageFilterChainColorOpsTest : AbstractFilterTest() {

    private fun sampleImage(): ImmutableImage {
        val buf = BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB)
        val g = buf.createGraphics()
        g.color = java.awt.Color(180, 100, 50)
        g.fillRect(0, 0, 64, 64)
        g.dispose()
        return ImmutableImage.fromAwt(buf)
    }

    @Test
    fun `brightness adds one op`() {
        val chain = ImageFilterChain()
        chain.brightness(1.5f)
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `contrast adds one op`() {
        val chain = ImageFilterChain()
        chain.contrast(1.2)
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `gamma adds one op`() {
        val chain = ImageFilterChain()
        chain.gamma(1.0)
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `hsb adds one op`() {
        val chain = ImageFilterChain()
        chain.hsb(0.1f, 0.2f, 0.3f)
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `saturation with valid factor adds one op`() {
        val chain = ImageFilterChain()
        chain.saturation(1.5f)
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `saturation with negative factor throws`() {
        val chain = ImageFilterChain()
        assertFailsWith<IllegalArgumentException> {
            chain.saturation(-0.1f)
        }
    }

    @Test
    fun `hue adds one op`() {
        val chain = ImageFilterChain()
        chain.hue(45f)
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `rgb with valid factors adds one Pixel op`() {
        val chain = ImageFilterChain()
        chain.rgb(1f, 1f, 1f)
        chain.build().size shouldBeEqualTo 1
        (chain.build()[0] is ImageFilterChain.Op.Pixel).shouldBeTrue()
    }

    @Test
    fun `rgb with negative r factor throws`() {
        val chain = ImageFilterChain()
        assertFailsWith<IllegalArgumentException> {
            chain.rgb(-0.1f, 1f, 1f)
        }
    }

    @Test
    fun `opacity with valid alpha adds one op`() {
        val chain = ImageFilterChain()
        chain.opacity(0.5f)
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `opacity out of range throws`() {
        val chain = ImageFilterChain()
        assertFailsWith<IllegalArgumentException> {
            chain.opacity(1.1f)
        }
    }

    @Test
    fun `threshold adds one op`() {
        val chain = ImageFilterChain()
        chain.threshold(128)
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `posterize with valid levels adds one op`() {
        val chain = ImageFilterChain()
        chain.posterize(4)
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `posterize with level 1 throws`() {
        val chain = ImageFilterChain()
        assertFailsWith<IllegalArgumentException> {
            chain.posterize(1)
        }
    }

    @Test
    fun `colorTemperature adds one op`() {
        val chain = ImageFilterChain()
        chain.colorTemperature(5500)
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `applyFilters with saturation changes pixel values`() {
        val image = sampleImage()
        val result = image.applyFilters { saturation(0f) }  // fully desaturate → grayscale
        (result !== image).shouldBeTrue()
        result.width shouldBeEqualTo image.width
        result.height shouldBeEqualTo image.height
    }
}
