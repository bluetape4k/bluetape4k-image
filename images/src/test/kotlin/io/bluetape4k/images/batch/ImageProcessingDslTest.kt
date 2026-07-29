package io.bluetape4k.images.batch

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.images.AbstractImageTest
import io.bluetape4k.images.coroutines.SuspendJpegWriter
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import kotlin.test.assertFailsWith

class ImageProcessingDslTest : AbstractImageTest() {

    private fun sampleImage(width: Int = 200, height: Int = 150): ImmutableImage {
        val buf = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = buf.createGraphics()
        g.color = java.awt.Color.CYAN
        g.fillRect(0, 0, width, height)
        g.dispose()
        return ImmutableImage.fromAwt(buf)
    }

    @Test
    fun `empty DSL pipeline applies no transform and writer is null`() {
        val dsl = ImageProcessingDsl()
        val image = sampleImage()

        val result = dsl.apply(image)

        // transform이 없으므로 result는 original dimension과 같아야 합니다.
        result.width shouldBeEqualTo image.width
        result.height shouldBeEqualTo image.height
        dsl.selectedWriter() shouldBeEqualTo null
    }

    @Test
    fun `resize sets dimensions on output image`() {
        val dsl = ImageProcessingDsl()
        dsl.resize(100, 75)

        val result = dsl.apply(sampleImage(200, 150))

        result.width shouldBeEqualTo 100
        result.height shouldBeEqualTo 75
    }

    @Test
    fun `resize with positive values does not throw`() {
        val dsl = ImageProcessingDsl()
        dsl.resize(50, 50)
        dsl.selectedWriter() shouldBeEqualTo null  // no writer chosen yet
    }

    @Test
    fun `resize with zero width throws IllegalArgumentException`() {
        val dsl = ImageProcessingDsl()
        assertFailsWith<IllegalArgumentException> {
            dsl.resize(0, 100)
        }
    }

    @Test
    fun `resize with negative height throws IllegalArgumentException`() {
        val dsl = ImageProcessingDsl()
        assertFailsWith<IllegalArgumentException> {
            dsl.resize(100, -1)
        }
    }

    @Test
    fun `toJpeg selects a writer`() {
        val dsl = ImageProcessingDsl()
        dsl.toJpeg(quality = 80)

        dsl.selectedWriter().shouldNotBeNull()
    }

    @Test
    fun `toJpeg with out-of-range quality throws`() {
        val dsl = ImageProcessingDsl()
        assertFailsWith<IllegalArgumentException> {
            dsl.toJpeg(quality = 101)
        }
    }

    @Test
    fun `toJpeg with negative quality throws`() {
        val dsl = ImageProcessingDsl()
        assertFailsWith<IllegalArgumentException> {
            dsl.toJpeg(quality = -1)
        }
    }

    @Test
    fun `calling toJpeg twice throws IllegalStateException`() {
        val dsl = ImageProcessingDsl()
        dsl.toJpeg(quality = 80)
        assertFailsWith<IllegalStateException> {
            dsl.toJpeg(quality = 70)
        }
    }

    @Test
    fun `writer function sets a writer`() {
        val dsl = ImageProcessingDsl()
        dsl.writer(SuspendJpegWriter.Default)

        dsl.selectedWriter().shouldNotBeNull()
    }

    @Test
    fun `writer called twice throws IllegalStateException`() {
        val dsl = ImageProcessingDsl()
        dsl.writer(SuspendJpegWriter.Default)
        assertFailsWith<IllegalStateException> {
            dsl.writer(SuspendJpegWriter.Default)
        }
    }

    @Test
    fun `chained resize and fit produce correct output`() {
        val dsl = ImageProcessingDsl()
        dsl.resize(100, 100)
        dsl.toJpeg(quality = 85)

        val result = dsl.apply(sampleImage(200, 150))

        result.width shouldBeEqualTo 100
        result.height shouldBeEqualTo 100
        dsl.selectedWriter().shouldNotBeNull()
    }

    @Test
    fun `gaussianBlur accepts positive radius`() {
        val dsl = ImageProcessingDsl()
        dsl.gaussianBlur(radius = 3)
        // 예외가 발생하지 않아야 하며 apply는 result를 생성해야 합니다.
        val result = dsl.apply(sampleImage())
        result.shouldNotBeNull()
    }

    @Test
    fun `gaussianBlur with zero radius throws`() {
        val dsl = ImageProcessingDsl()
        assertFailsWith<IllegalArgumentException> {
            dsl.gaussianBlur(radius = 0)
        }
    }

    @Test
    fun `watermark with negative alpha throws`() {
        val dsl = ImageProcessingDsl()
        val logo = sampleImage(20, 20)
        assertFailsWith<IllegalArgumentException> {
            dsl.watermark(logo = logo, alpha = -0.1f)
        }
    }

    @Test
    fun `watermark with alpha greater than 1 throws`() {
        val dsl = ImageProcessingDsl()
        val logo = sampleImage(20, 20)
        assertFailsWith<IllegalArgumentException> {
            dsl.watermark(logo = logo, alpha = 1.1f)
        }
    }
}
