package io.bluetape4k.images.ocr

import com.sksamuel.scrimage.ImmutableImage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts text from this image with a blocking [OcrEngine].
 *
 * ## Contract
 * - Uses [TesseractOcrEngine] by default.
 * - Throws [OcrException] when recognition fails.
 *
 * ```kotlin
 * val text = image.extractText(OcrOptions(languages = listOf("eng")))
 * ```
 */
fun ImmutableImage.extractText(
    options: OcrOptions = OcrOptions(),
    engine: OcrEngine = TesseractOcrEngine(),
): String =
    engine.recognize(this, options).text

/**
 * Extracts text from this image on [dispatcher].
 *
 * ## Contract
 * - The blocking OCR call runs inside [withContext].
 * - Cancellation before dispatch prevents the OCR engine from starting.
 *
 * ```kotlin
 * val text = image.suspendExtractText(OcrOptions(languages = listOf("eng", "kor")))
 * ```
 */
suspend fun ImmutableImage.suspendExtractText(
    options: OcrOptions = OcrOptions(),
    engine: OcrEngine = TesseractOcrEngine(),
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
): String =
    withContext(dispatcher) {
        engine.recognize(this@suspendExtractText, options).text
    }
