package io.bluetape4k.images.benchmark

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import org.junit.jupiter.api.Test

class OcrBenchmarkFixturesTest {

    @Test
    fun `canonical manifest has all required OCR fixture scenarios`() {
        OcrBenchmarkFixtures.loadManifest().fixtures.map(OcrBenchmarkFixtureEntry::scenario)
            .shouldBeEqualTo(OcrBenchmarkScenario.entries.toList())
    }

    @Test
    fun `canonical OCR fixtures match pinned bytes and dimensions`() {
        OcrBenchmarkScenario.entries.forEach { scenario ->
            val fixture = OcrBenchmarkFixtures.load(scenario)
            fixture.image.width.shouldBeEqualTo(fixture.entry.width)
            fixture.image.height.shouldBeEqualTo(fixture.entry.height)
        }
    }

    @Test
    fun `fixture loading rejects path traversal and wrong hashes`() {
        val traversal = manifestJson("../secret.png")
        val traversalError = assertFailsWith<IllegalArgumentException> {
            OcrBenchmarkFixtures.loadForTest(
                traversal.toByteArray(),
                OcrBenchmarkScenario.CLEAN_TEXT,
                mapOf("../secret.png" to byteArrayOf(1)),
            )
        }
        traversalError.message.orEmpty().shouldContain("normalized and relative")

        assertFailsWith<IllegalArgumentException> {
            OcrBenchmarkFixtures.loadForTest(
                manifestJson("bench/ocr/clean-text.png").toByteArray(),
                OcrBenchmarkScenario.CLEAN_TEXT,
                mapOf("bench/ocr/clean-text.png" to byteArrayOf(1)),
            )
        }
    }

    private fun manifestJson(resource: String): String =
        """
        {
          "schemaVersion":1,
          "hashAlgorithm":"SHA-256",
          "fixtures":[
            {"scenario":"clean-text","resource":"$resource","width":1,"height":1,"sha256":"${"0".repeat(64)}","languages":["eng"],"expectedTokens":["OCR"],"provenance":"test"},
            {"scenario":"noisy-scan","resource":"bench/ocr/noisy-scan.png","width":1,"height":1,"sha256":"${"1".repeat(64)}","languages":["eng"],"expectedTokens":["OCR"],"provenance":"test"},
            {"scenario":"rotated-document","resource":"bench/ocr/rotated-document.png","width":1,"height":1,"sha256":"${"2".repeat(64)}","languages":["eng"],"expectedTokens":["OCR"],"provenance":"test"},
            {"scenario":"multilingual-text","resource":"bench/ocr/multilingual-text.png","width":1,"height":1,"sha256":"${"3".repeat(64)}","languages":["eng","kor","jpn"],"expectedTokens":["OCR"],"provenance":"test"}
          ]
        }
        """.trimIndent()
}
