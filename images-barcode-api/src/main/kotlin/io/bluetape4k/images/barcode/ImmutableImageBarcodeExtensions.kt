package io.bluetape4k.images.barcode

import com.sksamuel.scrimage.ImmutableImage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts barcodes from this image with [reader].
 *
 * ## Contract
 * This is the blocking extraction surface. It delegates directly to
 * [BarcodeReader.readBarcodes] and applies [BarcodeOptions.filter] so provider
 * modules can return raw provider order while callers get the requested view.
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
 * Extracts barcodes from this image on [dispatcher].
 *
 * ## Contract
 * The provider call runs inside [withContext]. Cancellation before dispatch
 * prevents the reader from starting, and provider-thrown cancellation is
 * propagated unchanged.
 */
suspend fun ImmutableImage.suspendExtractBarcodes(
    reader: BarcodeReader,
    options: BarcodeOptions = BarcodeOptions(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): List<BarcodeResult> =
    withContext(dispatcher) {
        this@suspendExtractBarcodes.extractBarcodes(reader, options)
    }
