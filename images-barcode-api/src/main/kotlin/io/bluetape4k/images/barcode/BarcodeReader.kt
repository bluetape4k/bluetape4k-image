package io.bluetape4k.images.barcode

import com.sksamuel.scrimage.ImmutableImage

/**
 * Reads barcodes from an [ImmutableImage].
 *
 * ## Contract
 * Implementations are provider adapters. They may call blocking, native, or
 * remote decoders, but must return provider-neutral [BarcodeResult] values.
 * Coroutine callers should use [suspendExtractBarcodes] to choose a dispatcher.
 */
fun interface BarcodeReader {

    /**
     * Reads barcode results from [image] using [options].
     *
     * @param image source image
     * @param options provider-neutral extraction options
     * @return decoded barcode results in provider order
     */
    fun readBarcodes(
        image: ImmutableImage,
        options: BarcodeOptions,
    ): List<BarcodeResult>
}

/**
 * Reads barcodes from [image] using default [BarcodeOptions].
 */
fun BarcodeReader.readBarcodes(image: ImmutableImage): List<BarcodeResult> =
    readBarcodes(image, BarcodeOptions())
