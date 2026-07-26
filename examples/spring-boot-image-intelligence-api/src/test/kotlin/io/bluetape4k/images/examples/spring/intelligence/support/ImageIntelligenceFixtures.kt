package io.bluetape4k.images.examples.spring.intelligence.support

import com.sksamuel.scrimage.ImmutableImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import com.sksamuel.scrimage.nio.JpegWriter
import com.sksamuel.scrimage.nio.PngWriter
import com.sksamuel.scrimage.webp.WebpWriter

internal fun pngBytes(width: Int = 40, height: Int = 30): ByteArray =
    ImmutableImage.create(width, height)
        .forWriter(PngWriter.MaxCompression)
        .bytes()

internal fun jpegBytes(width: Int = 40, height: Int = 30): ByteArray =
    ImmutableImage.create(width, height)
        .forWriter(JpegWriter.Default)
        .bytes()

internal fun webpBytes(width: Int = 40, height: Int = 30): ByteArray =
    ImmutableImage.create(width, height)
        .forWriter(WebpWriter.DEFAULT)
        .bytes()

internal fun qrImage(
    payload: String = VISITOR_PASS_PAYLOAD,
    width: Int = 240,
    height: Int = 240,
): ImmutableImage {
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, width, height)
    return ImmutableImage.fromAwt(MatrixToImageWriter.toBufferedImage(matrix))
}

internal const val VISITOR_PASS_PAYLOAD: String = "visitor:PASS-001"
