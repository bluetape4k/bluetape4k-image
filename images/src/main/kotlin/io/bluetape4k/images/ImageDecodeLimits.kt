package io.bluetape4k.images

import io.bluetape4k.support.requirePositiveNumber
import java.io.Serializable

/**
 * Resource limits for decoding externally supplied encoded image bytes.
 *
 * Use this value with the bounded `immutableImageOf` overloads before handing
 * user-controlled payloads to Scrimage decoders.
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

        /** Default encoded input limit for HTTP and file-upload boundaries. */
        const val DEFAULT_MAX_ENCODED_BYTES: Long = 10L * 1024L * 1024L

        /** Default decoded pixel budget, equivalent to 4096 x 4096. */
        const val DEFAULT_MAX_DECODED_PIXELS: Long = 16_777_216L

        /** Default maximum decoded width or height. */
        const val DEFAULT_MAX_DECODED_SIDE: Int = 8_192

        /** Conservative defaults for untrusted upload-style inputs. */
        val ExternalInput: ImageDecodeLimits = ImageDecodeLimits()
    }
}
