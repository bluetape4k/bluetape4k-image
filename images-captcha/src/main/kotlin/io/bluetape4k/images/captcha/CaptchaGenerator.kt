package io.bluetape4k.images.captcha

/**
 * CAPTCHA image challenge를 생성합니다.
 *
 * application이 생성된 challenge에 재사용 가능한 one-shot verification contract가 필요하면
 * [CaptchaVerificationService]를 사용합니다. durable storage backend와 rate limiting
 * policy는 계속 application 책임입니다.
 */
interface CaptchaGenerator {

    /**
     * 이 generator가 사용하는 rendering 및 text-generation option입니다.
     */
    val options: CaptchaOptions

    /**
     * CAPTCHA challenge를 생성합니다.
     *
     * @param length 이 호출에 적용할 실제 text length입니다. 기본값은 [CaptchaOptions.length]이고 호출마다 검증됩니다.
     * @return 생성된 challenge text와 image입니다.
     */
    fun generate(length: Int = options.length): CaptchaChallenge

    /**
     * coroutine에서 CAPTCHA challenge를 생성합니다.
     *
     * CPU-bound Java2D rendering이 시작되기 전 cancellation을 반영합니다. Java2D drawing은
     * non-suspending이므로 rendering 중간 cancellation은 보장하지 않습니다.
     *
     * @param length 이 호출에 적용할 실제 text length입니다.
     * @return 생성된 challenge text와 image입니다.
     */
    suspend fun generateSuspend(length: Int = options.length): CaptchaChallenge
}
