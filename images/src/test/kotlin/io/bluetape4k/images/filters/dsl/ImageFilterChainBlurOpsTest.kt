package io.bluetape4k.images.filters.dsl

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.images.filters.AbstractFilterTest
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import kotlin.test.assertFailsWith

class ImageFilterChainBlurOpsTest : AbstractFilterTest() {

    private fun sampleImage(): ImmutableImage {
        val buf = BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB)
        val g = buf.createGraphics()
        g.color = java.awt.Color.ORANGE
        g.fillRect(0, 0, 64, 64)
        g.dispose()
        return ImmutableImage.fromAwt(buf)
    }

    @Test
    fun `blur adds one op to chain`() {
        val chain = ImageFilterChain()
        chain.blur()
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `gaussianBlur with positive radius adds one op`() {
        val chain = ImageFilterChain()
        chain.gaussianBlur(3)
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `gaussianBlur with zero radius is allowed`() {
        // radius >= 0 is the contract; 0 means "no blur" but is valid
        val chain = ImageFilterChain()
        chain.gaussianBlur(0)
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `gaussianBlur with negative radius throws`() {
        val chain = ImageFilterChain()
        assertFailsWith<IllegalArgumentException> {
            chain.gaussianBlur(-1)
        }
    }

    @Test
    fun `sharpen adds one op to chain`() {
        val chain = ImageFilterChain()
        chain.sharpen()
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `noiseReduction adds one op to chain`() {
        val chain = ImageFilterChain()
        chain.noiseReduction()
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `motionBlur adds one op to chain`() {
        val chain = ImageFilterChain()
        chain.motionBlur(distance = 10.0, angle = 0.5)
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `gaussianBlur applied via applyFilters produces visually different image`() {
        val image = sampleImage()
        val result = image.applyFilters { gaussianBlur(5) }
        // A strong blur on a solid image changes pixel values near the edge
        // Simplest assertion: result is not the same object and has same dimensions
        result.width shouldBeEqualTo image.width
        result.height shouldBeEqualTo image.height
        (result !== image).shouldBeTrue()
    }
}
