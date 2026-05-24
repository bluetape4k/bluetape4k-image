package io.bluetape4k.images.captcha

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeSameInstanceAs
import java.awt.Color
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

class CaptchaOptionsTest {

    @Test
    fun `default options use readable bounded values`() {
        val options = CaptchaOptions()

        options.length shouldBeEqualTo 6
        options.charSet.contains('I') shouldBeEqualTo false
        options.charSet.contains('O') shouldBeEqualTo false
        options.charSet.contains('0') shouldBeEqualTo false
        options.charSet.contains('1') shouldBeEqualTo false
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
    fun `serializable options round trip keeps duration and singleton options`() {
        val options = CaptchaOptions(
            expiresAfter = 3.minutes,
            noise = CaptchaNoise.Low,
            distortion = CaptchaDistortion.None,
        )

        val restored = roundTrip(options)

        restored shouldBeEqualTo options
        restored.expiresAfter shouldBeEqualTo 3.minutes
        restored.noise shouldBeSameInstanceAs CaptchaNoise.Low
        restored.distortion shouldBeSameInstanceAs CaptchaDistortion.None
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> roundTrip(value: T): T {
        val bytes = ByteArrayOutputStream().use { output ->
            ObjectOutputStream(output).use { it.writeObject(value) }
            output.toByteArray()
        }
        return ObjectInputStream(ByteArrayInputStream(bytes)).use { input ->
            input.readObject() as T
        }
    }
}
