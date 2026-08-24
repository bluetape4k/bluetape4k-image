package io.bluetape4k.images.benchmark

import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.text.Normalizer
import kotlin.math.abs

import kotlinx.serialization.Serializable as KotlinxSerializable

/** CER/WER receipt에서 사용하는 고정 정규화 정책입니다. */
@KotlinxSerializable
internal data class OcrBenchmarkMetricNormalization(
    val unicodeForm: String = "NFC",
    val lineEnding: String = "LF",
    val whitespace: OcrBenchmarkMetricWhitespacePolicy = OcrBenchmarkMetricWhitespacePolicy.COLLAPSE,
    val caseSensitive: Boolean = true,
) {
    init {
        require(unicodeForm == "NFC") { "OCR metric Unicode normalization must be NFC" }
        require(lineEnding == "LF") { "OCR metric line ending normalization must be LF" }
    }
}

@KotlinxSerializable
internal enum class OcrBenchmarkMetricWhitespacePolicy {
    COLLAPSE,
}

@KotlinxSerializable
internal enum class OcrBenchmarkMetricExclusionReason {
    @SerialName("VALID_BLANK")
    VALID_BLANK,
}

/** OCR prediction을 비교하기 전에 적용하는 결정적 NFC/LF/공백 정규화입니다. */
internal object OcrBenchmarkTextNormalizer {
    private val whitespace = Regex("[\\s\\p{Zs}]+")

    fun normalize(value: String): String {
        val lf = value.replace("\r\n", "\n").replace('\r', '\n')
        val nfc = Normalizer.normalize(lf, Normalizer.Form.NFC)
        return nfc.replace(whitespace, " ").trim()
    }

    fun words(value: String): List<String> =
        normalize(value)
            .replace('\n', ' ')
            .split(' ')
            .filter(String::isNotEmpty)
}

/** Unicode code-point CER와 공백 기준 token WER를 함께 계산합니다. */
internal data class OcrBenchmarkErrorRates(
    val characterEdits: Int,
    val referenceCharacters: Int,
    val cer: Double,
    val wordEdits: Int,
    val referenceWords: Int,
    val wer: Double,
) {
    companion object {
        fun score(reference: String, prediction: String): OcrBenchmarkErrorRates {
            val normalizedReference = OcrBenchmarkTextNormalizer.normalize(reference)
            val normalizedPrediction = OcrBenchmarkTextNormalizer.normalize(prediction)
            val referenceCharacters = normalizedReference.codePoints().toArray().toList()
            val predictionCharacters = normalizedPrediction.codePoints().toArray().toList()
            val referenceWords = OcrBenchmarkTextNormalizer.words(normalizedReference)
            val predictionWords = OcrBenchmarkTextNormalizer.words(normalizedPrediction)
            val characterEdits = editDistance(referenceCharacters, predictionCharacters)
            val wordEdits = editDistance(referenceWords, predictionWords)
            val characterDenominator = referenceCharacters.size.coerceAtLeast(1)
            val wordDenominator = referenceWords.size.coerceAtLeast(1)
            return OcrBenchmarkErrorRates(
                characterEdits = characterEdits,
                referenceCharacters = referenceCharacters.size,
                cer = characterEdits.toDouble() / characterDenominator,
                wordEdits = wordEdits,
                referenceWords = referenceWords.size,
                wer = wordEdits.toDouble() / wordDenominator,
            )
        }

        private fun <T> editDistance(reference: List<T>, prediction: List<T>): Int {
            var previous = IntArray(prediction.size + 1) { index -> index }
            reference.forEachIndexed { referenceIndex, referenceValue ->
                val current = IntArray(prediction.size + 1)
                current[0] = referenceIndex + 1
                prediction.forEachIndexed { predictionIndex, predictionValue ->
                    val substitution = if (referenceValue == predictionValue) 0 else 1
                    current[predictionIndex + 1] = minOf(
                        current[predictionIndex] + 1,
                        previous[predictionIndex + 1] + 1,
                        previous[predictionIndex] + substitution,
                    )
                }
                previous = current
            }
            return previous[prediction.size]
        }
    }
}

