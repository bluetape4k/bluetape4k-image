package io.bluetape4k.images.barcode

import io.bluetape4k.support.requireNotBlank
import java.io.Serializable

/**
 * provider-neutral barcode failure reason입니다.
 */
enum class BarcodeFailureReason {
    /** 이미지에서 barcode를 찾지 못했습니다. */
    NO_BARCODE,

    /** 요청한 symbology를 provider가 지원하지 않습니다. */
    UNSUPPORTED_FORMAT,

    /** 이미지 입력을 디코딩할 수 없거나 입력이 malformed입니다. */
    MALFORMED_INPUT,

    /** provider가 decoding 중 실패했습니다. */
    DECODE_FAILED,

    /** provider를 사용할 수 없거나 설정이 잘못되었습니다. */
    PROVIDER_UNAVAILABLE,

    /** 작업이 취소되었습니다. */
    CANCELLED,

    /** failure reason이 알 수 없거나 provider-specific입니다. */
    UNKNOWN,
}

/**
 * barcode 추출 실패의 base exception입니다.
 *
 * ## 동작/계약
 * message는 log와 caller response에 안전하도록 정제되어야 합니다. provider module은
 * 민감한 path나 payload가 [message]에 들어가지 않게 하면서 원본 cause를 붙일 수 있습니다.
 */
open class BarcodeException(
    val reason: BarcodeFailureReason,
    message: String,
    cause: Throwable? = null,
): RuntimeException(message, cause), Serializable {

    init {
        message.requireNotBlank("message")
    }

    companion object {
        private const val serialVersionUID: Long = 1632956962849674105L
    }
}
