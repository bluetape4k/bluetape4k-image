package io.bluetape4k.images.benchmark

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import org.junit.jupiter.api.Test

class OcrProviderComparisonReceiptTest {

    @Test
    fun `baseline only receipt round trips with provider identity and full corpus coverage`() {
        val manifest = OcrBenchmarkCorpusV2.loadManifest()
        val receipt = receipt(manifest, OcrProviderComparisonStatus.BASELINE_ONLY)

        OcrProviderComparisonReceiptValidator.validate(receipt, manifest)
        val encoded = OcrProviderComparisonReceipt.encode(receipt).decodeToString()
        encoded.shouldContain("\"scenario\":\"clean\"")
        val decoded = OcrProviderComparisonReceipt.decode(encoded.encodeToByteArray())

        decoded.status.shouldBeEqualTo(OcrProviderComparisonStatus.BASELINE_ONLY)
        decoded.providers.single().fixtures.size.shouldBeEqualTo(manifest.fixtures.size + manifest.negatives.size)
        decoded.comparison.shouldBeEqualTo(null)
    }

    @Test
    fun `comparable receipt requires two immutable provider identities and comparison summary`() {
        val manifest = OcrBenchmarkCorpusV2.loadManifest()
        val receipt = receipt(manifest, OcrProviderComparisonStatus.COMPARABLE)

        OcrProviderComparisonReceiptValidator.validate(receipt, manifest)
        receipt.providers.map { it.identity.provider }.shouldBeEqualTo(listOf("tesseract", "paddleocr"))
    }

    @Test
    fun `validator rejects provider manifest drift and one provider comparison`() {
        val manifest = OcrBenchmarkCorpusV2.loadManifest()
        val valid = receipt(manifest, OcrProviderComparisonStatus.COMPARABLE)

        val manifestError = assertFailsWith<IllegalArgumentException> {
            OcrProviderComparisonReceiptValidator.validate(
                valid.copy(providers = valid.providers.mapIndexed { index, provider ->
                    if (index == 1) provider.copy(manifestSha256 = "0".repeat(64)) else provider
                }),
                manifest,
            )
        }
        manifestError.message.orEmpty().shouldContain("manifest SHA-256")

        val providerCountError = assertFailsWith<IllegalArgumentException> {
            OcrProviderComparisonReceiptValidator.validate(
                valid.copy(providers = valid.providers.take(1)),
                manifest,
            )
        }
        providerCountError.message.orEmpty().shouldContain("exactly two providers")
    }

    @Test
    fun `validator rejects mutable comparison identity and unknown JSON fields`() {
        val manifest = OcrBenchmarkCorpusV2.loadManifest()
        val valid = receipt(manifest, OcrProviderComparisonStatus.COMPARABLE)

        val identityError = assertFailsWith<IllegalArgumentException> {
            OcrProviderComparisonReceiptValidator.validate(
                valid.copy(providers = valid.providers.mapIndexed { index, provider ->
                    if (index == 1) provider.copy(identity = provider.identity.copy(imageDigest = "latest")) else provider
                }),
                manifest,
            )
        }
        identityError.message.orEmpty().shouldContain("image digest")

        val json = OcrProviderComparisonReceipt.encode(valid).decodeToString()
        val unknownFieldError = assertFailsWith<IllegalArgumentException> {
            OcrProviderComparisonReceipt.decode(
                (json.removeSuffix("}") + ",\"unexpected\":true}").encodeToByteArray(),
            )
        }
        unknownFieldError.message.orEmpty().shouldContain("JSON")

        val oversizedError = assertFailsWith<IllegalArgumentException> {
            OcrProviderComparisonReceipt.decode(ByteArray(512_001) { 'x'.code.toByte() })
        }
        oversizedError.message.orEmpty().shouldContain("byte size")

        val textFixtureId = manifest.fixtures.first { it.expectedOutcome == OcrBenchmarkExpectedOutcome.TEXT }.fixtureId
        val inconsistentError = assertFailsWith<IllegalArgumentException> {
            OcrProviderComparisonReceiptValidator.validate(
                valid.copy(providers = valid.providers.map { provider ->
                    provider.copy(fixtures = provider.fixtures.map { result ->
                        if (result.fixtureId == textFixtureId) {
                            result.copy(
                                actualOutcome = OcrBenchmarkExpectedOutcome.TEXT,
                                text = "",
                                geometry = emptyList(),
                                outputSha256 = sha256Hex(ByteArray(0)),
                            )
                        } else {
                            result
                        }
                    })
                }),
                manifest,
            )
        }
        inconsistentError.message.orEmpty().shouldContain("TEXT result")
    }

