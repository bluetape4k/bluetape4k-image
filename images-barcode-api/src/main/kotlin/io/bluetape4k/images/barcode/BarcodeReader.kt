package io.bluetape4k.images.barcode

import com.sksamuel.scrimage.ImmutableImage

/**
 * [ImmutableImage]에서 barcode를 읽습니다.
 *
 * ## 동작/계약
 * 구현체는 provider adapter입니다. blocking, native, remote decoder를 호출할 수 있지만
 * provider-neutral [BarcodeResult] 값을 반환해야 합니다. coroutine caller는 dispatcher를
 * 선택하려면 [suspendExtractBarcodes]를 사용합니다.
 */
fun interface BarcodeReader {

    /**
     * [options]를 사용해 [image]에서 barcode result를 읽습니다.
     *
     * @param image source image입니다.
     * @param options provider-neutral 추출 option입니다.
     * @return provider order를 보존한 디코딩 barcode result입니다.
     */
    fun readBarcodes(
        image: ImmutableImage,
        options: BarcodeOptions,
    ): List<BarcodeResult>
}

/**
 * 기본 [BarcodeOptions]를 사용해 [image]에서 barcode를 읽습니다.
 */
fun BarcodeReader.readBarcodes(image: ImmutableImage): List<BarcodeResult> =
    readBarcodes(image, BarcodeOptions())