@KotlinxSerializable
internal data class OcrBenchmarkMetricRow(
    val fixtureId: String,
    val scenario: OcrBenchmarkCorpusScenario,
    val expectedOutcome: OcrBenchmarkExpectedOutcome,
    val referenceSha256: String,
    val includedInAggregate: Boolean,
    val predictionSha256: String? = null,
    val characterEdits: Int? = null,
    val referenceCharacters: Int? = null,
    val cer: Double? = null,
    val wordEdits: Int? = null,
    val referenceWords: Int? = null,
    val wer: Double? = null,
    val exclusionReason: OcrBenchmarkMetricExclusionReason? = null,
)

@KotlinxSerializable
internal data class OcrBenchmarkMetricSummary(
    val scoredFixtureCount: Int,
    val excludedFixtureCount: Int,
    val excludedNegativeCount: Int,
    val characterEdits: Int,
    val referenceCharacters: Int,
    val cer: Double,
    val wordEdits: Int,
    val referenceWords: Int,
    val wer: Double,
)

/** OCR metric raw rows와 weighted summary를 함께 고정하는 JSON receipt입니다. */
@KotlinxSerializable
internal data class OcrBenchmarkMetricReceipt(
    val schemaVersion: Int,
    val metric: String,
    val manifestSha256: String,
    val normalization: OcrBenchmarkMetricNormalization,
    val rows: List<OcrBenchmarkMetricRow>,
    val excludedNegativeFixtureIds: List<String>,
    val summary: OcrBenchmarkMetricSummary,
) {
    companion object {
        private val json = Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = false
            isLenient = false
        }

        fun create(
            manifest: OcrBenchmarkCorpusManifest,
            predictions: Map<String, String>,
            manifestSha256: String,
        ): OcrBenchmarkMetricReceipt {
            require(manifestSha256.matches(Regex("[0-9a-f]{64}"))) {
                "OCR metric manifest SHA-256 is invalid"
            }
            val textIds = manifest.fixtures
                .filter { fixture -> fixture.expectedOutcome == OcrBenchmarkExpectedOutcome.TEXT }
                .map(OcrBenchmarkCorpusFixtureEntry::fixtureId)
                .toSet()
            require(predictions.keys == textIds) {
                "OCR metric predictions must match TEXT fixture IDs exactly"
            }
            val rows = manifest.fixtures.map { fixture ->
                val reference = OcrBenchmarkCorpusV2.loadFixture(fixture.fixtureId).normalizedText
                when (fixture.expectedOutcome) {
                    OcrBenchmarkExpectedOutcome.TEXT -> {
                        val prediction = requireNotNull(predictions[fixture.fixtureId])
                        val rates = OcrBenchmarkErrorRates.score(reference, prediction)
                        OcrBenchmarkMetricRow(
                            fixtureId = fixture.fixtureId,
                            scenario = fixture.scenario,
                            expectedOutcome = fixture.expectedOutcome,
                            referenceSha256 = fixture.groundTruth.text.sha256,
                            includedInAggregate = true,
                            predictionSha256 = sha256(prediction.encodeToByteArray()),
                            characterEdits = rates.characterEdits,
                            referenceCharacters = rates.referenceCharacters,
                            cer = rates.cer,
                            wordEdits = rates.wordEdits,
                            referenceWords = rates.referenceWords,
                            wer = rates.wer,
                        )
                    }
                    OcrBenchmarkExpectedOutcome.EMPTY -> OcrBenchmarkMetricRow(
                        fixtureId = fixture.fixtureId,
                        scenario = fixture.scenario,
                        expectedOutcome = fixture.expectedOutcome,
                        referenceSha256 = fixture.groundTruth.text.sha256,
                        includedInAggregate = false,
                        exclusionReason = OcrBenchmarkMetricExclusionReason.VALID_BLANK,
                    )
                    OcrBenchmarkExpectedOutcome.ERROR -> {
                        error("ERROR OCR fixtures must not be metric receipt rows: ${fixture.fixtureId}")
                    }
                }
            }
            return OcrBenchmarkMetricReceipt(
                schemaVersion = 1,
                metric = "CER_WER",
                manifestSha256 = manifestSha256,
                normalization = OcrBenchmarkMetricNormalization(),
                rows = rows,
                excludedNegativeFixtureIds = manifest.negatives.map(OcrBenchmarkNegativeFixtureReceipt::fixtureId),
                summary = summarize(rows, manifest.negatives.size),
            )
        }

        fun encode(receipt: OcrBenchmarkMetricReceipt): ByteArray =
            (json.encodeToString(receipt) + "\n").encodeToByteArray()

        fun decode(bytes: ByteArray): OcrBenchmarkMetricReceipt =
            json.decodeFromString(bytes.decodeToString())

        private fun summarize(rows: List<OcrBenchmarkMetricRow>, negativeCount: Int): OcrBenchmarkMetricSummary {
            val included = rows.filter(OcrBenchmarkMetricRow::includedInAggregate)
            val characterEdits = included.sumOf { requireNotNull(it.characterEdits) }
            val referenceCharacters = included.sumOf { requireNotNull(it.referenceCharacters) }
            val wordEdits = included.sumOf { requireNotNull(it.wordEdits) }
            val referenceWords = included.sumOf { requireNotNull(it.referenceWords) }
            return OcrBenchmarkMetricSummary(
                scoredFixtureCount = included.size,
                excludedFixtureCount = rows.size - included.size,
                excludedNegativeCount = negativeCount,
                characterEdits = characterEdits,
                referenceCharacters = referenceCharacters,
                cer = characterEdits.toDouble() / referenceCharacters.coerceAtLeast(1),
                wordEdits = wordEdits,
                referenceWords = referenceWords,
                wer = wordEdits.toDouble() / referenceWords.coerceAtLeast(1),
            )
        }

        private fun sha256(bytes: ByteArray): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { byte -> "%02x".format(byte) }
    }
}

