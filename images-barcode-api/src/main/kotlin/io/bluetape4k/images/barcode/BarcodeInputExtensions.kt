package io.bluetape4k.images.barcode

import io.bluetape4k.images.immutableImageOf
import java.io.InputStream
import java.nio.file.Path
import okio.Source

/**
 * 인코딩 이미지 [bytes]에서 barcode를 읽습니다.
 */
fun BarcodeReader.readBarcodes(
    bytes: ByteArray,
    options: BarcodeOptions = BarcodeOptions(),
): List<BarcodeResult> =
    readBarcodes(immutableImageOf(bytes), options)

/**
 * 인코딩 이미지 [path]에서 barcode를 읽습니다.
 */
fun BarcodeReader.readBarcodes(
    path: Path,
    options: BarcodeOptions = BarcodeOptions(),
): List<BarcodeResult> =
    readBarcodes(immutableImageOf(path), options)

/**
 * 호출자가 소유한 인코딩 이미지 [input]에서 barcode를 읽습니다.
 *
 * [input]을 닫는 책임은 계속 호출자에게 있습니다.
 */
fun BarcodeReader.readBarcodes(
    input: InputStream,
    options: BarcodeOptions = BarcodeOptions(),
): List<BarcodeResult> =
    readBarcodes(immutableImageOf(input), options)

/**
 * Okio [source]에서 barcode를 읽습니다.
 *
 * source는 `immutableImageOf(source)`에서 닫힙니다.
 */
fun BarcodeReader.readBarcodes(
    source: Source,
    options: BarcodeOptions = BarcodeOptions(),
): List<BarcodeResult> =
    readBarcodes(immutableImageOf(source), options)
