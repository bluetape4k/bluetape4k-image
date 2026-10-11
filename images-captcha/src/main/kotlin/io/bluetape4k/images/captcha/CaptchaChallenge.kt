package io.bluetape4k.images.captcha

import com.sksamuel.scrimage.ImmutableImage
import java.time.Instant

/**
 * 생성된 CAPTCHA text와 image입니다.
 *
 * [ImmutableImage]는 안정적인 Java serialization payload가 아니므로 이 class는 의도적으로
 * data class도 아니고 serializable도 아닙니다. challenge를 저장해야 한다면 인코딩된
 * image byte와 application-owned metadata를 저장합니다.
 *
 * @property text 사용자가 입력해야 하는 정답 text입니다.
 * @property image rendering된 challenge image입니다.
 * @property expiresAt application storage가 참고할 만료 시각입니다.
 */
class CaptchaChallenge(
    val text: String,
    val image: ImmutableImage,
    val expiresAt: Instant,
)
