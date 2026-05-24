package io.bluetape4k.images.captcha

import java.time.Clock

/**
 * Creates a Java2D-backed [CaptchaGenerator].
 */
fun captchaGenerator(
    clock: Clock = Clock.systemUTC(),
    block: CaptchaOptionsBuilder.() -> Unit = {},
): CaptchaGenerator {
    val options = CaptchaOptionsBuilder().apply(block).build()
    return Java2dCaptchaGenerator(options = options, clock = clock)
}