/** raw row, exclusion set, weighted summary의 교차 일관성을 검증합니다. */
internal object OcrBenchmarkMetricReceiptValidator {
    private const val EPSILON = 0.000_000_001

    fun validate(
        receipt: OcrBenchmarkMetricReceipt,
        manifest: OcrBenchmarkCorpusManifest,
        expectedManifestSha256: String? = null,
    ) {
        require(receipt.schemaVersion == 1) { "OCR metric receipt schema version differs" }
        require(receipt.metric == "CER_WER") { "OCR metric receipt metric differs" }
        require(receipt.manifestSha256.matches(Regex("[0-9a-f]{64}"))) {
            "OCR metric receipt manifest SHA-256 is invalid"
        }
        expectedManifestSha256?.let { expected ->
            require(expected.matches(Regex("[0-9a-f]{64}"))) {
                "Expected OCR metric manifest SHA-256 is invalid"
            }
            require(receipt.manifestSha256 == expected) {
                "OCR metric receipt manifest SHA-256 differs"
            }
        }
        require(receipt.normalization == OcrBenchmarkMetricNormalization()) {
            "OCR metric normalization policy differs"
        }
        val expectedRows = manifest.fixtures
        val expectedIds = expectedRows.map(OcrBenchmarkCorpusFixtureEntry::fixtureId)
        require(receipt.rows.map(OcrBenchmarkMetricRow::fixtureId).toSet().size == receipt.rows.size) {
            "OCR metric receipt fixture IDs must be unique"
        }
        require(receipt.rows.map(OcrBenchmarkMetricRow::fixtureId) == expectedIds) {
            "OCR metric receipt fixture coverage differs"
        }
        expectedRows.zip(receipt.rows).forEach { (fixture, row) ->
            require(row.scenario == fixture.scenario && row.expectedOutcome == fixture.expectedOutcome) {
                "OCR metric row classification differs: ${fixture.fixtureId}"
            }
            require(row.referenceSha256 == fixture.groundTruth.text.sha256) {
                "OCR metric reference hash differs: ${fixture.fixtureId}"
            }
            when (fixture.expectedOutcome) {
                OcrBenchmarkExpectedOutcome.TEXT -> validateTextRow(row, fixture.fixtureId)
                OcrBenchmarkExpectedOutcome.EMPTY -> validateEmptyRow(row, fixture.fixtureId)
                OcrBenchmarkExpectedOutcome.ERROR -> error("ERROR fixture must not be a positive metric row")
            }
        }
        val expectedNegativeIds = manifest.negatives.map(OcrBenchmarkNegativeFixtureReceipt::fixtureId)
        require(receipt.excludedNegativeFixtureIds == expectedNegativeIds) {
            "OCR metric negative fixture exclusions differ"
        }
        validateSummary(receipt.summary, receipt.rows, expectedNegativeIds.size)
    }

