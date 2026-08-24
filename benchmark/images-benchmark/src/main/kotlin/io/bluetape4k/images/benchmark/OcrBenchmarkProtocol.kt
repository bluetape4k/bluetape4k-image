package io.bluetape4k.images.benchmark

import kotlinx.serialization.json.Json
import java.security.MessageDigest

import kotlinx.serialization.Serializable as KotlinxSerializable

@KotlinxSerializable
internal data class OcrBenchmarkHostEnvelope(
    val os: String,
    val arch: String,
    val jvm: String,
    val tesseract: String,
    val tessdata: String,
    val languages: List<String>,
) {
    init {
        require(os.isNotBlank() && arch.isNotBlank() && jvm.isNotBlank() && tesseract.isNotBlank()) {
            "OCR protocol host envelope is incomplete"
        }
        require(tessdata.isNotBlank() && languages.isNotEmpty() && languages.all(String::isNotBlank)) {
            "OCR protocol tessdata envelope is incomplete"
        }
    }
}

@KotlinxSerializable
internal data class OcrBenchmarkProtocolEnvelope(
    val coldRuns: Int,
    val warmupRuns: Int,
    val warmRuns: Int,
    val throughputWindowMillis: Long,
    val rssUnit: String,
) {
    init {
        require(coldRuns == 1) { "OCR protocol coldRuns must be 1" }
        require(warmupRuns >= 1 && warmRuns >= 1) { "OCR protocol warm runs must be positive" }
        require(throughputWindowMillis >= 100) { "OCR protocol throughput window is too short" }
        require(rssUnit == "bytes") { "OCR protocol RSS unit must be bytes" }
    }
}

@KotlinxSerializable
internal data class OcrBenchmarkProtocolRow(
    val fixtureId: String,
    val scenario: OcrBenchmarkCorpusScenario,
    val expectedOutcome: OcrBenchmarkExpectedOutcome,
    val coldLatencyNanos: Long,
    val warmLatencyNanos: Long,
    val throughputOpsPerSecond: Double,
    val warmIterations: Int,
    val rssBeforeBytes: Long,
    val rssPeakBytes: Long,
    val outputSha256: String,
) {
    init {
        require(coldLatencyNanos > 0 && warmLatencyNanos > 0) {
            "OCR protocol latency must be positive: $fixtureId"
        }
        require(throughputOpsPerSecond.isFinite() && throughputOpsPerSecond > 0.0) {
            "OCR protocol throughput must be positive: $fixtureId"
        }
        require(warmIterations > 0) { "OCR protocol warm iteration count must be positive: $fixtureId" }
        require(rssBeforeBytes > 0 && rssPeakBytes >= rssBeforeBytes) {
            "OCR protocol RSS values are invalid: $fixtureId"
        }
        require(outputSha256.matches(Regex("[0-9a-f]{64}"))) {
            "OCR protocol output SHA-256 is invalid: $fixtureId"
        }
    }
}

/** cold/warm/throughput/RSS raw rows와 CER/WER receipt를 한 실행에 묶습니다. */
@KotlinxSerializable
internal data class OcrBenchmarkProtocolReceipt(
    val schemaVersion: Int,
    val issue: Int,
    val runId: String,
    val manifestSha256: String,
    val host: OcrBenchmarkHostEnvelope,
    val protocol: OcrBenchmarkProtocolEnvelope,
    val rows: List<OcrBenchmarkProtocolRow>,
    val metrics: OcrBenchmarkMetricReceipt,
) {
    companion object {
        private val json = Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = false
            isLenient = false
        }

        fun encode(receipt: OcrBenchmarkProtocolReceipt): ByteArray =
            (json.encodeToString(receipt) + "\n").encodeToByteArray()

        fun decode(bytes: ByteArray): OcrBenchmarkProtocolReceipt =
            json.decodeFromString(bytes.decodeToString())
    }
}

internal object OcrBenchmarkProtocolReceiptValidator {
    fun validate(receipt: OcrBenchmarkProtocolReceipt, manifest: OcrBenchmarkCorpusManifest) {
        require(receipt.schemaVersion == 1) { "OCR protocol receipt schema version differs" }
        require(receipt.issue == 565) { "OCR protocol receipt issue differs" }
        require(receipt.runId.matches(Regex("[a-z0-9][a-z0-9._-]{7,79}"))) {
            "OCR protocol run ID is invalid"
        }
        require(receipt.manifestSha256.matches(Regex("[0-9a-f]{64}"))) {
            "OCR protocol manifest SHA-256 is invalid"
        }
        require(receipt.metrics.manifestSha256 == receipt.manifestSha256) {
            "OCR protocol and metric manifest SHA-256 differ"
        }
        val expectedLanguages = manifest.fixtures
            .flatMap(OcrBenchmarkCorpusFixtureEntry::languages)
            .toSet()
        require(receipt.host.languages.toSet() == expectedLanguages) {
            "OCR protocol host languages differ"
        }
        OcrBenchmarkMetricReceiptValidator.validate(receipt.metrics, manifest, receipt.manifestSha256)
        val metricRows = receipt.metrics.rows.associateBy(OcrBenchmarkMetricRow::fixtureId)
        val expectedIds = manifest.fixtures.map(OcrBenchmarkCorpusFixtureEntry::fixtureId)
        require(receipt.rows.map(OcrBenchmarkProtocolRow::fixtureId).toSet().size == receipt.rows.size) {
            "OCR protocol fixture IDs must be unique"
        }
        require(receipt.rows.map(OcrBenchmarkProtocolRow::fixtureId) == expectedIds) {
            "OCR protocol fixture coverage differs"
        }
        manifest.fixtures.zip(receipt.rows).forEach { (fixture, row) ->
            require(row.scenario == fixture.scenario && row.expectedOutcome == fixture.expectedOutcome) {
                "OCR protocol fixture classification differs: ${fixture.fixtureId}"
            }
            require(row.warmIterations == receipt.protocol.warmRuns) {
                "OCR protocol warm iteration count differs: ${fixture.fixtureId}"
            }
            val metricRow = requireNotNull(metricRows[fixture.fixtureId]) {
                "OCR protocol metric row is missing: ${fixture.fixtureId}"
            }
            when (fixture.expectedOutcome) {
                OcrBenchmarkExpectedOutcome.TEXT -> require(row.outputSha256 == metricRow.predictionSha256) {
                    "OCR protocol output SHA-256 differs from metric prediction: ${fixture.fixtureId}"
                }
                OcrBenchmarkExpectedOutcome.EMPTY -> require(row.outputSha256 == sha256Hex(ByteArray(0))) {
                    "OCR protocol EMPTY output SHA-256 differs: ${fixture.fixtureId}"
                }
                OcrBenchmarkExpectedOutcome.ERROR -> error("ERROR fixture must not be a protocol row")
            }
        }
    }

    fun validateJson(bytes: ByteArray, manifest: OcrBenchmarkCorpusManifest) {
        validate(OcrBenchmarkProtocolReceipt.decode(bytes), manifest)
    }
}

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }
