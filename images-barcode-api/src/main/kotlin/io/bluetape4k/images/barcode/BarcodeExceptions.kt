package io.bluetape4k.images.barcode

import io.bluetape4k.support.requireNotBlank
import java.io.Serializable

/**
 * Provider-neutral barcode failure reason.
 */
enum class BarcodeFailureReason {
    /** No barcode was found in the image. */
    NO_BARCODE,

    /** The requested symbology is unsupported by the provider. */
    UNSUPPORTED_FORMAT,

    /** Image input could not be decoded or was malformed. */
    MALFORMED_INPUT,

    /** The provider failed during decoding. */
    DECODE_FAILED,

    /** The provider is unavailable or misconfigured. */
    PROVIDER_UNAVAILABLE,

    /** The operation was cancelled. */
    CANCELLED,

    /** Failure reason is unknown or provider-specific. */
    UNKNOWN,
}

/**
 * Base exception for barcode extraction failures.
 *
 * ## Contract
 * Messages should be sanitized for logs and caller responses. Provider modules
 * may attach the original cause while keeping sensitive paths or payloads out
 * of [message].
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