    private fun receipt(
        manifest: OcrBenchmarkCorpusManifest,
        status: OcrProviderComparisonStatus,
    ): OcrProviderComparisonReceipt {
        val manifestSha256 = OcrBenchmarkCorpusV2.manifestSha256()
        val providers = listOf("tesseract", "paddleocr")
            .take(if (status == OcrProviderComparisonStatus.COMPARABLE) 2 else 1)
            .mapIndexed { index, provider ->
                OcrProviderReceipt(
                    manifestSha256 = manifestSha256,
                    identity = OcrProviderIdentity(
                        provider = provider,
                        runtime = if (index == 0) "tesseract-cli" else "paddle-http",
                        model = if (status == OcrProviderComparisonStatus.COMPARABLE) {
                            "sha256:${"b".repeat(64)}"
                        } else {
                            "tesseract@5.5.0"
                        },
                        imageDigest = if (status == OcrProviderComparisonStatus.COMPARABLE) {
                            "sha256:${("a".repeat(64 - index))}${"c".repeat(index)}"
                        } else {
                            "host"
                        },
                    ),
                    fixtures = fixtureRows(manifest),
                )
            }

        return OcrProviderComparisonReceipt(
            schemaVersion = 1,
            issue = 544,
            status = status,
            manifestSha256 = manifestSha256,
            providers = providers,
            comparison = if (status == OcrProviderComparisonStatus.COMPARABLE) {
                OcrProviderComparisonSummary(
                    baselineProvider = "tesseract",
                    candidateProvider = "paddleocr",
                    comparedFixtureCount = manifest.fixtures.size + manifest.negatives.size,
                    cer = 0.0,
                    wer = 0.0,
                    throughputDeltaPercent = 10.0,
                    rssPeakDeltaBytes = 1024,
                )
            } else {
                null
            },
        )
    }

    private fun fixtureRows(manifest: OcrBenchmarkCorpusManifest): List<OcrProviderFixtureResult> =
        manifest.fixtures.map { entry ->
            val fixture = OcrBenchmarkCorpusV2.loadFixture(entry.fixtureId)
            OcrProviderFixtureResult(
                fixtureId = entry.fixtureId,
                scenario = entry.scenario.value,
                expectedOutcome = entry.expectedOutcome,
                actualOutcome = entry.expectedOutcome,
                text = fixture.normalizedText,
                geometry = fixture.boxes.map { box ->
                    OcrProviderGeometry(
                        boxId = box.boxId,
                        pageIndex = box.pageIndex,
                        text = box.text,
                        x = box.x,
                        y = box.y,
                        width = box.width,
                        height = box.height,
                        order = box.order,
                        confidence = null,
                    )
                },
                errorMessage = null,
                coldLatencyNanos = 10_000,
                warmLatencyNanos = 5_000,
                throughputOpsPerSecond = 100.0,
                warmIterations = 3,
                rssBeforeBytes = 10_000,
                rssPeakBytes = 12_000,
                outputSha256 = sha256Hex(fixture.normalizedText.encodeToByteArray()),
            )
        } + manifest.negatives.map { entry ->
            OcrProviderFixtureResult(
                fixtureId = entry.fixtureId,
                scenario = entry.scenario.value,
                expectedOutcome = entry.expectedOutcome,
                actualOutcome = OcrBenchmarkExpectedOutcome.ERROR,
                text = "",
                geometry = emptyList(),
                errorMessage = "malformed input",
                coldLatencyNanos = 10_000,
                warmLatencyNanos = 5_000,
                throughputOpsPerSecond = 100.0,
                warmIterations = 3,
                rssBeforeBytes = 10_000,
                rssPeakBytes = 12_000,
                outputSha256 = sha256Hex(ByteArray(0)),
            )
        }
}
