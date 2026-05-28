package io.bluetape4k.images.captcha

/**
 * Generates CAPTCHA image challenges.
 *
 * Use [CaptchaVerificationService] when applications need a reusable one-shot
 * verification contract for generated challenges. Applications still own the
 * durable storage backend and rate limiting policy.
 */
interface CaptchaGenerator {

    /**
     * Rendering and text-generation options used by this generator.
     */
    val options: CaptchaOptions

    /**
     * Generates a CAPTCHA challenge.
     *
     * @param length effective text length for this call; defaults to
     * [CaptchaOptions.length] and is validated per call
     * @return generated challenge text and image
     */
    fun generate(length: Int = options.length): CaptchaChallenge

    /**
     * Generates a CAPTCHA challenge from a coroutine.
     *
     * Cancellation is honored before CPU-bound Java2D rendering starts. Mid-render
     * cancellation is not guaranteed because Java2D drawing is non-suspending.
     *
     * @param length effective text length for this call
     * @return generated challenge text and image
     */
    suspend fun generateSuspend(length: Int = options.length): CaptchaChallenge
}
