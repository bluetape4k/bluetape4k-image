package io.bluetape4k.images.captcha

import io.bluetape4k.support.requireNotBlank
import java.awt.Font
import java.io.Serializable

/**
 * CAPTCHA text를 rendering할 때 사용하는 logical JVM font family와 style입니다.
 *
 * 첫 release는 logical JVM font만 사용하며 font binary asset을 bundle하지 않습니다.
 * 따라서 platform file dependency와 license drift를 피합니다.
 *
 * @property family [Font.SANS_SERIF] 같은 logical JVM font family입니다.
 * @property style 내부적으로 AWT [Font] constant에 매핑되는 style입니다.
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
 * [CaptchaFont]가 지원하는 font style 선택지입니다.
 */
enum class CaptchaFontStyle(internal val awtStyle: Int) {
    PLAIN(Font.PLAIN),
    BOLD(Font.BOLD),
    ITALIC(Font.ITALIC),
    BOLD_ITALIC(Font.BOLD or Font.ITALIC),
}
