package io.bluetape4k.images

import io.bluetape4k.support.requirePositiveNumber
import java.io.Serializable

/**
 * 외부에서 전달된 인코딩 이미지 바이트를 디코딩할 때 적용할 리소스 한계입니다.
 *
 * 사용자가 제어하는 payload를 Scrimage decoder에 넘기기 전에, 한계가 적용되는
 * `immutableImageOf` overload와 함께 사용합니다.
 */
data class ImageDecodeLimits(
    val maxEncodedBytes: Long = DEFAULT_MAX_ENCODED_BYTES,
    val maxDecodedPixels: Long = DEFAULT_MAX_DECODED_PIXELS,
    val maxDecodedSide: Int = DEFAULT_MAX_DECODED_SIDE,
) : Serializable {

    init {
        maxEncodedBytes.requirePositiveNumber("maxEncodedBytes")
        maxDecodedPixels.requirePositiveNumber("maxDecodedPixels")
        maxDecodedSide.requirePositiveNumber("maxDecodedSide")
    }

    companion object {
        private const val serialVersionUID: Long = 1L

        /** HTTP와 파일 업로드 경계에서 사용하는 기본 인코딩 입력 한계입니다. */
        const val DEFAULT_MAX_ENCODED_BYTES: Long = 10L * 1024L * 1024L

        /** 4096 x 4096에 해당하는 기본 디코딩 픽셀 예산입니다. */
        const val DEFAULT_MAX_DECODED_PIXELS: Long = 16_777_216L

        /** 디코딩된 이미지의 한 변에 허용하는 기본 최댓값입니다. */
        const val DEFAULT_MAX_DECODED_SIDE: Int = 8_192

        /** 신뢰하지 않는 업로드형 입력에 사용할 보수적인 기본값입니다. */
        val ExternalInput: ImageDecodeLimits = ImageDecodeLimits()
    }
}
