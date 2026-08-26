package io.bluetape4k.images.benchmark

import tools.jackson.core.JacksonException
import tools.jackson.core.JsonParser
import tools.jackson.core.StreamReadConstraints
import tools.jackson.core.json.JsonFactory
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import java.io.IOException
import java.io.Serializable

/** OCR provider 비교 결과가 같은 corpus와 실행 조건을 가리키도록 고정하는 상태입니다. */
internal enum class OcrProviderComparisonStatus {
    BASELINE_ONLY,
    COMPARABLE,
}

/** provider 실행 환경·모델·이미지의 provenance 식별자입니다. */
internal data class OcrProviderIdentity(
    val provider: String,
    val runtime: String,
    val model: String,
    val imageDigest: String,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/** provider OCR geometry 한 건의 pixel 좌표와 선택적 confidence입니다. */
internal data class OcrProviderGeometry(
    val boxId: String,
    val pageIndex: Int,
    val text: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val order: Int,
    val confidence: Double?,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/** provider 하나의 fixture별 품질·성능·메모리 관측값입니다. */
internal data class OcrProviderFixtureResult(
    val fixtureId: String,
    val scenario: OcrBenchmarkCorpusScenario,
    val expectedOutcome: OcrBenchmarkExpectedOutcome,
    val actualOutcome: OcrBenchmarkExpectedOutcome,
    val text: String,
    val geometry: List<OcrProviderGeometry>,
    val errorMessage: String?,
    val coldLatencyNanos: Long,
    val warmLatencyNanos: Long,
    val throughputOpsPerSecond: Double,
    val warmIterations: Int,
    val rssBeforeBytes: Long,
    val rssPeakBytes: Long,
    val outputSha256: String,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/** 동일 corpus에서 한 provider가 남긴 전체 receipt입니다. */
internal data class OcrProviderReceipt(
    val manifestSha256: String,
    val identity: OcrProviderIdentity,
    val fixtures: List<OcrProviderFixtureResult>,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/** 두 provider 결과를 비교할 때만 존재하는 요약 지표입니다. */
internal data class OcrProviderComparisonSummary(
    val baselineProvider: String,
    val candidateProvider: String,
    val comparedFixtureCount: Int,
    val cer: Double,
    val wer: Double,
    val throughputDeltaPercent: Double,
    val rssPeakDeltaBytes: Long,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Issue #544 provider-neutral OCR 비교 receipt입니다.
 *
 * 이 계약은 production OCR API나 provider dependency를 추가하지 않습니다. `BASELINE_ONLY`
 * 는 Tesseract 기준선만 기록하며 adoption 결론을 만들 수 없고, `COMPARABLE`만 두 개의
 * immutable provider identity와 비교 요약을 요구합니다.
 */
internal data class OcrProviderComparisonReceipt(
    val schemaVersion: Int,
    val issue: Int,
    val status: OcrProviderComparisonStatus,
    val manifestSha256: String,
    val providers: List<OcrProviderReceipt>,
    val comparison: OcrProviderComparisonSummary?,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
        private const val MAX_JSON_BYTES = 512_000

        private val mapper: JsonMapper by lazy {
            val constraints = StreamReadConstraints.builder()
                .maxNestingDepth(64)
                .maxDocumentLength(MAX_JSON_BYTES.toLong())
                .maxTokenCount(MAX_JSON_BYTES.toLong())
                .maxStringLength(64_000)
                .maxNameLength(256)
                .build()
            val factory = JsonFactory.builder()
                .streamReadConstraints(constraints)
                .build()
            JsonMapper.builder(factory)
                .addModule(kotlinModule())
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build()
        }

        /** Jackson3 JSON을 UTF-8 receipt bytes로 인코딩합니다. */
        fun encode(receipt: OcrProviderComparisonReceipt): ByteArray =
            (mapper.writeValueAsString(receipt) + "\n").encodeToByteArray()

        /** bounded Jackson3 parser로 receipt를 복원하고 trailing data를 거부합니다. */
        fun decode(bytes: ByteArray): OcrProviderComparisonReceipt {
            require(bytes.isNotEmpty() && bytes.size <= MAX_JSON_BYTES) {
                "OCR comparison receipt JSON byte size is out of bounds"
            }
            val parser = try {
                mapper.createParser(bytes)
            } catch (error: JacksonException) {
                throw IllegalArgumentException("OCR comparison receipt JSON is invalid", error)
            }
            try {
                val receipt = mapper.readValue(parser, OcrProviderComparisonReceipt::class.java)
                require(parser.nextToken() == null) {
                    "OCR comparison receipt JSON has trailing data"
                }
                return receipt
            } catch (error: IllegalArgumentException) {
                throw error
            } catch (error: JacksonException) {
                throw IllegalArgumentException("OCR comparison receipt JSON is invalid", error)
            } catch (error: IOException) {
                throw IllegalArgumentException("OCR comparison receipt JSON could not be read", error)
            } finally {
                parser.close()
            }
        }
    }
}

/** OCR 비교 receipt의 corpus·provenance·payload를 fail-closed로 검증합니다. */
internal object OcrProviderComparisonReceiptValidator {
    private val sha256Pattern = Regex("[0-9a-f]{64}")
    private val providerPattern = Regex("[a-z0-9][a-z0-9._-]{1,63}")
    private const val MAX_TEXT_CHARS = 64_000
    private const val MAX_GEOMETRY_ENTRIES = 4_096

    fun validate(
        receipt: OcrProviderComparisonReceipt,
        manifest: OcrBenchmarkCorpusManifest,
    ) {
        require(receipt.schemaVersion == 1) { "OCR comparison receipt schema version differs" }
        require(receipt.issue == 544) { "OCR comparison receipt issue differs" }
        require(receipt.manifestSha256.matches(sha256Pattern)) {
            "OCR comparison manifest SHA-256 is invalid"
        }
        require(receipt.manifestSha256 == OcrBenchmarkCorpusV2.manifestSha256()) {
            "OCR comparison manifest SHA-256 differs from corpus v2"
        }
        require(receipt.providers.isNotEmpty()) { "OCR comparison providers must not be empty" }
        when (receipt.status) {
            OcrProviderComparisonStatus.BASELINE_ONLY -> {
                require(receipt.providers.size == 1) {
                    "BASELINE_ONLY receipt must contain exactly one provider"
                }
                require(receipt.comparison == null) {
                    "BASELINE_ONLY receipt must not contain comparison summary"
                }
            }

            OcrProviderComparisonStatus.COMPARABLE -> {
                require(receipt.providers.size == 2) {
                    "COMPARABLE receipt must contain exactly two providers"
                }
                require(receipt.comparison != null) {
                    "COMPARABLE receipt must contain comparison summary"
                }
            }
        }

        val providerNames = receipt.providers.map { provider -> provider.identity.provider }
        require(providerNames.distinct().size == providerNames.size) {
            "OCR comparison provider identities must be unique"
        }
        val expectedFixtures = expectedFixtures(manifest)
        receipt.providers.forEach { provider ->
            require(provider.manifestSha256 == receipt.manifestSha256) {
                "OCR comparison provider manifest SHA-256 differs"
            }
            validateIdentity(provider.identity, receipt.status)
            require(provider.fixtures.map(OcrProviderFixtureResult::fixtureId) == expectedFixtures.keys.toList()) {
                "OCR comparison fixture coverage differs"
            }
            provider.fixtures.forEach { result ->
                val expected = requireNotNull(expectedFixtures[result.fixtureId]) {
                    "OCR comparison fixture is unknown: ${result.fixtureId}"
                }
                validateResult(result, expected)
            }
        }

        receipt.comparison?.let { summary ->
            require(summary.baselineProvider != summary.candidateProvider) {
                "OCR comparison baseline and candidate providers must differ"
            }
            require(summary.baselineProvider in providerNames && summary.candidateProvider in providerNames) {
                "OCR comparison summary provider is unknown"
            }
            require(summary.comparedFixtureCount == expectedFixtures.size) {
                "OCR comparison summary fixture count differs"
            }
            require(summary.cer.isFinite() && summary.cer in 0.0..1.0) {
                "OCR comparison CER is invalid"
            }
            require(summary.wer.isFinite() && summary.wer in 0.0..1.0) {
                "OCR comparison WER is invalid"
            }
            require(summary.throughputDeltaPercent.isFinite()) {
                "OCR comparison throughput delta is invalid"
            }
        }
    }

    private fun validateIdentity(
        identity: OcrProviderIdentity,
        status: OcrProviderComparisonStatus,
    ) {
        require(identity.provider.matches(providerPattern)) {
            "OCR comparison provider identity is invalid"
        }
        require(identity.runtime.isNotBlank() && identity.runtime == identity.runtime.trim()) {
            "OCR comparison runtime identity is incomplete"
        }
        require(identity.model.isNotBlank() && identity.model == identity.model.trim()) {
            "OCR comparison model identity is incomplete"
        }
        when (status) {
            OcrProviderComparisonStatus.BASELINE_ONLY -> {
                require(identity.imageDigest == "host" || identity.imageDigest.matches(sha256Pattern.withPrefix())) {
                    "OCR comparison baseline image identity is invalid"
                }
            }

            OcrProviderComparisonStatus.COMPARABLE -> {
                require(identity.model.matches(sha256Pattern.withPrefix())) {
                    "OCR comparison model identity must be immutable"
                }
                require(identity.imageDigest.matches(sha256Pattern.withPrefix())) {
                    "OCR comparison image digest must be immutable"
                }
            }
        }
    }

    private fun validateResult(
        result: OcrProviderFixtureResult,
        expected: ExpectedFixture,
    ) {
        require(result.scenario == expected.scenario && result.expectedOutcome == expected.outcome) {
            "OCR comparison fixture classification differs: ${result.fixtureId}"
        }
        require(result.text.length <= MAX_TEXT_CHARS) {
            "OCR comparison text is too large: ${result.fixtureId}"
        }
        require(result.coldLatencyNanos > 0 && result.warmLatencyNanos > 0) {
            "OCR comparison latency must be positive: ${result.fixtureId}"
        }
        require(result.throughputOpsPerSecond.isFinite() && result.throughputOpsPerSecond > 0.0) {
            "OCR comparison throughput must be positive: ${result.fixtureId}"
        }
        require(result.warmIterations > 0) {
            "OCR comparison warm iterations must be positive: ${result.fixtureId}"
        }
        require(result.rssBeforeBytes > 0 && result.rssPeakBytes >= result.rssBeforeBytes) {
            "OCR comparison RSS values are invalid: ${result.fixtureId}"
        }
        require(result.outputSha256.matches(sha256Pattern)) {
            "OCR comparison output SHA-256 is invalid: ${result.fixtureId}"
        }
        require(result.outputSha256 == sha256Hex(result.text.encodeToByteArray())) {
            "OCR comparison output SHA-256 differs: ${result.fixtureId}"
        }
        when (result.actualOutcome) {
            OcrBenchmarkExpectedOutcome.TEXT -> {
                require(result.text.isNotBlank() && result.geometry.isNotEmpty()) {
                    "OCR comparison TEXT result is incomplete: ${result.fixtureId}"
                }
                require(result.errorMessage == null) {
                    "OCR comparison TEXT result must not contain an error: ${result.fixtureId}"
                }
            }

            OcrBenchmarkExpectedOutcome.EMPTY -> {
                require(result.text.isBlank() && result.geometry.isEmpty()) {
                    "OCR comparison EMPTY result is inconsistent: ${result.fixtureId}"
                }
                require(result.errorMessage == null) {
                    "OCR comparison EMPTY result must not contain an error: ${result.fixtureId}"
                }
            }

            OcrBenchmarkExpectedOutcome.ERROR -> {
                require(result.text.isBlank() && result.geometry.isEmpty()) {
                    "OCR comparison ERROR result is inconsistent: ${result.fixtureId}"
                }
                require(!result.errorMessage.isNullOrBlank()) {
                    "OCR comparison ERROR result must contain an error: ${result.fixtureId}"
                }
            }
        }
        require(result.geometry.size <= MAX_GEOMETRY_ENTRIES) {
            "OCR comparison geometry is too large: ${result.fixtureId}"
        }
        require(result.geometry.map(OcrProviderGeometry::boxId).distinct().size == result.geometry.size) {
            "OCR comparison geometry box IDs must be unique: ${result.fixtureId}"
        }
        require(result.geometry.map(OcrProviderGeometry::order) == result.geometry.indices.toList()) {
            "OCR comparison geometry order must be contiguous: ${result.fixtureId}"
        }
        result.geometry.forEach { box ->
            require(
                box.boxId.isNotBlank() && box.pageIndex == 0 && box.text.isNotBlank() &&
                    box.x >= 0 && box.y >= 0 && box.width > 0 && box.height > 0 &&
                    (expected.width == null ||
                        (box.x <= expected.width && box.width <= expected.width - box.x)) &&
                    (expected.height == null ||
                        (box.y <= expected.height && box.height <= expected.height - box.y))
            ) {
                "OCR comparison geometry is invalid: ${result.fixtureId}"
            }
            require(box.confidence == null || (box.confidence.isFinite() && box.confidence in 0.0..1.0)) {
                "OCR comparison geometry confidence is invalid: ${result.fixtureId}"
            }
        }
    }

    private data class ExpectedFixture(
        val scenario: OcrBenchmarkCorpusScenario,
        val outcome: OcrBenchmarkExpectedOutcome,
        val width: Int?,
        val height: Int?,
    )

    private fun expectedFixtures(
        manifest: OcrBenchmarkCorpusManifest,
    ): LinkedHashMap<String, ExpectedFixture> =
        linkedMapOf<String, ExpectedFixture>().apply {
            manifest.fixtures.forEach { entry ->
                put(
                    entry.fixtureId,
                    ExpectedFixture(
                        scenario = entry.scenario,
                        outcome = entry.expectedOutcome,
                        width = entry.resource.width,
                        height = entry.resource.height,
                    ),
                )
            }
            manifest.negatives.forEach { entry ->
                put(
                    entry.fixtureId,
                    ExpectedFixture(
                        scenario = entry.scenario,
                        outcome = entry.expectedOutcome,
                        width = null,
                        height = null,
                    ),
                )
            }
        }

    private fun Regex.withPrefix(): Regex = Regex("sha256:$pattern")
}
