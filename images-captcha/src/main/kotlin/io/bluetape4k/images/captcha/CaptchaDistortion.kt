package io.bluetape4k.images.captcha

import io.bluetape4k.support.requireInRange
import java.io.Serializable

/**
 * text와 noise rendering 후 적용되는 선택적 bounded distortion입니다.
 */
sealed interface CaptchaDistortion: Serializable {

    data object None: CaptchaDistortion {
        private fun readResolve(): Any = None
    }

    /**
     * 수평 wave distortion입니다.
     *
     * @property strength `0.0f..1.0f` 범위의 distortion strength입니다.
     */
    data class Wave(
        val strength: Float,
    ): CaptchaDistortion {

        init {
            strength.requireInRange(0.0f, 1.0f, "strength")
        }

        companion object {
            private const val serialVersionUID: Long = -2378188398676685507L
        }
    }
}
