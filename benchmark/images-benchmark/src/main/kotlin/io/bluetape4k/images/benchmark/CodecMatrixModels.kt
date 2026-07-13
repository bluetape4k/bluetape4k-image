package io.bluetape4k.images.benchmark

import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requirePositiveNumber
import java.io.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable as KotlinSerializable

internal const val CODEC_MATRIX_SCHEMA_VERSION: Int = 1
internal const val CODEC_MATRIX_MAX_CELLS: Int = 64
internal const val CODEC_MATRIX_MAX_ARTIFACTS: Int = 128

private val RUN_ID_REGEX = Regex("[a-z0-9][a-z0-9._-]{7,79}")
private val SHA_256_REGEX = Regex("[0-9a-f]{64}")
private val RELATIVE_PATH_REGEX = Regex("[A-Za-z0-9][A-Za-z0-9._/-]{0,255}")
private val WINDOWS_ABSOLUTE_PATH_REGEX = Regex("[A-Za-z]:.*")

@KotlinSerializable
internal enum class CodecMatrixScenario {
    @SerialName("web-photo")
    WEB_PHOTO,

    @SerialName("profile")
    PROFILE,
}

@KotlinSerializable
internal enum class CodecMatrixDirection {
    @SerialName("encode")
    ENCODE,

    @SerialName("decode")
    DECODE,
}

@KotlinSerializable
internal enum class CodecMatrixFormat {
    JPEG,
    PNG,
    WEBP,
    AVIF,
    HEIC,
}

@KotlinSerializable
internal enum class CodecMatrixTransformRecipe {
    @SerialName("cover-center-crop-v1")
    COVER_CENTER_CROP_V1,
}

@KotlinSerializable
internal enum class CodecMatrixBackendId {
    @SerialName("java21")
    JAVA21,

    @SerialName("java25")
    JAVA25,
}

@KotlinSerializable
internal enum class CodecMatrixCellStatus {
    ELIGIBLE,
    MEASURED,
    UNSUPPORTED,
    SKIPPED,

    @SerialName("N/A")
    N_A,

    FAILED_SMOKE,
    ERROR,
    ;

    val isTerminalUnmeasured: Boolean
        get() = this != ELIGIBLE && this != MEASURED
}

@KotlinSerializable
internal enum class CodecMatrixReasonCode {
    NONE,
    CAPABILITY_UNAVAILABLE,
    CAPABILITY_UNKNOWN,
    HOST_BINARY_INCOMPATIBLE,
    POLICY_HOLD,
    RUNTIME_INITIALIZATION_FAILED,
    BACKEND_IDENTITY_MISMATCH,
    FIXTURE_INVALID,
    SMOKE_FAILED,
    EVIDENCE_INVALID,
}

