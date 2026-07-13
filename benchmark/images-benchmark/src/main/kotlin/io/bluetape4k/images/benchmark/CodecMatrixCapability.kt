package io.bluetape4k.images.benchmark

import java.io.Serializable
import kotlinx.serialization.Serializable as KotlinSerializable

internal enum class CodecMatrixCapabilitySupport {
    AVAILABLE,
    UNAVAILABLE,
    UNKNOWN,
}

internal data class CodecMatrixDirectionalCapability(
    val format: CodecMatrixFormat,
    val direction: CodecMatrixDirection,
    val support: CodecMatrixCapabilitySupport,
    val reason: String? = null,
)

internal data class CodecMatrixSmokeFixture(
    val backend: CodecMatrixBackendId,
    val scenario: CodecMatrixScenario,
    val dimensions: CodecMatrixDimensions,
    val jpegBytes: ByteArray,
    val jpegSha256: CodecMatrixSha256,
    val targetBytes: ByteArray?,
    val targetSha256: CodecMatrixSha256?,
)

internal interface CodecMatrixCodecOps {
    fun open(bytes: ByteArray): CodecMatrixCodecHandle
}

internal interface CodecMatrixCodecHandle : AutoCloseable {
    val width: Int
    val height: Int

    fun toBytes(format: CodecMatrixFormat): ByteArray
}

