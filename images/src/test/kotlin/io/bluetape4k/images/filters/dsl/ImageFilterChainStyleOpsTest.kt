package io.bluetape4k.images.filters.dsl

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.images.filters.AbstractFilterTest
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage

class ImageFilterChainStyleOpsTest : AbstractFilterTest() {

    private fun sampleImage(): ImmutableImage {
        val buf = BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB)
        val g = buf.createGraphics()
        g.color = java.awt.Color(200, 150, 100)
        g.fillRect(0, 0, 64, 64)
        g.dispose()
        return ImmutableImage.fromAwt(buf)
    }

    @Test
    fun `sepia adds one op`() {
        val chain = ImageFilterChain()
        chain.sepia()
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `grayscale adds one op`() {
        val chain = ImageFilterChain()
        chain.grayscale()
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `invert adds one op`() {
        val chain = ImageFilterChain()
        chain.invert()
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `vintage adds one op`() {
        val chain = ImageFilterChain()
        chain.vintage()
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `chrome adds one op`() {
        val chain = ImageFilterChain()
        chain.chrome()
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `nashville adds one op`() {
        val chain = ImageFilterChain()
        chain.nashville()
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `gotham adds one op`() {
        val chain = ImageFilterChain()
        chain.gotham()
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `summer adds one op`() {
        val chain = ImageFilterChain()
        chain.summer()
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `oldPhoto adds one op`() {
        val chain = ImageFilterChain()
        chain.oldPhoto()
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `applyFilters with grayscale produces different image`() {
        val image = sampleImage()
        val result = image.applyFilters { grayscale() }
        // grayscale은 R, G, B를 바꾸므로 결과가 컬러 원본과 달라집니다.
        assertNotSimilarToImage(result, image, threshold = 5)
    }

    @Test
    fun `multiple style ops accumulate in chain`() {
        val chain = ImageFilterChain()
        chain.sepia()
        chain.invert()
        chain.vintage()
        chain.build().size shouldBeEqualTo 3
    }
}
