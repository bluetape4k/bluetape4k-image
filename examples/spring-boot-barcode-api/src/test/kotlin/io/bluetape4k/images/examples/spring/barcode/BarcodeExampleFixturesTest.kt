package io.bluetape4k.images.examples.spring.barcode

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.images.ImageDimensions
import io.bluetape4k.images.barcode.BarcodeFormat
import io.bluetape4k.images.barcode.BarcodeReader
import io.bluetape4k.images.barcode.extractBarcodes
import io.bluetape4k.images.barcode.zxing.ZxingBarcodeReader
import io.bluetape4k.images.immutableImageOf
import io.bluetape4k.images.probeImageDimensions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.security.MessageDigest

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BarcodeExampleFixturesTest {

    private val fixtures = BarcodeExampleFixtures()
    private val reader: BarcodeReader = ZxingBarcodeReader()

    @Test
    fun `fixtures have pinned hashes dimensions and extraction behavior`() {
        val sample = fixtures.bytes(BarcodeExampleFixture.SAMPLE)
        sha256(sample) shouldBeEqualTo SAMPLE_SHA256
        probeImageDimensions(sample) shouldBeEqualTo ImageDimensions(220, 220)

        val sampleResults = immutableImageOf(sample).extractBarcodes(reader)
        sampleResults.single().text shouldBeEqualTo "bluetape4k-barcode-quickstart"
        sampleResults.single().format shouldBeEqualTo BarcodeFormat.QR_CODE

        val noResult = fixtures.bytes(BarcodeExampleFixture.NO_RESULT)
        sha256(noResult) shouldBeEqualTo NO_RESULT_SHA256
        probeImageDimensions(noResult) shouldBeEqualTo ImageDimensions(220, 220)
        immutableImageOf(noResult).extractBarcodes(reader).shouldBeEmpty()

        sha256(fixtures.bytes(BarcodeExampleFixture.MALFORMED)) shouldBeEqualTo MALFORMED_SHA256
    }

    @Test
    fun `fixture reads return isolated byte arrays`() {
        val first = fixtures.bytes(BarcodeExampleFixture.SAMPLE)
        val originalFirstByte = first[0]
        first[0] = 0

        fixtures.bytes(BarcodeExampleFixture.SAMPLE)[0] shouldBeEqualTo originalFirstByte
    }

    @Test
    fun `fixture paths are fixed and missing resources fail construction`() {
        BarcodeExampleFixture.entries.map { it.resource } shouldBeEqualTo listOf(
            "barcodes/qr.png",
            "barcodes/no-result.png",
            "barcodes/malformed.bin",
        )

        assertFailsWith<IllegalArgumentException> {
            BarcodeExampleFixtures(resourceLoader = { null })
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { "%02x".format(it) }

    private companion object {
        const val SAMPLE_SHA256 = "5d048dd6769ede80f453ffb6c80fe6745092bf895c429b6104d5cc74d892c44d"
        const val NO_RESULT_SHA256 = "86aad41769423ad85a979fefe109d00829044a1eba5d891547499413e3d9ff2b"
        const val MALFORMED_SHA256 = "f2e2c6db1745cc40df646dc40c385487c36e4ceb3f1d5c8d6ad1f7620af1ebae"
    }
}
