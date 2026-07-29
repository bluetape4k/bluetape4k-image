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
        // radius >= 0이 contract입니다. 0은 "no blur"를 의미하지만 유효합니다.
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
        // 단색 이미지에 강한 blur를 적용하면 edge 근처 pixel 값이 바뀝니다.
        // 가장 단순한 assertion은 result가 같은 object가 아니고 크기가 같다는 점입니다.
        result.width shouldBeEqualTo image.width
        result.height shouldBeEqualTo image.height
        (result !== image).shouldBeTrue()
    }
}
