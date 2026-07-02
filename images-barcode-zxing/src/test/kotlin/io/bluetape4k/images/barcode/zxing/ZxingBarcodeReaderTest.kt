package io.bluetape4k.images.barcode.zxing

import com.google.zxing.BarcodeFormat as ZxingFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotBeEmpty
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.images.barcode.BarcodeException
import io.bluetape4k.images.barcode.BarcodeFailureReason
import io.bluetape4k.images.barcode.BarcodeFormat
import io.bluetape4k.images.barcode.BarcodeOptions
import io.bluetape4k.images.barcode.extractBarcodes
import java.awt.Color
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ZxingBarcodeReaderTest {

    private lateinit var reader: ZxingBarcodeReader

    @BeforeEach
    fun beforeEach() {
        reader = ZxingBarcodeReader()
    }

    @Test
    fun `reads QR code into provider neutral result`() {
        val image = barcodeImage("bluetape4k-qr", ZxingFormat.QR_CODE)

        val results = image.extractBarcodes(reader, BarcodeOptions(formats = setOf(BarcodeFormat.QR_CODE)))

        val result = results.single()
        result.text shouldBeEqualTo "bluetape4k-qr"
        result.format shouldBeEqualTo BarcodeFormat.QR_CODE
        result.provider.name shouldBeEqualTo "ZXing"
        result.provider.backend shouldBeEqualTo "zxing-core"
        result.rawBackendFormat shouldBeEqualTo "QR_CODE"
        val region = result.region.shouldNotBeNull()
        region.points.shouldNotBeEmpty()
        region.boundingBox.shouldNotBeNull()
    }

    @Test
    fun `reads Code 128 barcode`() {
        val image = barcodeImage("BT4K-245", ZxingFormat.CODE_128)

        val result = image.extractBarcodes(reader).single()

        result.text shouldBeEqualTo "BT4K-245"
        result.format shouldBeEqualTo BarcodeFormat.CODE_128
        result.rawBackendFormat shouldBeEqualTo "CODE_128"
    }

    @Test
    fun `format mismatch returns no results`() {
        val image = barcodeImage("format-mismatch", ZxingFormat.QR_CODE)

        val results = image.extractBarcodes(reader, BarcodeOptions(formats = setOf(BarcodeFormat.CODE_128)))

        results.isEmpty().shouldBeTrue()
    }

    @Test
    fun `no barcode image returns no results`() {
        val results = blankImage().extractBarcodes(reader)

        results.isEmpty().shouldBeTrue()
    }

    @Test
    fun `try harder reads rotated QR code`() {
        val image = rotateClockwise(barcodeImage("rotated-qr", ZxingFormat.QR_CODE))

        val result = image.extractBarcodes(
            reader,
            BarcodeOptions(formats = setOf(BarcodeFormat.QR_CODE), tryHarder = true),
        ).single()

        result.text shouldBeEqualTo "rotated-qr"
        result.format shouldBeEqualTo BarcodeFormat.QR_CODE
    }

    @Test
    fun `malformed encoded bytes map to barcode exception`() {
        val error = assertFailsWith<BarcodeException> {
            reader.readBarcodes("not-an-image".toByteArray())
        }

        error.reason shouldBeEqualTo BarcodeFailureReason.MALFORMED_INPUT
    }

    @Test
    fun `unsupported requested format maps to barcode exception`() {
        val error = assertFailsWith<BarcodeException> {
            blankImage().extractBarcodes(reader, BarcodeOptions(formats = setOf(BarcodeFormat.UNKNOWN)))
        }

        error.reason shouldBeEqualTo BarcodeFailureReason.UNSUPPORTED_FORMAT
    }

    @Test
    fun `raw bytes are included only when requested and available`() {
        val image = barcodeImage("raw-bytes", ZxingFormat.QR_CODE)

        val result = image.extractBarcodes(reader, BarcodeOptions(includeRawBytes = true)).single()

        result.rawBytes.shouldNotBeNull().isNotEmpty().shouldBeTrue()
    }

    private fun barcodeImage(
        text: String,
        format: ZxingFormat,
    ): ImmutableImage {
        val dimensions = when (format) {
            ZxingFormat.CODE_128 -> 360 to 120
            else -> 220 to 220
        }
        val matrix = MultiFormatWriter().encode(text, format, dimensions.first, dimensions.second)
        return ImmutableImage.fromAwt(MatrixToImageWriter.toBufferedImage(matrix))
    }

    private fun blankImage(): ImmutableImage {
        val buffered = BufferedImage(180, 120, BufferedImage.TYPE_INT_RGB)
        val graphics = buffered.createGraphics()
        try {
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, buffered.width, buffered.height)
        } finally {
            graphics.dispose()
        }
        return ImmutableImage.fromAwt(buffered)
    }

    private fun rotateClockwise(image: ImmutableImage): ImmutableImage {
        val source = image.awt()
        val rotated = BufferedImage(source.height, source.width, BufferedImage.TYPE_INT_RGB)
        val graphics = rotated.createGraphics()
        try {
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, rotated.width, rotated.height)
            val transform = AffineTransform()
            transform.translate(source.height.toDouble(), 0.0)
            transform.rotate(Math.PI / 2.0)
            graphics.drawImage(source, transform, null)
        } finally {
            graphics.dispose()
        }
        return ImmutableImage.fromAwt(rotated)
    }
}
