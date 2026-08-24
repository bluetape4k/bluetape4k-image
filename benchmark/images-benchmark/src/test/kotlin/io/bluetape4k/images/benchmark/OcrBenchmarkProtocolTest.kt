package io.bluetape4k.images.benchmark

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import org.junit.jupiter.api.Test

class OcrBenchmarkProtocolTest {

    @Test
    fun `protocol receipt covers every positive fixture and embeds metric receipt`() {
        val manifest = OcrBenchmarkCorpusV2.loadManifest()
        val predictions =
            manifest.fixtures
                .filter { it.expectedOutcome == OcrBenchmarkExpectedOutcome.TEXT }
                .associate { fixture ->
                    fixture.fixtureId to OcrBenchmarkCorpusV2.loadFixture(fixture.fixtureId).normalizedText
                }
        val manifestSha256 = "d".repeat(64)
        val metricReceipt = OcrBenchmarkMetricReceipt.create(manifest, predictions, manifestSha256)
        val metricRows = metricReceipt.rows.associateBy(OcrBenchmarkMetricRow::fixtureId)
        val receipt = OcrBenchmarkProtocolReceipt(
            schemaVersion = 1,
            issue = 565,
            runId = "issue-565-protocol-test-001",
            manifestSha256 = manifestSha256,
            host = OcrBenchmarkHostEnvelope(
                os = "test",
                arch = "test",
                jvm = "test",
                tesseract = "test",
                tessdata = "test",
                languages = listOf("eng", "jpn", "kor"),
            ),
            protocol = OcrBenchmarkProtocolEnvelope(
                coldRuns = 1,
                warmupRuns = 2,
                warmRuns = 3,
                throughputWindowMillis = 250,
                rssUnit = "bytes",
            ),
            rows = manifest.fixtures.map { fixture ->
                OcrBenchmarkProtocolRow(
                    fixtureId = fixture.fixtureId,
                    scenario = fixture.scenario,
                    expectedOutcome = fixture.expectedOutcome,
                    coldLatencyNanos = 10_000,
                    warmLatencyNanos = 5_000,
                    throughputOpsPerSecond = 100.0,
                    warmIterations = 3,
                    rssBeforeBytes = 10_000,
                    rssPeakBytes = 12_000,
                    outputSha256 = metricRows.getValue(fixture.fixtureId).predictionSha256
                        ?: sha256Hex(ByteArray(0)),
                )
            },
            metrics = metricReceipt,
        )

        OcrBenchmarkProtocolReceiptValidator.validate(receipt, manifest)
        receipt.rows.size.shouldBeEqualTo(24)
        receipt.protocol.rssUnit.shouldBeEqualTo("bytes")
    }

    @Test
    fun `protocol validator rejects non positive rss and manifest drift`() {
        val manifest = OcrBenchmarkCorpusV2.loadManifest()
        val predictions =
            manifest.fixtures
                .filter { it.expectedOutcome == OcrBenchmarkExpectedOutcome.TEXT }
                .associate { fixture ->
                    fixture.fixtureId to OcrBenchmarkCorpusV2.loadFixture(fixture.fixtureId).normalizedText
                }
        val manifestSha256 = "f".repeat(64)
        val metricReceipt = OcrBenchmarkMetricReceipt.create(manifest, predictions, manifestSha256)
        val metricRows = metricReceipt.rows.associateBy(OcrBenchmarkMetricRow::fixtureId)
        val valid = OcrBenchmarkProtocolReceipt(
            schemaVersion = 1,
            issue = 565,
            runId = "issue-565-protocol-test-002",
            manifestSha256 = manifestSha256,
            host = OcrBenchmarkHostEnvelope("test", "test", "test", "test", "test", listOf("eng", "jpn", "kor")),
            protocol = OcrBenchmarkProtocolEnvelope(1, 2, 3, 250, "bytes"),
            rows = manifest.fixtures.map { fixture ->
                OcrBenchmarkProtocolRow(
                    fixture.fixtureId,
                    fixture.scenario,
                    fixture.expectedOutcome,
                    10_000,
                    5_000,
                    100.0,
                    3,
                    10_000,
                    12_000,
                    metricRows.getValue(fixture.fixtureId).predictionSha256
                        ?: sha256Hex(ByteArray(0)),
                )
            },
            metrics = metricReceipt,
        )

        val rssError = assertFailsWith<IllegalArgumentException> {
            OcrBenchmarkProtocolReceiptValidator.validate(
                valid.copy(rows = valid.rows.dropLast(1) + valid.rows.last().copy(rssPeakBytes = 0)),
                manifest,
            )
        }
        rssError.message.orEmpty().shouldContain("RSS")

        val outputHashError = assertFailsWith<IllegalArgumentException> {
            OcrBenchmarkProtocolReceiptValidator.validate(
                valid.copy(rows = valid.rows.dropLast(1) + valid.rows.last().copy(outputSha256 = "e".repeat(64))),
                manifest,
            )
        }
        outputHashError.message.orEmpty().shouldContain("output SHA-256")

        val warmIterationError = assertFailsWith<IllegalArgumentException> {
            OcrBenchmarkProtocolReceiptValidator.validate(
                valid.copy(rows = valid.rows.dropLast(1) + valid.rows.last().copy(warmIterations = 1)),
                manifest,
            )
        }
        warmIterationError.message.orEmpty().shouldContain("warm iteration")

        val driftError = assertFailsWith<IllegalArgumentException> {
            OcrBenchmarkProtocolReceiptValidator.validate(
                valid.copy(manifestSha256 = "0".repeat(64)),
                manifest,
            )
        }
        driftError.message.orEmpty().shouldContain("manifest SHA-256")
    }
}
