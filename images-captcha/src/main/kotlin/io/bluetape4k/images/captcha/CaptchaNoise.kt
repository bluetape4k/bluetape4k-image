package io.bluetape4k.images.captcha

import io.bluetape4k.support.requireInRange
import java.io.Serializable

/**
 * Noise level applied to generated CAPTCHA images.
 */
sealed interface CaptchaNoise: Serializable {

    val lines: Int
    val dots: Int

    data object None: CaptchaNoise {
        override val lines: Int = 0
        override val dots: Int = 0

        private fun readResolve(): Any = None
    }

    data object Low: CaptchaNoise {
        override val lines: Int = 2
        override val dots: Int = 20

        private fun readResolve(): Any = Low
    }

    data object Medium: CaptchaNoise {
        override val lines: Int = 4
        override val dots: Int = 40

        private fun readResolve(): Any = Medium
    }

    data object High: CaptchaNoise {
        override val lines: Int = 8
        override val dots: Int = 80

        private fun readResolve(): Any = High
    }

    /**
     * Custom bounded line and dot noise counts.
     */
    data class Custom(
        override val lines: Int,
        override val dots: Int,
    ): CaptchaNoise {

        init {
            lines.requireInRange(0, MAX_NOISE_COUNT, "lines")
            dots.requireInRange(0, MAX_NOISE_COUNT, "dots")
        }

        companion object {
            private const val serialVersionUID: Long = -3007144775925711153L
        }
    }

    companion object {
        const val MAX_NOISE_COUNT: Int = 500
    }
}
