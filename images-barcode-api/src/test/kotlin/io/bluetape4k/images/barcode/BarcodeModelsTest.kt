package io.bluetape4k.images.barcode

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import org.junit.jupiter.api.Test

class BarcodeModelsTest {

    @Test
    fun `provider identity validates metadata`() {
        val provider = BarcodeProviderIdentity(
            name = "ZXing",
            version = "3.5.4",
            backend = "java",
            metadata = mapOf("decoder" to "MultiFormatReader"),
        )

        provider.name shouldBeEqualTo "ZXing"
        provider.metadata["decoder"] shouldBeEqualTo "MultiFormatReader"

        assertFailsWith<IllegalArgumentException> {
            BarcodeProviderIdentity(name = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            BarcodeProviderIdentity(name = "ZXing", metadata = mapOf(" " to "value"))
        }
        assertFailsWith<IllegalArgumentException> {
            BarcodeProviderIdentity(name = "ZXing", metadata = mapOf("decoder" to " "))
        }
    }

    @Test
    fun `geometry validates coordinate spaces`() {
        val point = BarcodePoint(x = 10.0, y = 12.0)
        val box = BarcodeBoundingBox(
            x = 4.0,
            y = 8.0,
            width = 32.0,
            height = 16.0,
            coordinateSpace = BarcodeCoordinateSpace.PIXEL,
        )
        val region = BarcodeRegion(
            points = listOf(point),
            coordinateSpace = BarcodeCoordinateSpace.PIXEL,
            boundingBox = box,
        )

        region.points shouldContain point
        region.boundingBox shouldBeEqualTo box

        assertFailsWith<IllegalArgumentException> {
            BarcodePoint(x = Double.NaN, y = 1.0)
        }
        assertFailsWith<IllegalArgumentException> {
            BarcodeBoundingBox(
                x = 0.9,
                y = 0.1,
                width = 0.2,
                height = 0.2,
                coordinateSpace = BarcodeCoordinateSpace.NORMALIZED,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            BarcodeRegion(
                points = emptyList(),
                coordinateSpace = BarcodeCoordinateSpace.PIXEL,
            )
        }
    }

    @Test
    fun `options filter formats and confidence`() {
        val provider = BarcodeProviderIdentity(name = "fake")
        val accepted = BarcodeResult(
            text = "https://bluetape4k.io",
            format = BarcodeFormat.QR_CODE,
            provider = provider,
            confidence = 0.92,
        )
        val rejectedFormat = BarcodeResult(
            text = "1234567890128",
            format = BarcodeFormat.EAN_13,
            provider = provider,
            confidence = 0.99,
        )
        val rejectedConfidence = BarcodeResult(
            text = "low",
            format = BarcodeFormat.QR_CODE,
            provider = provider,
            confidence = 0.40,
        )

        val options = BarcodeOptions(
            formats = setOf(BarcodeFormat.QR_CODE),
            minimumConfidence = 0.80,
        )

        options.accepts(accepted) shouldBeEqualTo true
        options.accepts(rejectedFormat) shouldBeEqualTo false
        options.accepts(rejectedConfidence) shouldBeEqualTo false
        options.filter(listOf(accepted, rejectedFormat, rejectedConfidence)) shouldBeEqualTo listOf(accepted)

        assertFailsWith<IllegalArgumentException> {
            BarcodeOptions(minimumConfidence = 1.1)
        }
    }

    @Test
    fun `result validates text and raw metadata`() {
        val provider = BarcodeProviderIdentity(name = "fake")
        val rawBytes = byteArrayOf(1, 2, 3)
        val result = BarcodeResult(
            text = "BT4K",
            format = BarcodeFormat.CODE_128,
            provider = provider,
            rawBytes = rawBytes,
            rawBackendFormat = "CODE128",
            metadata = mapOf("checksum" to "ok"),
        )

        result.text shouldBeEqualTo "BT4K"
        result.rawBytes?.contentEquals(rawBytes) shouldBeEqualTo true
        result.metadata["checksum"] shouldBeEqualTo "ok"
        val sameBytesResult = BarcodeResult(
            text = "BT4K",
            format = BarcodeFormat.CODE_128,
            provider = provider,
            rawBytes = byteArrayOf(1, 2, 3),
            rawBackendFormat = "CODE128",
            metadata = mapOf("checksum" to "ok"),
        )
        result shouldBeEqualTo sameBytesResult
        result.hashCode() shouldBeEqualTo sameBytesResult.hashCode()

        assertFailsWith<IllegalArgumentException> {
            BarcodeResult(text = " ", format = BarcodeFormat.QR_CODE, provider = provider)
        }
        assertFailsWith<IllegalArgumentException> {
            BarcodeResult(text = "BT4K", format = BarcodeFormat.QR_CODE, provider = provider, confidence = -0.1)
        }
        assertFailsWith<IllegalArgumentException> {
            BarcodeResult(text = "BT4K", format = BarcodeFormat.QR_CODE, provider = provider, rawBytes = byteArrayOf())
        }
    }

    @Test
    fun `barcode exception exposes reason and sanitized message`() {
        val error = BarcodeException(
            reason = BarcodeFailureReason.DECODE_FAILED,
            message = "decode failed",
        )

        error.reason shouldBeEqualTo BarcodeFailureReason.DECODE_FAILED
        error.message shouldBeEqualTo "decode failed"
    }

    @Test
    fun `models are serializable`() {
        val result = BarcodeResult(
            text = "BT4K",
            format = BarcodeFormat.DATA_MATRIX,
            provider = BarcodeProviderIdentity(name = "fake"),
            region = BarcodeRegion(
                points = listOf(BarcodePoint(x = 0.1, y = 0.2)),
                coordinateSpace = BarcodeCoordinateSpace.NORMALIZED,
            ),
            confidence = 1.0,
        )

        @Suppress("UNCHECKED_CAST")
        val restored = roundTrip(result) as BarcodeResult

        restored.text shouldBeEqualTo result.text
        restored.format shouldBeEqualTo result.format
        restored.region?.points shouldBeEqualTo result.region?.points
    }

    private fun roundTrip(value: Any): Any {
        val bytes = ByteArrayOutputStream().use { buffer ->
            ObjectOutputStream(buffer).use { output ->
                output.writeObject(value)
            }
            buffer.toByteArray()
        }

        return ObjectInputStream(ByteArrayInputStream(bytes)).use { input ->
            input.readObject()
        }
    }
}
