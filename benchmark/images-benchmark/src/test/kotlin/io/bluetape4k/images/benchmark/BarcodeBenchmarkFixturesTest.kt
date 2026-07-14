package io.bluetape4k.images.benchmark

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.images.barcode.zxing.ZxingBarcodeReader
import org.junit.jupiter.api.Test

class BarcodeBenchmarkFixturesTest {

    @Test
    fun `canonical manifest contains QR Code 128 and no result fixtures`() {
        val manifest = BarcodeBenchmarkFixtures.loadManifest()

        manifest.fixtures.map(BarcodeBenchmarkFixtureEntry::scenario)
            .shouldBeEqualTo(BarcodeBenchmarkScenario.entries.toList())
    }

    @Test
    fun `canonical fixtures match bytes dimensions and provider expectations`() {
        val reader = ZxingBarcodeReader()

        BarcodeBenchmarkScenario.entries.forEach { scenario ->
            val fixture = BarcodeBenchmarkFixtures.load(scenario)
            val results = reader.readBarcodes(fixture.image, fixture.options())
            fixture.verify(results)
        }
    }

    @Test
    fun `fixture resource path rejects traversal absolute paths and oversized bytes`() {
        val validBytes = byteArrayOf(1, 2, 3)

        assertFailsWith<IllegalArgumentException> {
            BarcodeBenchmarkFixtures.loadForTest(
                manifestJson("../secret.png").toByteArray(),
                BarcodeBenchmarkScenario.QR,
                mapOf("../secret.png" to validBytes),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            BarcodeBenchmarkFixtures.loadForTest(
                manifestJson("/tmp/secret.png").toByteArray(),
                BarcodeBenchmarkScenario.QR,
                mapOf("/tmp/secret.png" to validBytes),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            BarcodeBenchmarkFixtures.loadForTest(
                manifestJson("bench/barcode/qr.png").toByteArray(),
                BarcodeBenchmarkScenario.QR,
                mapOf("bench/barcode/qr.png" to ByteArray(1_048_577)),
            )
        }
    }

    @Test
    fun `manifest rejects duplicate missing and unknown scenarios`() {
        assertFailsWith<IllegalArgumentException> {
            BarcodeBenchmarkFixtures.decodeManifest(
                manifestJson("bench/barcode/qr.png").replace(
                    "\"scenario\":\"code-128\"",
                    "\"scenario\":\"qr\"",
                ).toByteArray(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            BarcodeBenchmarkFixtures.decodeManifest(
                manifestJson("bench/barcode/qr.png").replace(
                    fixtureJson(BarcodeBenchmarkScenario.NO_RESULT),
                    "",
                ).toByteArray(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            BarcodeBenchmarkFixtures.decodeManifest(
                manifestJson("bench/barcode/qr.png").replace(
                    "\"scenario\":\"qr\"",
                    "\"scenario\":\"unknown\"",
                ).toByteArray(),
            )
        }
    }

    @Test
    fun `fixture loading rejects wrong hash missing resources and unknown fields`() {
        val onePixelPng = javaClass.getResourceAsStream("/bench/one-pixel.png")?.use { it.readBytes() }
            ?: byteArrayOf(1, 2, 3)

        assertFailsWith<IllegalArgumentException> {
            BarcodeBenchmarkFixtures.loadForTest(
                manifestJson("bench/barcode/qr.png").toByteArray(),
                BarcodeBenchmarkScenario.QR,
                mapOf("bench/barcode/qr.png" to onePixelPng),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            BarcodeBenchmarkFixtures.loadForTest(
                manifestJson("bench/barcode/qr.png").toByteArray(),
                BarcodeBenchmarkScenario.QR,
                emptyMap(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            BarcodeBenchmarkFixtures.decodeManifest(
                manifestJson("bench/barcode/qr.png")
                    .replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"unexpected\":true")
                    .toByteArray(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            BarcodeBenchmarkFixtures.decodeManifest("not-json".toByteArray())
        }
    }

    @Test
    fun `fixture loading rejects dimensions that differ from decoded image`() {
        val manifest = requireNotNull(javaClass.getResourceAsStream("/bench/barcode/manifest.json"))
            .use { it.readBytes() }
            .toString(Charsets.UTF_8)
            .replaceFirst("\"width\": 220", "\"width\": 219")
            .toByteArray()
        val qrBytes = requireNotNull(javaClass.getResourceAsStream("/bench/barcode/qr.png"))
            .use { it.readBytes() }

        val error = assertFailsWith<IllegalArgumentException> {
            BarcodeBenchmarkFixtures.loadForTest(
                manifest,
                BarcodeBenchmarkScenario.QR,
                mapOf("bench/barcode/qr.png" to qrBytes),
            )
        }

        error.message.orEmpty().shouldContain("dimensions differ")
    }

    private fun manifestJson(qrResource: String): String =
        """
        {
          "schemaVersion":1,
          "hashAlgorithm":"SHA-256",
          "fixtures":[
            ${fixtureJson(BarcodeBenchmarkScenario.QR, qrResource)},
            ${fixtureJson(BarcodeBenchmarkScenario.CODE_128)},
            ${fixtureJson(BarcodeBenchmarkScenario.NO_RESULT)}
          ]
        }
        """.trimIndent()

    private fun fixtureJson(
        scenario: BarcodeBenchmarkScenario,
        resource: String = "bench/barcode/${scenario.value}.png",
    ): String =
        when (scenario) {
            BarcodeBenchmarkScenario.QR ->
                """{"scenario":"qr","resource":"$resource","width":1,"height":1,"sha256":"${"0".repeat(64)}","expectedText":"qr","expectedFormat":"QR_CODE","provenance":"test"}"""

            BarcodeBenchmarkScenario.CODE_128 ->
                """{"scenario":"code-128","resource":"$resource","width":1,"height":1,"sha256":"${"1".repeat(64)}","expectedText":"code","expectedFormat":"CODE_128","provenance":"test"}"""

            BarcodeBenchmarkScenario.NO_RESULT ->
                """{"scenario":"no-result","resource":"$resource","width":1,"height":1,"sha256":"${"2".repeat(64)}","expectEmpty":true,"provenance":"test"}"""
        }
}
