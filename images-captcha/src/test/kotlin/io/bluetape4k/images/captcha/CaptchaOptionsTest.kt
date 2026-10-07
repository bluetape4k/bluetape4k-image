package io.bluetape4k.images.captcha

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeSameInstanceAs
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.assertions.shouldNotContain
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test
import java.awt.Color
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlin.time.Duration.Companion.minutes

class CaptchaOptionsTest {

    companion object: KLogging()

    @Test
    fun `default options use readable bounded values`() {
        val options = CaptchaOptions()

        log.debug { "default options=$options" }
        options.length shouldBeEqualTo 6
        options.charSet shouldNotContain "I"
        options.charSet shouldNotContain "O"
        options.charSet shouldNotContain "0"
        options.charSet shouldNotContain "1"
        options.imageSize shouldBeEqualTo CaptchaImageSize(200, 80)
        options.expiresAfter shouldBeEqualTo 5.minutes
    }

    @Test
    fun `length is bounded`() {
        assertFailsWith<IllegalArgumentException> {
            CaptchaOptions(length = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            CaptchaOptions(length = 33)
        }
    }

    @Test
    fun `font size is bounded`() {
        assertFailsWith<IllegalArgumentException> {
            CaptchaOptions(fontSize = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            CaptchaOptions(fontSize = 513)
        }
    }

    @Test
    fun `character set rejects unusable characters`() {
        assertFailsWith<IllegalArgumentException> {
            CaptchaOptions(charSet = "A")
        }
        assertFailsWith<IllegalArgumentException> {
            CaptchaOptions(charSet = "AB ")
        }
        assertFailsWith<IllegalArgumentException> {
            CaptchaOptions(charSet = "AB\n")
        }
    }

    @Test
    fun `colors must keep visible contrast from background`() {
        assertFailsWith<IllegalArgumentException> {
            CaptchaOptions(textColors = emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            CaptchaOptions(textColors = listOf(Color(0, 0, 0, 0)))
        }
        assertFailsWith<IllegalArgumentException> {
            CaptchaOptions(backgroundColor = Color.WHITE, textColors = listOf(Color.WHITE))
        }
        assertFailsWith<IllegalArgumentException> {
            CaptchaOptions(
                backgroundColor = Color.WHITE,
                textColors = listOf(Color.WHITE, Color(0, 0, 0, 0)),
            )
        }
    }

    @Test
    fun `builder produces immutable options value`() {
        val options = CaptchaOptionsBuilder()
            .length(4)
            .charSet("ABCD")
            .imageSize(120, 48)
            .fontSize(22)
            .noise(CaptchaNoise.Low)
            .distortion(CaptchaDistortion.Wave(0.25f))
            .backgroundColor(Color(250, 250, 250))
            .textColors(Color.BLUE)
            .expiresAfter(3.minutes)
            .fonts(CaptchaFont())
            .build()

        log.debug { "options=$options" }

        options.length shouldBeEqualTo 4
        options.charSet shouldBeEqualTo "ABCD"
        options.imageSize shouldBeEqualTo CaptchaImageSize(120, 48)
        options.fontSize shouldBeEqualTo 22
        options.noise shouldBeEqualTo CaptchaNoise.Low
        options.distortion shouldBeEqualTo CaptchaDistortion.Wave(0.25f)
        options.expiresAfter shouldBeEqualTo 3.minutes
        options.fonts shouldBeEqualTo listOf(CaptchaFont())
    }

    @Test
    fun `constructor and copy snapshot mutable collections`() {
        val colors = mutableListOf(Color.BLACK, Color.BLUE)
        val fonts = mutableListOf(CaptchaFont("Dialog", CaptchaFontStyle.PLAIN))
        val options = CaptchaOptions(textColors = colors, fonts = fonts)

        colors.clear()
        fonts.clear()

        options.textColors shouldBeEqualTo listOf(Color.BLACK, Color.BLUE)
        options.fonts shouldBeEqualTo listOf(CaptchaFont("Dialog", CaptchaFontStyle.PLAIN))

        val copiedColors = mutableListOf(Color.RED, Color.BLUE)
        val copied = options.copy(textColors = copiedColors)
        copiedColors.clear()

        copied.textColors shouldBeEqualTo listOf(Color.RED, Color.BLUE)
        copied.fonts shouldBeEqualTo options.fonts
    }

    @Test
    fun `manual value contract keeps source compatibility while changing data reflection`() {
        val options = CaptchaOptions()

        log.debug { "options=$options" }
        options.component1() shouldBeEqualTo options.length
        options.component2() shouldBeEqualTo options.charSet
        options.component3() shouldBeEqualTo options.imageSize
        options.component4() shouldBeEqualTo options.fontSize
        options.component5() shouldBeEqualTo options.noise
        options.component6() shouldBeEqualTo options.distortion
        options.component7() shouldBeEqualTo options.backgroundColor
        options.component8() shouldBeEqualTo options.textColors
        options.component9() shouldBeEqualTo options.expiresAfter
        options.component10() shouldBeEqualTo options.fonts

        CaptchaOptions::class.isData.shouldBeFalse()
    }

    @Test
    fun `jdk serializable options round trip keeps duration and singleton options`() {
        val options = CaptchaOptions(
            expiresAfter = 3.minutes,
            noise = CaptchaNoise.Low,
            distortion = CaptchaDistortion.None,
        )

        val restored = roundTrip(options).shouldNotBeNull()

        restored shouldBeEqualTo options
        restored.expiresAfter shouldBeEqualTo 3.minutes
        restored.noise shouldBeSameInstanceAs CaptchaNoise.Low
        restored.distortion shouldBeSameInstanceAs CaptchaDistortion.None
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T: Any> roundTrip(value: T): T? {
        val bytes = ByteArrayOutputStream().use { output ->
            ObjectOutputStream(output).use { it.writeObject(value) }
            output.toByteArray()
        }
        return ObjectInputStream(ByteArrayInputStream(bytes)).use { input ->
            input.readObject() as? T
        }
    }
}
