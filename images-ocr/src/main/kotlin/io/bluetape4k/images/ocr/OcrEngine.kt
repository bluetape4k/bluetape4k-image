package io.bluetape4k.images.ocr

import com.sksamuel.scrimage.ImmutableImage
import java.io.Serializable

/**
 * Recognizes text from an [ImmutableImage].
 *
 * ## Contract
 * Implementations may call blocking native OCR libraries. Callers that need a
 * coroutine boundary should use `ImmutableImage.suspendExtractText`.
 */
fun interface OcrEngine {

    /**
     * Recognizes text from [image] using [options].
     *
     * @param image image to inspect
     * @param options OCR options
     * @return recognized OCR text and effective options
     */
    fun recognize(image: ImmutableImage, options: OcrOptions): OcrResult
}

/**
 * Base exception for OCR failures.
 */
open class OcrException(
    message: String,
    cause: Throwable? = null,
): RuntimeException(message, cause), Serializable {
    companion object {
        private const val serialVersionUID: Long = -2921342777424838470L
    }
}

/**
 * OCR failure caused by native library, tessdata, or language-pack setup.
 */
class OcrConfigurationException(
    message: String,
    cause: Throwable? = null,
): OcrException(message, cause) {
    companion object {
        private const val serialVersionUID: Long = 7589750812884482785L
    }
}
