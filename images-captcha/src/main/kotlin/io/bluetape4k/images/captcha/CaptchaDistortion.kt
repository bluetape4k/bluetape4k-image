package io.bluetape4k.images.captcha

import io.bluetape4k.support.requireInRange
import java.io.Serializable

/**
 * Optional bounded distortion applied after text and noise rendering.
 */
sealed interface CaptchaDistortion: Serializable {

    data object None: CaptchaDistortion {
        private fun readResolve(): Any = None
    }

    /**
     * Horizontal wave distortion.
     *
     * @property strength distortion strength in `0.0f..1.0f`
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
