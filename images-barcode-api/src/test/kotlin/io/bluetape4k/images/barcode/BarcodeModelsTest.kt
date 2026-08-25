package io.bluetape4k.images.barcode

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotContain
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import org.junit.jupiter.api.Test

class BarcodeModelsTest {

    @Test
    fun `provider identity validates metadata`() {
        val metadata = mutableMapOf("decoder" to "MultiFormatReader")
        val provider = BarcodeProviderIdentity(
            name = "ZXing",
            version = "3.5.4",
            backend = "java",
            metadata = metadata,
        )

        provider.name shouldBeEqualTo "ZXing"
        provider.metadata["decoder"] shouldBeEqualTo "MultiFormatReader"
        metadata["decoder"] = "mutated"
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
        val points = mutableListOf(point)
        val box = BarcodeBoundingBox(
            x = 4.0,
            y = 8.0,
            width = 32.0,
            height = 16.0,
            coordinateSpace = BarcodeCoordinateSpace.PIXEL,
        )
        val region = BarcodeRegion(
            points = points,
            coordinateSpace = BarcodeCoordinateSpace.PIXEL,
            boundingBox = box,
        )

        region.points shouldContain point
        region.boundingBox shouldBeEqualTo box
        points.clear()
        region.points shouldContain point

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

        val formats = mutableSetOf(BarcodeFormat.QR_CODE)
        val metadata = mutableMapOf("source" to "test")
        val options = BarcodeOptions(
            formats = formats,
            minimumConfidence = 0.80,
            metadata = metadata,
        )

        formats += BarcodeFormat.CODE_128
        metadata["source"] = "mutated"
        options.formats shouldBeEqualTo setOf(BarcodeFormat.QR_CODE)
        options.metadata["source"] shouldBeEqualTo "test"

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
    fun `result toString redacts payload and metadata values`() {
        val result = BarcodeResult(
            text = "secret-token-123",
            format = BarcodeFormat.QR_CODE,
            provider = BarcodeProviderIdentity(
                name = "fake",
                version = "1.0",
                backend = "test",
                metadata = mapOf("credential" to "provider-secret"),
            ),
            rawBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04),
            rawBackendFormat = "QR_CODE",
            metadata = mapOf("secret-header" to "header-secret"),
        )

        val rendered = result.toString()

        rendered.shouldNotContain("secret-token-123")
        rendered.shouldNotContain("provider-secret")
        rendered.shouldNotContain("header-secret")
        rendered.shouldNotContain("[1, 2, 3, 4]")
        rendered.shouldContain("textLength=16")
        rendered.shouldContain("rawBytes=length=4")
        rendered.shouldContain("metadataEntries=1")
        rendered.shouldContain("provider=fake")
    }

    @Test
    fun `result keeps raw bytes immutable across input and getter mutations`() {
        val provider = BarcodeProviderIdentity(name = "fake")
        val source = byteArrayOf(1, 2, 3)
        val metadata = mutableMapOf("source" to "test")
        val result = BarcodeResult(
            text = "BT4K",
            format = BarcodeFormat.CODE_128,
            provider = provider,
            rawBytes = source,
            metadata = metadata,
        )
        val expected = source.copyOf()
        val initialHash = result.hashCode()
        val results = hashSetOf(result)

        source[0] = 9
        result.rawBytes.contentEquals(expected) shouldBeEqualTo true
        metadata["source"] = "mutated"
        result.metadata["source"] shouldBeEqualTo "test"

        val exposed = result.rawBytes ?: error("rawBytes should be present")
        exposed[1] = 8
        result.rawBytes?.contentEquals(expected) shouldBeEqualTo true
        result.hashCode() shouldBeEqualTo initialHash
        results.contains(result) shouldBeEqualTo true
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
            rawBytes = byteArrayOf(7, 8, 9),
        )

        @Suppress("UNCHECKED_CAST")
        val restored = roundTrip(result) as BarcodeResult

        restored.text shouldBeEqualTo result.text
        restored.format shouldBeEqualTo result.format
        restored.region?.points shouldBeEqualTo result.region?.points
        restored.rawBytes?.contentEquals(byteArrayOf(7, 8, 9)) shouldBeEqualTo true

        val exposed = restored.rawBytes ?: error("rawBytes should be present")
        exposed[0] = 0
        restored.rawBytes?.contentEquals(byteArrayOf(7, 8, 9)) shouldBeEqualTo true
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
