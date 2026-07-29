package io.bluetape4k.images.ocr

import com.sksamuel.scrimage.ImmutableImage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * blocking [OcrEngine]으로 이 이미지에서 text를 추출합니다.
 *
 * ## 동작/계약
 * - 기본적으로 [TesseractOcrEngine]을 사용합니다.
 * - recognition이 실패하면 [OcrException]을 던집니다.
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
 * blocking [StructuredOcrEngine]으로 이 이미지에서 structured OCR data를 추출합니다.
 *
 * ## 동작/계약
 * - 기본적으로 [TesseractOcrEngine]을 사용합니다.
 * - [OcrStructuredResult.text]를 통해 [extractText]와 같은 plain text surface를 반환합니다.
 * - block, line, word metadata 양은 [OcrOptions.structuredDetail]를 따릅니다.
 *
 * ```kotlin
 * val result = image.extractOcr(OcrOptions(structuredDetail = OcrStructuredDetail.WORD))
 * ```
 */
fun ImmutableImage.extractOcr(
    options: OcrOptions = OcrOptions(),
    engine: StructuredOcrEngine = TesseractOcrEngine(),
): OcrStructuredResult =
    engine.recognizeStructured(this, options)

/**
 * [dispatcher] 위에서 이 이미지의 text를 추출합니다.
 *
 * ## 동작/계약
 * - blocking OCR call은 [withContext] 안에서 실행됩니다.
 * - dispatch 전에 취소되면 OCR engine이 시작되지 않습니다.
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

/**
 * [dispatcher] 위에서 이 이미지의 structured OCR data를 추출합니다.
 *
 * ## 동작/계약
 * blocking structured OCR call은 [withContext] 안에서 실행됩니다. dispatch 전에 취소되면
 * OCR engine이 시작되지 않습니다.
 *
 * ```kotlin
 * val result = image.suspendExtractOcr(OcrOptions(structuredDetail = OcrStructuredDetail.LINE))
 * ```
 */
suspend fun ImmutableImage.suspendExtractOcr(
    options: OcrOptions = OcrOptions(),
    engine: StructuredOcrEngine = TesseractOcrEngine(),
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
): OcrStructuredResult =
    withContext(dispatcher) {
        engine.recognizeStructured(this@suspendExtractOcr, options)
    }
