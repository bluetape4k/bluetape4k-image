package io.bluetape4k.images.barcode

import io.bluetape4k.support.requireNotBlank
import java.io.Serializable

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
