package io.bluetape4k.images.captcha

import java.time.Clock

/**
 * Java2D 기반 [CaptchaGenerator]를 생성합니다.
 */
fun captchaGenerator(
    clock: Clock = Clock.systemUTC(),
    block: CaptchaOptionsBuilder.() -> Unit = {},
): CaptchaGenerator {
    val options = CaptchaOptionsBuilder().apply(block).build()
    return Java2dCaptchaGenerator(options = options, clock = clock)
}