    fun validateJson(
        bytes: ByteArray,
        manifest: OcrBenchmarkCorpusManifest,
        expectedManifestSha256: String? = null,
    ) {
        validate(OcrBenchmarkMetricReceipt.decode(bytes), manifest, expectedManifestSha256)
    }

    private fun validateTextRow(row: OcrBenchmarkMetricRow, fixtureId: String) {
        require(row.includedInAggregate && row.exclusionReason == null) {
            "TEXT metric row must be included: $fixtureId"
        }
        require(row.predictionSha256?.matches(Regex("[0-9a-f]{64}")) == true) {
            "TEXT metric prediction hash is invalid: $fixtureId"
        }
        require(row.characterEdits != null && row.characterEdits >= 0) {
            "TEXT metric character edits are invalid: $fixtureId"
        }
        require(row.referenceCharacters != null && row.referenceCharacters > 0) {
            "TEXT metric reference character count is invalid: $fixtureId"
        }
        require(row.wordEdits != null && row.wordEdits >= 0) {
            "TEXT metric word edits are invalid: $fixtureId"
        }
        require(row.referenceWords != null && row.referenceWords > 0) {
            "TEXT metric reference word count is invalid: $fixtureId"
        }
        requireFinite(row.cer, "CER", fixtureId)
        requireFinite(row.wer, "WER", fixtureId)
        require(abs(requireNotNull(row.cer) - row.characterEdits.toDouble() / row.referenceCharacters) < EPSILON) {
            "TEXT metric CER does not match edits: $fixtureId"
        }
        require(abs(requireNotNull(row.wer) - row.wordEdits.toDouble() / row.referenceWords) < EPSILON) {
            "TEXT metric WER does not match edits: $fixtureId"
        }
    }

    private fun validateEmptyRow(row: OcrBenchmarkMetricRow, fixtureId: String) {
        require(!row.includedInAggregate && row.exclusionReason == OcrBenchmarkMetricExclusionReason.VALID_BLANK) {
            "EMPTY metric row must be excluded as VALID_BLANK: $fixtureId"
        }
        require(
            row.predictionSha256 == null && row.characterEdits == null && row.referenceCharacters == null &&
                row.cer == null && row.wordEdits == null && row.referenceWords == null && row.wer == null
        ) {
            "EMPTY metric row must not contain CER/WER values: $fixtureId"
        }
    }

    private fun validateSummary(
        summary: OcrBenchmarkMetricSummary,
        rows: List<OcrBenchmarkMetricRow>,
        negativeCount: Int,
    ) {
        val included = rows.filter(OcrBenchmarkMetricRow::includedInAggregate)
        val characterEdits = included.sumOf { requireNotNull(it.characterEdits) }
        val referenceCharacters = included.sumOf { requireNotNull(it.referenceCharacters) }
        val wordEdits = included.sumOf { requireNotNull(it.wordEdits) }
        val referenceWords = included.sumOf { requireNotNull(it.referenceWords) }
        require(summary.scoredFixtureCount == included.size) { "OCR metric scored fixture count differs" }
        require(summary.excludedFixtureCount == rows.size - included.size) {
            "OCR metric excluded fixture count differs"
        }
        require(summary.excludedNegativeCount == negativeCount) { "OCR metric excluded negative count differs" }
        require(summary.characterEdits == characterEdits && summary.referenceCharacters == referenceCharacters) {
            "OCR metric CER summary counts differ"
        }
        require(summary.wordEdits == wordEdits && summary.referenceWords == referenceWords) {
            "OCR metric WER summary counts differ"
        }
        requireFinite(summary.cer, "CER", "summary")
        requireFinite(summary.wer, "WER", "summary")
        require(abs(summary.cer - characterEdits.toDouble() / referenceCharacters.coerceAtLeast(1)) < EPSILON) {
            "OCR metric summary CER differs"
        }
        require(abs(summary.wer - wordEdits.toDouble() / referenceWords.coerceAtLeast(1)) < EPSILON) {
            "OCR metric summary WER differs"
        }
    }

    private fun requireFinite(value: Double?, metric: String, fixtureId: String) {
        require(value != null && value.isFinite() && value >= 0.0) {
            "OCR metric $metric is invalid: $fixtureId"
        }
    }
}