@KotlinSerializable
internal data class CodecMatrixSizeObservation(
    val key: CodecMatrixCellKey,
    val inputBytes: Long,
    val outputBytes: Long,
    val outputSha256: CodecMatrixSha256,
): Serializable {
    init {
        require(inputBytes > 0L) { "inputBytes must be positive" }
        require(outputBytes > 0L) { "outputBytes must be positive" }
    }

    companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

@KotlinSerializable
internal data class CodecMatrixSizeManifest(
    val schemaVersion: Int = CODEC_MATRIX_SCHEMA_VERSION,
    val runId: CodecMatrixRunId,
    val backend: CodecMatrixBackendId,
    val observations: List<CodecMatrixSizeObservation>,
): Serializable {
    init {
        require(schemaVersion == CODEC_MATRIX_SCHEMA_VERSION) { "unsupported schemaVersion: $schemaVersion" }
        require(observations.size <= CODEC_MATRIX_MAX_CELLS) { "size observations exceed the matrix limit" }
        require(observations.map(CodecMatrixSizeObservation::key).toSet().size == observations.size) {
            "size observation keys must be unique"
        }
    }

    companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

@KotlinSerializable
internal data class CodecMatrixExperimentalFixtureEntry(
    val scenario: CodecMatrixScenario,
    val input: CodecMatrixInput,
): Serializable {
    companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

@KotlinSerializable
internal data class CodecMatrixExperimentalFixtureManifest(
    val schemaVersion: Int = CODEC_MATRIX_SCHEMA_VERSION,
    val runId: CodecMatrixRunId,
    val producerBackend: CodecMatrixBackendId,
    val producerJdk: Int,
    val libvipsVersion: String? = null,
    val codecLibraryVersions: Map<String, String> = emptyMap(),
    val command: String = "prepareExperimentalCodecMatrixFixtures",
    val entries: List<CodecMatrixExperimentalFixtureEntry>,
): Serializable {
    init {
        require(schemaVersion == CODEC_MATRIX_SCHEMA_VERSION) { "unsupported schemaVersion: $schemaVersion" }
        require(producerJdk in 1..100) { "producerJdk is outside the accepted range" }
        require(entries.isNotEmpty()) { "experimental fixture entries must not be empty" }
        require(entries.map { it.scenario to it.input.format }.toSet().size == entries.size) {
            "experimental fixture entries must be unique"
        }
    }

    companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

internal class CodecMatrixCapabilityEvaluator(
    private val codecOps: CodecMatrixCodecOps,
) {
    fun evaluate(
        capability: CodecMatrixDirectionalCapability,
        fixture: CodecMatrixSmokeFixture,
        preflightStatus: CodecMatrixCellStatus,
    ): CodecMatrixCell {
        preflightTerminalCell(capability, fixture, preflightStatus)?.let { return it }
        when (capability.support) {
            CodecMatrixCapabilitySupport.UNAVAILABLE -> return terminalCell(
                capability,
                fixture,
                CodecMatrixCellStatus.UNSUPPORTED,
                CodecMatrixReasonCode.CAPABILITY_UNAVAILABLE,
                capability.reason ?: "codec operation is unavailable",
            )
            CodecMatrixCapabilitySupport.UNKNOWN -> return terminalCell(
                capability,
                fixture,
                CodecMatrixCellStatus.SKIPPED,
                CodecMatrixReasonCode.CAPABILITY_UNKNOWN,
                capability.reason ?: "codec operation support is unknown",
            )
            CodecMatrixCapabilitySupport.AVAILABLE -> Unit
        }

        val input = inputFor(capability, fixture) ?: return terminalCell(
            capability,
            fixture,
            CodecMatrixCellStatus.ERROR,
            CodecMatrixReasonCode.FIXTURE_INVALID,
            "pinned target input is required for decode",
        )
        if (capability.direction == CodecMatrixDirection.DECODE &&
            CodecMatrixJson.sha256(input) != fixture.targetSha256
        ) {
            return terminalCell(
                capability,
                fixture,
                CodecMatrixCellStatus.ERROR,
                CodecMatrixReasonCode.FIXTURE_INVALID,
                "pinned target input hash differs",
            )
        }

        return try {
            val outputFormat = if (capability.direction == CodecMatrixDirection.ENCODE) {
                capability.format
            } else {
                CodecMatrixFormat.JPEG
            }
            val output = codecOps.open(input).use { handle ->
                require(handle.width == fixture.dimensions.width && handle.height == fixture.dimensions.height) {
                    "smoke input dimensions differ"
                }
                handle.toBytes(outputFormat)
            }
            require(output.isNotEmpty()) { "smoke output is empty" }
            require(codecMatrixMagic(outputFormat, output).valid) { "smoke output magic differs" }
            eligibleCell(capability, fixture)
        } catch (_: Exception) {
            terminalCell(
                capability,
                fixture,
                CodecMatrixCellStatus.FAILED_SMOKE,
                CodecMatrixReasonCode.SMOKE_FAILED,
                "${capability.direction.name.lowercase()} smoke failed",
            )
        }
    }

    private fun preflightTerminalCell(
        capability: CodecMatrixDirectionalCapability,
        fixture: CodecMatrixSmokeFixture,
        preflightStatus: CodecMatrixCellStatus,
    ): CodecMatrixCell? = when (preflightStatus) {
        CodecMatrixCellStatus.ELIGIBLE -> null
        CodecMatrixCellStatus.N_A -> terminalCell(
            capability,
            fixture,
            CodecMatrixCellStatus.N_A,
            CodecMatrixReasonCode.HOST_BINARY_INCOMPATIBLE,
            "host and selected backend are incompatible",
        )
        else -> terminalCell(
            capability,
            fixture,
            CodecMatrixCellStatus.ERROR,
            CodecMatrixReasonCode.EVIDENCE_INVALID,
            "preflight is not eligible",
        )
    }

    private fun inputFor(
        capability: CodecMatrixDirectionalCapability,
        fixture: CodecMatrixSmokeFixture,
    ): ByteArray? = when (capability.direction) {
        CodecMatrixDirection.ENCODE -> fixture.jpegBytes
        CodecMatrixDirection.DECODE -> fixture.targetBytes
    }

    private fun eligibleCell(
        capability: CodecMatrixDirectionalCapability,
        fixture: CodecMatrixSmokeFixture,
    ): CodecMatrixCell = CodecMatrixCell(
        key = cellKey(capability, fixture),
        status = CodecMatrixCellStatus.ELIGIBLE,
    ).also(CodecMatrixCell::validateEligibility)

    private fun terminalCell(
        capability: CodecMatrixDirectionalCapability,
        fixture: CodecMatrixSmokeFixture,
        status: CodecMatrixCellStatus,
        reasonCode: CodecMatrixReasonCode,
        reason: String,
    ): CodecMatrixCell = CodecMatrixCell(
        key = cellKey(capability, fixture),
        status = status,
        reasonCode = reasonCode,
        reason = sanitizeCodecMatrixText(reason),
        rerunGuidance = "rerun codec capability smoke",
    ).also(CodecMatrixCell::validateEligibility)

    private fun cellKey(
        capability: CodecMatrixDirectionalCapability,
        fixture: CodecMatrixSmokeFixture,
    ): CodecMatrixCellKey = CodecMatrixCellKey(
        backend = fixture.backend,
        scenario = fixture.scenario,
        format = capability.format,
        direction = capability.direction,
        inputSha256 = if (capability.direction == CodecMatrixDirection.ENCODE) {
            fixture.jpegSha256
        } else {
            fixture.targetSha256 ?: CodecMatrixSha256("0".repeat(64))
        },
    )
}
