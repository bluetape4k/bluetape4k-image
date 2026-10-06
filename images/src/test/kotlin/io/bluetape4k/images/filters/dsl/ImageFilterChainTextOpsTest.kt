package io.bluetape4k.images.filters.dsl

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.images.filters.AbstractFilterTest
import io.bluetape4k.images.useGraphics
import io.bluetape4k.logging.KLogging
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage

class ImageFilterChainTextOpsTest: AbstractFilterTest() {

    companion object: KLogging()

    private fun sampleImage(width: Int = 128, height: Int = 96): ImmutableImage {
        val buf = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        buf.useGraphics { g ->
            g.color = java.awt.Color.DARK_GRAY
            g.fillRect(0, 0, width, height)
        }
        return ImmutableImage.fromAwt(buf)
    }

    @Test
    fun `watermark adds one op to chain`() {
        val chain = ImageFilterChain()
        chain.watermark("Hello")
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `watermarkAt adds one op to chain`() {
        val chain = ImageFilterChain()
        chain.watermarkAt("Copyright", x = 10, y = 10)
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `caption adds one op to chain`() {
        val chain = ImageFilterChain()
        chain.caption("My Caption")
        chain.build().size shouldBeEqualTo 1
    }

    @Test
    fun `combining watermark and caption accumulates ops`() {
        val chain = ImageFilterChain()
        chain.watermark("mark")
        chain.caption("caption text")
        chain.build().size shouldBeEqualTo 2
    }

    @Test
    fun `applyFilters with watermark produces image of same dimensions`() {
        val image = sampleImage()
        val result = image.applyFilters { watermark("Test") }
        result.width shouldBeEqualTo image.width
        result.height shouldBeEqualTo image.height
        (result !== image).shouldBeTrue()
    }

    @Test
    fun `applyFilters with caption produces image of same dimensions`() {
        val image = sampleImage()
        val result = image.applyFilters { caption("Footer text") }
        result.width shouldBeEqualTo image.width
        result.height shouldBeEqualTo image.height
    }
}
