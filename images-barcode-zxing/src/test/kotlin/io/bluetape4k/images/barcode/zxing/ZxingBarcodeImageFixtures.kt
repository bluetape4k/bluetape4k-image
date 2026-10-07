package io.bluetape4k.images.barcode.zxing

import com.google.zxing.MultiFormatWriter
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.common.BitMatrix
import com.sksamuel.scrimage.ImmutableImage
import com.google.zxing.BarcodeFormat as ZxingFormat

object ZxingBarcodeImageFixtures {

    fun barcodeImage(
        text: String,
        format: ZxingFormat,
    ): ImmutableImage {
        val dimensions = when (format) {
            ZxingFormat.CODE_128 -> 360 to 120
            else -> 220 to 220
        }
        val matrix: BitMatrix = MultiFormatWriter().encode(text, format, dimensions.first, dimensions.second)
        return ImmutableImage.fromAwt(MatrixToImageWriter.toBufferedImage(matrix))
    }
}
