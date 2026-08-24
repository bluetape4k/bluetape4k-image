package io.bluetape4k.images.benchmark

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import org.junit.jupiter.api.Test
import kotlin.math.abs

class OcrBenchmarkMetricsTest {

    @Test
    fun `metric normalizer fixes unicode line endings whitespace and composition`() {
        val decomposed = "Cafe\u0301\r\n  OCR\t  v2  "

        OcrBenchmarkTextNormalizer.normalize(decomposed)
            .shouldBeEqualTo("Café OCR v2")
    }

    @Test
    fun `CER and WER use unicode code points and reference weighted denominators`() {
        val score = OcrBenchmarkErrorRates.score("ab cd", "ab xd")

        score.characterEdits.shouldBeEqualTo(1)
        score.referenceCharacters.shouldBeEqualTo(5)
        score.wordEdits.shouldBeEqualTo(1)
        score.referenceWords.shouldBeEqualTo(2)
        abs(score.cer - 0.2).shouldBeLessThan(0.000_001)
        abs(score.wer - 0.5).shouldBeLessThan(0.000_001)
    }

    @Test
    fun `receipt scores text fixtures and excludes blank and malformed inputs`() {
        val manifest = OcrBenchmarkCorpusV2.loadManifest()
        val predictions =
            manifest.fixtures
                .filter { it.expectedOutcome == OcrBenchmarkExpectedOutcome.TEXT }
                .associate { fixture ->
                    fixture.fixtureId to OcrBenchmarkCorpusV2.loadFixture(fixture.fixtureId).normalizedText
                }

        val receipt = OcrBenchmarkMetricReceipt.create(manifest, predictions, "a".repeat(64))

        OcrBenchmarkMetricReceiptValidator.validate(receipt, manifest)
        receipt.rows.size.shouldBeEqualTo(24)
        receipt.summary.scoredFixtureCount.shouldBeEqualTo(21)
        receipt.summary.excludedFixtureCount.shouldBeEqualTo(3)
        receipt.summary.excludedNegativeCount.shouldBeEqualTo(3)
        receipt.summary.characterEdits.shouldBeEqualTo(0)
        receipt.summary.wordEdits.shouldBeEqualTo(0)
        receipt.summary.cer.shouldBeEqualTo(0.0)
        receipt.summary.wer.shouldBeEqualTo(0.0)
    }

    @Test
    fun `receipt validator rejects wrong aggregate and duplicate fixture rows`() {
        val manifest = OcrBenchmarkCorpusV2.loadManifest()
        val predictions =
            manifest.fixtures
                .filter { it.expectedOutcome == OcrBenchmarkExpectedOutcome.TEXT }
                .associate { fixture ->
                    fixture.fixtureId to OcrBenchmarkCorpusV2.loadFixture(fixture.fixtureId).normalizedText
                }
        val receipt = OcrBenchmarkMetricReceipt.create(manifest, predictions, "b".repeat(64))

        val wrongSummary = receipt.copy(summary = receipt.summary.copy(cer = 0.25))
        val aggregateError = assertFailsWith<IllegalArgumentException> {
            OcrBenchmarkMetricReceiptValidator.validate(wrongSummary, manifest)
        }
        aggregateError.message.orEmpty().shouldContain("CER")

        val duplicateRows = receipt.copy(rows = receipt.rows + receipt.rows.first())
        assertFailsWith<IllegalArgumentException> {
            OcrBenchmarkMetricReceiptValidator.validate(duplicateRows, manifest)
        }
    }

    @Test
    fun `receipt JSON round trip keeps the explicit schema and exclusion reasons`() {
        val manifest = OcrBenchmarkCorpusV2.loadManifest()
        val predictions =
            manifest.fixtures
                .filter { it.expectedOutcome == OcrBenchmarkExpectedOutcome.TEXT }
                .associate { fixture ->
                    fixture.fixtureId to OcrBenchmarkCorpusV2.loadFixture(fixture.fixtureId).normalizedText
                }
        val receipt = OcrBenchmarkMetricReceipt.create(manifest, predictions, "c".repeat(64))

        val decoded = OcrBenchmarkMetricReceipt.decode(OcrBenchmarkMetricReceipt.encode(receipt))

        decoded.schemaVersion.shouldBeEqualTo(1)
        decoded.metric.shouldBeEqualTo("CER_WER")
        decoded.rows.first { it.expectedOutcome == OcrBenchmarkExpectedOutcome.EMPTY }
            .exclusionReason
            .shouldBeEqualTo(OcrBenchmarkMetricExclusionReason.VALID_BLANK)
        OcrBenchmarkMetricReceiptValidator.validateJson(OcrBenchmarkMetricReceipt.encode(decoded), manifest)
    }

    private fun Double.shouldBeLessThan(expected: Double) {
        require(this < expected) { "Expected <$this> to be less than <$expected>" }
    }
}
