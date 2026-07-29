package io.bluetape4k.images.similarity

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.images.AbstractImageTest
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage

class SimilarityScaleUtilsTest : AbstractImageTest() {

    private fun makeImage(width: Int, height: Int): ImmutableImage {
        val buf = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        return ImmutableImage.fromAwt(buf)
    }

    @Test
    fun `prepareForSimilarity returns same image when already within maxSide`() {
        val image = makeImage(200, 100)
        val result = image.prepareForSimilarity(512)
        // resize가 필요 없으므로 같은 object여야 합니다.
        result shouldBeEqualTo image
    }

    @Test
    fun `prepareForSimilarity scales down large image to fit maxSide`() {
        val image = makeImage(1024, 768)
        val result = image.prepareForSimilarity(512)
        (result.width <= 512).shouldBeTrue()
        (result.height <= 512).shouldBeTrue()
    }

    @Test
    fun `prepareForSimilarity preserves aspect ratio`() {
        val image = makeImage(1000, 500)  // 2:1 ratio
        val result = image.prepareForSimilarity(512)
        // 너비는 높이의 약 두 배여야 합니다.
        val ratio = result.width.toDouble() / result.height.toDouble()
        (ratio > 1.9 && ratio < 2.1).let { valid ->
            if (!valid) throw AssertionError("Expected ratio ~2 but got $ratio")
        }
    }

    @Test
    fun `prepareForSimilarity with default maxSide 512 scales 4K image`() {
        val image = makeImage(3840, 2160)  // 4K image
        val result = image.prepareForSimilarity()  // default 512
        (result.width <= 512).shouldBeTrue()
        (result.height <= 512).shouldBeTrue()
    }

    @Test
    fun `prepareForSimilarity on small image returns original`() {
        val image = makeImage(100, 100)
        val result = image.prepareForSimilarity(512)
        result.width shouldBeEqualTo 100
        result.height shouldBeEqualTo 100
    }
}
