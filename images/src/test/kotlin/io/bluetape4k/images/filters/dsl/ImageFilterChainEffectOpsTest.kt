package io.bluetape4k.images.filters.dsl

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.images.filters.AbstractFilterTest
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage

class ImageFilterChainEffectOpsTest : AbstractFilterTest() {

    private fun sampleImage(): ImmutableImage {
        val buf = BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB)
        val g = buf.createGraphics()
        g.color = java.awt.Color.MAGENTA
        g.fillRect(0, 0, 64, 64)
        g.dispose()
        return ImmutableImage.fromAwt(buf)
    }

    @Test
    fun `oil adds one op`() {
        val chain = ImageFilterChain()
        chain.oil()
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `crystallize adds one op`() {
        val chain = ImageFilterChain()
        chain.crystallize()
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `pixelate with valid blockSize adds one op`() {
        val chain = ImageFilterChain()
        chain.pixelate(8)
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `pixelate with blockSize zero throws`() {
        val chain = ImageFilterChain()
        assertFailsWith<IllegalArgumentException> {
            chain.pixelate(0)
        }
    }

    @Test
    fun `medianBlur adds one op`() {
        val chain = ImageFilterChain()
        chain.medianBlur(2)
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `border with positive thickness adds one op`() {
        val chain = ImageFilterChain()
        chain.border(3)
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `border with negative thickness throws`() {
        val chain = ImageFilterChain()
        assertFailsWith<IllegalArgumentException> {
            chain.border(-1)
        }
    }

    @Test
    fun `vignette adds one op`() {
        val chain = ImageFilterChain()
        chain.vignette()
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `glow adds one op`() {
        val chain = ImageFilterChain()
        chain.glow()
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `lensFlare adds one op`() {
        val chain = ImageFilterChain()
        chain.lensFlare()
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `roundedCorners with positive radius adds one op`() {
        val chain = ImageFilterChain()
        chain.roundedCorners(10)
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `roundedCorners with negative radius throws`() {
        val chain = ImageFilterChain()
        assertFailsWith<IllegalArgumentException> {
            chain.roundedCorners(-1)
        }
    }

    @Test
    fun `applyFilters with pixelate produces different image`() {
        val image = sampleImage()
        val result = image.applyFilters { pixelate(16) }
        (result !== image).shouldBeTrue()
        result.width shouldBeEqualTo image.width
    }
}
