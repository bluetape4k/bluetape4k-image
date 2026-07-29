package io.bluetape4k.images.captcha

import java.awt.Color
import kotlin.time.Duration

/**
 * [CaptchaOptions] builder입니다.
 */
class CaptchaOptionsBuilder {

    private var length: Int = CaptchaOptions.DEFAULT_LENGTH
    private var charSet: String = CaptchaOptions.DEFAULT_CHAR_SET
    private var imageSize: CaptchaImageSize = CaptchaImageSize()
    private var fontSize: Int = CaptchaOptions.DEFAULT_FONT_SIZE
    private var noise: CaptchaNoise = CaptchaNoise.Medium
    private var distortion: CaptchaDistortion = CaptchaDistortion.None
    private var backgroundColor: Color = Color.WHITE
    private var textColors: List<Color> = listOf(Color.DARK_GRAY)
    private var expiresAfter: Duration = CaptchaOptions.DEFAULT_EXPIRES_AFTER
    private var fonts: List<CaptchaFont> = CaptchaFont.defaults()

    fun length(value: Int): CaptchaOptionsBuilder = apply {
        length = value
    }

    fun charSet(value: String): CaptchaOptionsBuilder = apply {
        charSet = value
    }

    fun imageSize(width: Int, height: Int): CaptchaOptionsBuilder = apply {
        imageSize = CaptchaImageSize(width, height)
    }

    fun fontSize(value: Int): CaptchaOptionsBuilder = apply {
        fontSize = value
    }

    fun noise(value: CaptchaNoise): CaptchaOptionsBuilder = apply {
        noise = value
    }

    fun distortion(value: CaptchaDistortion): CaptchaOptionsBuilder = apply {
        distortion = value
    }

    fun backgroundColor(value: Color): CaptchaOptionsBuilder = apply {
        backgroundColor = value
    }

    fun textColors(vararg values: Color): CaptchaOptionsBuilder = apply {
        textColors = values.toList()
    }

    fun textColors(values: Iterable<Color>): CaptchaOptionsBuilder = apply {
        textColors = values.toList()
    }

    fun expiresAfter(value: Duration): CaptchaOptionsBuilder = apply {
        expiresAfter = value
    }

    fun fonts(vararg values: CaptchaFont): CaptchaOptionsBuilder = apply {
        fonts = values.toList()
    }

    fun fonts(values: Iterable<CaptchaFont>): CaptchaOptionsBuilder = apply {
        fonts = values.toList()
    }

    fun build(): CaptchaOptions =
        CaptchaOptions(
            length = length,
            charSet = charSet,
            imageSize = imageSize,
            fontSize = fontSize,
            noise = noise,
            distortion = distortion,
            backgroundColor = backgroundColor,
            textColors = textColors,
            expiresAfter = expiresAfter,
            fonts = fonts,
        )
}