@KotlinSerializable
internal data class CodecMatrixRunId(
    val value: String,
): Serializable {
    init {
        require(RUN_ID_REGEX.matches(value)) {
            "runId must match ${RUN_ID_REGEX.pattern}"
        }
    }

    companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

@KotlinSerializable
internal data class CodecMatrixSha256(
    val value: String,
): Serializable {
    init {
        require(SHA_256_REGEX.matches(value)) { "sha256 must be 64 lowercase hexadecimal characters" }
    }

    companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

@KotlinSerializable
internal data class CodecMatrixRelativePath(
    val value: String,
): Serializable {
    init {
        value.requireNotBlank("path")
        require(value.length <= 256) { "path must contain at most 256 characters" }
        require(RELATIVE_PATH_REGEX.matches(value)) { "path contains unsupported characters" }
        require(!value.startsWith('/')) { "path must be repository-relative" }
        require(!WINDOWS_ABSOLUTE_PATH_REGEX.matches(value)) { "path must be repository-relative" }
        require('\\' !in value) { "path must use forward slashes" }
        require(value.split('/').none { it.isBlank() || it == "." || it == ".." }) {
            "path must not contain empty, current, or parent segments"
        }
    }

    companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

@KotlinSerializable
internal data class CodecMatrixDimensions(
    val width: Int,
    val height: Int,
): Serializable {
    init {
        width.requirePositiveNumber("width")
        height.requirePositiveNumber("height")
        require(width <= 100_000 && height <= 100_000) { "dimensions exceed the codec matrix limit" }
    }

    companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

@KotlinSerializable
internal data class CodecMatrixArtifact(
    val path: CodecMatrixRelativePath,
    val sha256: CodecMatrixSha256,
    val byteCount: Long,
): Serializable {
    init {
        byteCount.requirePositiveNumber("byteCount")
    }

    companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

@KotlinSerializable
internal data class CodecMatrixFileRecord(
    val path: CodecMatrixRelativePath,
    val sha256: CodecMatrixSha256,
    val byteCount: Long,
    val dimensions: CodecMatrixDimensions,
): Serializable {
    init {
        byteCount.requirePositiveNumber("byteCount")
    }

    companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

@KotlinSerializable
internal data class CodecMatrixMagic(
    val signature: String,
    val valid: Boolean,
): Serializable {
    init {
        signature.requireNotBlank("signature")
        require(signature.length <= 32) { "magic signature exceeds 32 characters" }
    }

    companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

@KotlinSerializable
internal data class CodecMatrixCodecOptions(
    val jpegQuality: Int,
    val jpegProgressive: Boolean,
    val pngCompression: Int,
    val webpLosslessLevel: Int,
    val webpQuality: Int,
    val webpMethod: Int,
    val webpLossless: Boolean,
    val webpNoAlpha: Boolean,
): Serializable {
    init {
        require(jpegQuality in 0..100) { "jpegQuality must be between 0 and 100" }
        require(pngCompression in 0..9) { "pngCompression must be between 0 and 9" }
        require(webpLosslessLevel in -1..9) { "webpLosslessLevel must be between -1 and 9" }
        require(webpQuality in 0..100) { "webpQuality must be between 0 and 100" }
        require(webpMethod in 0..6) { "webpMethod must be between 0 and 6" }
    }

    companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

@KotlinSerializable
internal data class CodecMatrixInput(
    val format: CodecMatrixFormat,
    val path: CodecMatrixRelativePath,
    val sha256: CodecMatrixSha256,
    val byteCount: Long,
    val dimensions: CodecMatrixDimensions,
    val magic: CodecMatrixMagic,
): Serializable {
    init {
        byteCount.requirePositiveNumber("byteCount")
    }

    companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

@KotlinSerializable
internal data class CodecMatrixFixtureEntry(
    val scenario: CodecMatrixScenario,
    val source: CodecMatrixFileRecord,
    val derived: CodecMatrixFileRecord,
    val inputs: List<CodecMatrixInput>,
): Serializable {
    init {
        require(inputs.isNotEmpty()) { "fixture inputs must not be empty" }
        require(inputs.size <= CodecMatrixFormat.entries.size) { "fixture input count exceeds known formats" }
        require(inputs.map(CodecMatrixInput::format).toSet().size == inputs.size) {
            "fixture input formats must be unique"
        }
    }

    companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

@KotlinSerializable
internal data class CodecMatrixFixtureManifest(
    val schemaVersion: Int = CODEC_MATRIX_SCHEMA_VERSION,
    val runId: CodecMatrixRunId,
    val recipe: CodecMatrixTransformRecipe,
    val options: CodecMatrixCodecOptions,
    val fixtures: List<CodecMatrixFixtureEntry>,
): Serializable {
    init {
        require(schemaVersion == CODEC_MATRIX_SCHEMA_VERSION) { "unsupported schemaVersion: $schemaVersion" }
        require(fixtures.isNotEmpty()) { "fixtures must not be empty" }
        require(fixtures.size <= CodecMatrixScenario.entries.size) { "fixture count exceeds known scenarios" }
        require(fixtures.map(CodecMatrixFixtureEntry::scenario).toSet().size == fixtures.size) {
            "fixture scenarios must be unique"
        }
    }

    companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

@KotlinSerializable
internal data class CodecMatrixCellKey(
    val backend: CodecMatrixBackendId,
    val scenario: CodecMatrixScenario,
    val format: CodecMatrixFormat,
    val direction: CodecMatrixDirection,
    val inputSha256: CodecMatrixSha256,
): Serializable {
    companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

@KotlinSerializable
internal data class CodecMatrixMetrics(
    val latencyMs: Double? = null,
    val allocationBytesPerOp: Double? = null,
    val inputBytes: Long? = null,
    val outputBytes: Long? = null,
    val outputSha256: CodecMatrixSha256? = null,
): Serializable {
    internal fun validateComplete() {
        require(latencyMs != null && latencyMs.isFinite() && latencyMs >= 0.0) {
            "measured latencyMs must be finite and non-negative"
        }
        require(allocationBytesPerOp != null && allocationBytesPerOp.isFinite() && allocationBytesPerOp >= 0.0) {
            "measured allocationBytesPerOp must be finite and non-negative"
        }
        require(inputBytes != null && inputBytes > 0L) { "measured inputBytes must be positive" }
        require(outputBytes != null && outputBytes > 0L) { "measured outputBytes must be positive" }
        require(outputSha256 != null) { "measured outputSha256 is required" }
    }

    companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

@KotlinSerializable
internal data class CodecMatrixCell(
    val key: CodecMatrixCellKey,
    val status: CodecMatrixCellStatus,
    val reasonCode: CodecMatrixReasonCode = CodecMatrixReasonCode.NONE,
    val reason: String? = null,
    val rerunGuidance: String? = null,
    val metrics: CodecMatrixMetrics? = null,
): Serializable {
    internal fun validateEligibility() {
        require(status != CodecMatrixCellStatus.MEASURED) {
            "eligibility evidence cannot claim MEASURED"
        }
        validateState()
    }

    internal fun validateFinalized() {
        validateState()
    }

    private fun validateState() {
        when (status) {
            CodecMatrixCellStatus.MEASURED -> {
                require(reasonCode == CodecMatrixReasonCode.NONE) { "MEASURED must not carry a reason code" }
                require(reason.isNullOrBlank()) { "MEASURED must not carry a reason" }
                require(rerunGuidance.isNullOrBlank()) { "MEASURED must not carry rerun guidance" }
                requireNotNull(metrics) { "MEASURED requires metrics" }.validateComplete()
            }

            CodecMatrixCellStatus.ELIGIBLE -> {
                require(reasonCode == CodecMatrixReasonCode.NONE) { "ELIGIBLE must not carry a reason code" }
                require(reason.isNullOrBlank()) { "ELIGIBLE must not carry a reason" }
                require(rerunGuidance.isNullOrBlank()) { "ELIGIBLE must not carry rerun guidance" }
                require(metrics == null) { "ELIGIBLE must not carry metrics" }
            }

            else -> {
                require(reasonCode != CodecMatrixReasonCode.NONE) { "$status requires a fixed reason code" }
                rerunGuidance.requireNotBlank("rerunGuidance")
                require(metrics == null) { "$status must not carry metrics" }
            }
        }
        reason?.let {
            it.requireNotBlank("reason")
            require(it.length <= 256) { "reason must contain at most 256 characters" }
        }
        rerunGuidance?.let {
            require(it.length <= 256) { "rerunGuidance must contain at most 256 characters" }
        }
    }

    companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

@KotlinSerializable
internal data class CodecMatrixEligibilityManifest(
    val schemaVersion: Int = CODEC_MATRIX_SCHEMA_VERSION,
    val runId: CodecMatrixRunId,
    val expectedCellCount: Int = 1,
    val cells: List<CodecMatrixCell>,
    val artifacts: List<CodecMatrixArtifact> = emptyList(),
): Serializable {
    internal fun validateEligibility(): CodecMatrixEligibilityManifest = apply {
        validateManifestShape(schemaVersion, expectedCellCount, cells, artifacts)
        cells.forEach(CodecMatrixCell::validateEligibility)
    }

    companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

@KotlinSerializable
internal data class CodecMatrixFinalizedManifest(
    val schemaVersion: Int = CODEC_MATRIX_SCHEMA_VERSION,
    val runId: CodecMatrixRunId,
    val expectedCellCount: Int = 1,
    val cells: List<CodecMatrixCell>,
    val artifacts: List<CodecMatrixArtifact> = emptyList(),
    val supersedes: CodecMatrixRunId? = null,
    val replacesFailedAttempt: CodecMatrixFailedAttemptReference? = null,
): Serializable {
    internal fun validateAccepted(): CodecMatrixFinalizedManifest = apply {
        validateManifestShape(schemaVersion, expectedCellCount, cells, artifacts)
        require(cells.none { cell ->
            cell.status == CodecMatrixCellStatus.ELIGIBLE ||
                    cell.status == CodecMatrixCellStatus.FAILED_SMOKE ||
                    cell.status == CodecMatrixCellStatus.ERROR
        }) { "accepted evidence contains a blocking status" }
        cells.forEach(CodecMatrixCell::validateFinalized)
    }

    companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

@KotlinSerializable
internal data class CodecMatrixFailedAttemptReference(
    val runId: CodecMatrixRunId,
    val manifestSha256: CodecMatrixSha256,
): Serializable {
    companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

@KotlinSerializable
internal data class CodecMatrixFailedAttemptManifest(
    val schemaVersion: Int = CODEC_MATRIX_SCHEMA_VERSION,
    val runId: CodecMatrixRunId,
    val expectedCellCount: Int,
    val cells: List<CodecMatrixCell>,
    val artifacts: List<CodecMatrixArtifact> = emptyList(),
): Serializable {
    internal fun validateFailedAttempt(): CodecMatrixFailedAttemptManifest = apply {
        validateManifestShape(schemaVersion, expectedCellCount, cells, artifacts)
        require(cells.any { cell ->
            cell.status == CodecMatrixCellStatus.FAILED_SMOKE || cell.status == CodecMatrixCellStatus.ERROR
        }) { "failed attempt evidence requires a blocking status" }
        require(cells.none { cell ->
            cell.status == CodecMatrixCellStatus.ELIGIBLE || cell.status == CodecMatrixCellStatus.MEASURED
        }) { "failed attempt evidence contains a non-terminal status" }
        cells.forEach(CodecMatrixCell::validateFinalized)
    }

    companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

private fun validateManifestShape(
    schemaVersion: Int,
    expectedCellCount: Int,
    cells: List<CodecMatrixCell>,
    artifacts: List<CodecMatrixArtifact>,
) {
    require(schemaVersion == CODEC_MATRIX_SCHEMA_VERSION) { "unsupported schemaVersion: $schemaVersion" }
    require(expectedCellCount in 1..CODEC_MATRIX_MAX_CELLS) { "expectedCellCount is outside the matrix limit" }
    require(cells.size == expectedCellCount) { "expected $expectedCellCount cells but found ${cells.size}" }
    require(cells.map(CodecMatrixCell::key).toSet().size == cells.size) { "matrix cells must be unique" }
    require(artifacts.size <= CODEC_MATRIX_MAX_ARTIFACTS) { "artifact count exceeds the matrix limit" }
    require(artifacts.map(CodecMatrixArtifact::path).toSet().size == artifacts.size) {
        "artifact paths must be unique"
    }
}
