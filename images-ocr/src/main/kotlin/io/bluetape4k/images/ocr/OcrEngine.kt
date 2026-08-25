package io.bluetape4k.images.ocr

import com.sksamuel.scrimage.ImmutableImage
import java.io.Serializable

/**
 * [ImmutableImage]에서 text를 인식합니다.
 *
 * ## 동작/계약
 * 구현체는 blocking native OCR library를 호출할 수 있습니다. coroutine boundary가 필요한
 * caller는 `ImmutableImage.suspendExtractText`를 사용해야 합니다.
 */
fun interface OcrEngine {

    /**
     * [options]를 사용해 [image]에서 text를 인식합니다.
     *
     * @param image 검사할 image입니다.
     * @param options OCR option입니다.
     * @return 인식된 OCR text와 실제 적용 option입니다.
     */
    fun recognize(image: ImmutableImage, options: OcrOptions): OcrResult
}

/**
 * [ImmutableImage]에서 plain text와 structured OCR entry를 함께 인식합니다.
 *
 * ## 동작/계약
 * - [recognize]는 source-compatible plain-text surface로 유지됩니다.
 * - [recognizeStructured]는 [OcrOptions.structuredDetail]에 따라 page metadata와 선택적
 *   block, line, word entry를 반환합니다.
 * - confidence 또는 bounding-box data가 없으면 `null`로 유지해야 합니다.
 */
interface StructuredOcrEngine: OcrEngine {

    /**
     * [options]를 사용해 [image]에서 structured OCR content를 인식합니다.
     *
     * @param image 검사할 image입니다.
     * @param options structured detail과 source region을 포함한 OCR option입니다.
     * @return structured OCR result입니다.
     */
    fun recognizeStructured(image: ImmutableImage, options: OcrOptions): OcrStructuredResult
}

/**
 * OCR failure의 base exception입니다.
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
 * native library, tessdata, language-pack 설정 때문에 발생한 OCR failure입니다.
 * public message는 환경 경로를 노출하지 않으며, 진단이 필요한 경우 원본 failure는 [cause]로
 * 보존됩니다.
 */
class OcrConfigurationException(
    message: String,
    cause: Throwable? = null,
): OcrException(message, cause) {
    constructor(message: String) : this(message, null)

    companion object {
        private const val serialVersionUID: Long = 7589750812884482785L
    }
}
