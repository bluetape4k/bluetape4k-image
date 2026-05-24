package io.bluetape4k.images.captcha

import io.bluetape4k.support.requireNotBlank
import java.awt.Font
import java.io.Serializable

/**
 * Logical JVM font family and style used while rendering CAPTCHA text.
 *
 * The first release uses logical JVM fonts only and does not bundle font binary
 * assets, avoiding platform file dependencies and license drift.
 *
 * @property family logical JVM font family such as [Font.SANS_SERIF]
 * @property style style mapped to an AWT [Font] constant internally
 */
data class CaptchaFont(
    val family: String = Font.SANS_SERIF,
    val style: CaptchaFontStyle = CaptchaFontStyle.BOLD,
): Serializable {

    init {
        family.requireNotBlank("family")
    }

    internal fun toAwtFont(size: Int): Font =
        Font(family, style.awtStyle, size)

    companion object {
        private const val serialVersionUID: Long = -5195555983661228167L

        @JvmStatic
        fun defaults(): List<CaptchaFont> =
            listOf(
                CaptchaFont(Font.SANS_SERIF, CaptchaFontStyle.BOLD),
                CaptchaFont(Font.SERIF, CaptchaFontStyle.BOLD),
                CaptchaFont(Font.MONOSPACED, CaptchaFontStyle.BOLD),
            )
    }
}

/**
 * Font style choices supported by [CaptchaFont].
 */
enum class CaptchaFontStyle(internal val awtStyle: Int) {
    PLAIN(Font.PLAIN),
    BOLD(Font.BOLD),
    ITALIC(Font.ITALIC),
    BOLD_ITALIC(Font.BOLD or Font.ITALIC),
}
