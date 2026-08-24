package io.bluetape4k.images.barcode.zxing

import com.google.zxing.BarcodeFormat as ZxingFormat
import com.sksamuel.scrimage.nio.PngWriter
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
import io.bluetape4k.images.barcode.testfixtures.BarcodeTestFixtures
import io.bluetape4k.images.barcode.zxing.ZxingBarcodeImageFixtures.barcodeImage
import io.bluetape4k.images.ImageDimensions
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
        val results = BarcodeTestFixtures.blankImage().extractBarcodes(reader)

        results.isEmpty().shouldBeTrue()
    }

    @Test
    fun `try harder reads rotated QR code`() {
        val image = BarcodeTestFixtures.rotateClockwise(barcodeImage("rotated-qr", ZxingFormat.QR_CODE))

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
            reader.readBarcodes(BarcodeTestFixtures.malformedImageBytes)
        }

        error.reason shouldBeEqualTo BarcodeFailureReason.MALFORMED_INPUT
    }

    @Test
    fun `external encoded bytes enforce image side limits before ZXing`() {
        val bytes = BarcodeTestFixtures.blankImage(ImageDimensions(width = 8_193, height = 1))
            .bytes(PngWriter.NoCompression)

        val error = assertFailsWith<BarcodeException> {
            reader.readBarcodes(bytes)
        }

        error.reason shouldBeEqualTo BarcodeFailureReason.MALFORMED_INPUT
    }

    @Test
    fun `unsupported requested format maps to barcode exception`() {
        val error = assertFailsWith<BarcodeException> {
            BarcodeTestFixtures.blankImage().extractBarcodes(
                reader,
                BarcodeOptions(formats = setOf(BarcodeFormat.UNKNOWN)),
            )
        }

        error.reason shouldBeEqualTo BarcodeFailureReason.UNSUPPORTED_FORMAT
    }

    @Test
    fun `raw bytes are included only when requested and available`() {
        val image = barcodeImage("raw-bytes", ZxingFormat.QR_CODE)

        val result = image.extractBarcodes(reader, BarcodeOptions(includeRawBytes = true)).single()

        result.rawBytes.shouldNotBeNull().isNotEmpty().shouldBeTrue()
    }
}
