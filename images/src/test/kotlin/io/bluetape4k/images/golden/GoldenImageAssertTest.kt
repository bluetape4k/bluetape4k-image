package io.bluetape4k.images.golden

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.images.immutableImageOf
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.utils.Resourcex
import java.awt.Color
import java.awt.image.BufferedImage
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldContain
import org.junit.jupiter.api.Test
import org.opentest4j.AssertionFailedError
import org.opentest4j.TestAbortedException

/**
 * [GoldenImageAssert] 자기 검증 테스트.
 *
 * 실제 golden fixture를 사용하는 public 비교 계약을 검증합니다.
 */
class GoldenImageAssertTest {

    companion object : KLoggingChannel() {
        private const val GOLDEN_KEY = "resize-320x240"
    }

    private fun solidImage(w: Int, h: Int, color: Color): ImmutableImage {
        val buf = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = buf.createGraphics()
        g.color = color
        g.fillRect(0, 0, w, h)
        g.dispose()
        return ImmutableImage.fromAwt(buf)
    }

    private fun goldenImage(): ImmutableImage =
        immutableImageOf(Resourcex.getBytes("/golden/images/$GOLDEN_KEY.png"))

    private fun imageWithRedDelta(delta: Int): ImmutableImage {
        val image = goldenImage()
        val awt = image.awt()
        val color = Color(awt.getRGB(0, 0), true)
        awt.setRGB(
            0,
            0,
            Color((color.red + delta).coerceIn(0, 255), color.green, color.blue, color.alpha).rgb,
        )
        return ImmutableImage.fromAwt(awt)
    }

    @Test
    fun `존재하지 않는 골든 키는 TestAbortedException으로 skipped 처리된다`() {
        assertFailsWith<TestAbortedException> {
            GoldenImageAssert.assertSimilarToGolden(solidImage(10, 10, Color.RED), "nonexistent-key-xyz")
        }
    }

    @Test
    fun `실제 golden fixture와 동일한 이미지는 public 비교를 통과한다`() {
        GoldenImageAssert.assertSimilarToGolden(goldenImage(), GOLDEN_KEY)
    }

    @Test
    fun `크기가 다른 이미지는 AssertionFailedError를 던진다`() {
        val failure = assertFailsWith<AssertionFailedError> {
            GoldenImageAssert.assertSimilarToGolden(solidImage(10, 10, Color.RED), GOLDEN_KEY)
        }

        failure.message shouldContain "크기 불일치"
    }

    @Test
    fun `tolerance 내 픽셀 차이는 public 비교를 통과한다`() {
        GoldenImageAssert.assertSimilarToGolden(imageWithRedDelta(delta = 1), GOLDEN_KEY, tolerance = 3)
    }

    @Test
    fun `tolerance를 넘는 픽셀 차이는 AssertionError로 실패한다`() {
        val failure = assertFailsWith<AssertionFailedError> {
            GoldenImageAssert.assertSimilarToGolden(imageWithRedDelta(delta = 20), GOLDEN_KEY, tolerance = 3)
        }

        failure.message shouldContain "허용 오차"
    }

}
