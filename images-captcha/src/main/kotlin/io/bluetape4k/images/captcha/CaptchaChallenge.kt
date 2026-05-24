package io.bluetape4k.images.captcha

import com.sksamuel.scrimage.ImmutableImage
import java.time.Instant

/**
 * Generated CAPTCHA text and image.
 *
 * This class intentionally is not a data class and is not serializable because
 * [ImmutableImage] is not a stable Java serialization payload. Persist encoded
 * image bytes and application-owned metadata when a challenge must be stored.
 *
 * @property text answer text that the user must provide
 * @property image rendered challenge image
 * @property expiresAt advisory expiration instant for application storage
 */
class CaptchaChallenge(
    val text: String,
    val image: ImmutableImage,
    val expiresAt: Instant,
)
