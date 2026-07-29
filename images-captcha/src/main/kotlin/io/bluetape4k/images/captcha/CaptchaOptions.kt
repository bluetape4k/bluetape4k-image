package io.bluetape4k.images.captcha

import io.bluetape4k.support.requireInRange
import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requireNotEmpty
import io.bluetape4k.support.requirePositiveNumber
import java.awt.Color
import java.io.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * CAPTCHA image challenge 생성에 사용하는 option입니다.
 *
 * 기본값은 uppercase character만 사용하고 모호한 `I`, `O`, `0`, `1`을 제외해
 * 사용자 입력 실수를 줄입니다.
 */
data class CaptchaOptions(
    val length: Int = DEFAULT_LENGTH,
    val charSet: String = DEFAULT_CHAR_SET,
    val imageSize: CaptchaImageSize = CaptchaImageSize(),
    val fontSize: Int = DEFAULT_FONT_SIZE,
    val noise: CaptchaNoise = CaptchaNoise.Medium,
    val distortion: CaptchaDistortion = CaptchaDistortion.None,
    val backgroundColor: Color = Color.WHITE,
    val textColors: List<Color> = listOf(Color.DARK_GRAY),
    val expiresAfter: Duration = DEFAULT_EXPIRES_AFTER,
    val fonts: List<CaptchaFont> = CaptchaFont.defaults(),
): Serializable {

    init {
        validateLength(length)
        validateCharSet(charSet)
        fontSize.requireInRange(MIN_FONT_SIZE, MAX_FONT_SIZE, "fontSize")
        textColors.requireNotEmpty("textColors")
        require(textColors.any { it.isVisible() }) { "textColors must include a visible color" }
        require(textColors.any { it.isVisible() && !it.hasSameRgb(backgroundColor) }) {
            "textColors must include a visible color different from backgroundColor"
        }
        require(expiresAfter > Duration.ZERO) { "expiresAfter must be greater than zero" }
        fonts.requireNotEmpty("fonts")
    }

    companion object {
        private const val serialVersionUID: Long = -5633101925229847169L

        const val DEFAULT_LENGTH: Int = 6
        const val DEFAULT_CHAR_SET: String = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        const val DEFAULT_FONT_SIZE: Int = 36
        val DEFAULT_EXPIRES_AFTER: Duration = 5.minutes

        const val MIN_LENGTH: Int = 1
        const val MAX_LENGTH: Int = 32
        const val MIN_FONT_SIZE: Int = 1
        const val MAX_FONT_SIZE: Int = 512

        internal fun validateLength(length: Int) {
            length.requireInRange(MIN_LENGTH, MAX_LENGTH, "length")
        }

        internal fun validateCharSet(charSet: String) {
            charSet.requireNotBlank("charSet")
            require(charSet.toSet().size >= 2) { "charSet must contain at least two distinct characters" }
            require(charSet.all { it.isPrintableCaptchaChar() }) {
                "charSet must contain only printable non-whitespace BMP characters"
            }
        }

        private fun Char.isPrintableCaptchaChar(): Boolean =
            !isISOControl() && !isSurrogate() && !isWhitespace()

        private fun Color.isVisible(): Boolean =
            alpha > 0

        private fun Color.hasSameRgb(other: Color): Boolean =
            red == other.red && green == other.green && blue == other.blue
    }
}

/**
 * CAPTCHA image dimension입니다.
 */
data class CaptchaImageSize(
    val width: Int = DEFAULT_WIDTH,
    val height: Int = DEFAULT_HEIGHT,
): Serializable {

    init {
        width.requireInRange(MIN_SIZE, MAX_SIZE, "width")
        height.requireInRange(MIN_SIZE, MAX_SIZE, "height")
    }

    companion object {
        private const val serialVersionUID: Long = 8531479865717372133L

        const val DEFAULT_WIDTH: Int = 200
        const val DEFAULT_HEIGHT: Int = 80
        const val MIN_SIZE: Int = 1
        const val MAX_SIZE: Int = 2_000
    }
}
