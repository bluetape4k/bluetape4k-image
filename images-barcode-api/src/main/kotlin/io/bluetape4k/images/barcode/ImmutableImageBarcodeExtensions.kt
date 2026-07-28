package io.bluetape4k.images.barcode

import com.sksamuel.scrimage.ImmutableImage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [reader]로 이 이미지에서 barcode를 추출합니다.
 *
 * ## 동작/계약
 * 이 함수는 blocking 추출 surface입니다. [BarcodeReader.readBarcodes]에 직접 위임한 뒤
 * [BarcodeOptions.filter]를 적용하므로, provider module은 raw provider order를 반환하고
 * caller는 요청한 view를 받을 수 있습니다.
 *
 * ```kotlin
 * val results = image.extractBarcodes(reader, BarcodeOptions(formats = setOf(BarcodeFormat.QR_CODE)))
 * ```
 */
fun ImmutableImage.extractBarcodes(
    reader: BarcodeReader,
    options: BarcodeOptions = BarcodeOptions(),
): List<BarcodeResult> =
    options.filter(reader.readBarcodes(this, options))

/**
 * [dispatcher] 위에서 이 이미지의 barcode를 추출합니다.
 *
 * ## 동작/계약
 * provider 호출은 [withContext] 안에서 실행됩니다. dispatch 전에 취소되면 reader가
 * 시작되지 않고, provider가 던진 cancellation은 변경 없이 전파됩니다.
 */
suspend fun ImmutableImage.suspendExtractBarcodes(
    reader: BarcodeReader,
    options: BarcodeOptions = BarcodeOptions(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): List<BarcodeResult> =
    withContext(dispatcher) {
        this@suspendExtractBarcodes.extractBarcodes(reader, options)
    }
