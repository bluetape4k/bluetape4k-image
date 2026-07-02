package io.bluetape4k.images.barcode

import io.bluetape4k.images.immutableImageOf
import java.io.InputStream
import java.nio.file.Path
import okio.Source

/**
 * Reads barcodes from encoded image [bytes].
 */
fun BarcodeReader.readBarcodes(
    bytes: ByteArray,
    options: BarcodeOptions = BarcodeOptions(),
): List<BarcodeResult> =
    readBarcodes(immutableImageOf(bytes), options)

/**
 * Reads barcodes from an encoded image [path].
 */
fun BarcodeReader.readBarcodes(
    path: Path,
    options: BarcodeOptions = BarcodeOptions(),
): List<BarcodeResult> =
    readBarcodes(immutableImageOf(path), options)

/**
 * Reads barcodes from a caller-owned encoded image [input].
 *
 * The caller remains responsible for closing [input].
 */
fun BarcodeReader.readBarcodes(
    input: InputStream,
    options: BarcodeOptions = BarcodeOptions(),
): List<BarcodeResult> =
    readBarcodes(immutableImageOf(input), options)

/**
 * Reads barcodes from an Okio [source].
 *
 * The source is closed by `immutableImageOf(source)`.
 */
fun BarcodeReader.readBarcodes(
    source: Source,
    options: BarcodeOptions = BarcodeOptions(),
): List<BarcodeResult> =
    readBarcodes(immutableImageOf(source), options)
