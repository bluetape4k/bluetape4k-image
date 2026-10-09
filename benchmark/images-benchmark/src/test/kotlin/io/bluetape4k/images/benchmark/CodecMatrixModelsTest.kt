package io.bluetape4k.images.benchmark

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.images.benchmark.CodecMatrixCellStatus.ELIGIBLE
import io.bluetape4k.images.benchmark.CodecMatrixCellStatus.ERROR
import io.bluetape4k.images.benchmark.CodecMatrixCellStatus.FAILED_SMOKE
import io.bluetape4k.images.benchmark.CodecMatrixCellStatus.MEASURED
import io.bluetape4k.images.benchmark.CodecMatrixCellStatus.N_A
import io.bluetape4k.images.benchmark.CodecMatrixCellStatus.SKIPPED
import io.bluetape4k.images.benchmark.CodecMatrixCellStatus.UNSUPPORTED
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class CodecMatrixModelsTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `eligibility manifest cannot claim measured`() {
        assertFailsWith<IllegalArgumentException> {
            eligibilityManifest(MEASURED).validateEligibility()
        }
    }

    @Test
    fun `accepted manifest rejects blocking states`() {
        listOf(
            ELIGIBLE,
            FAILED_SMOKE,
            ERROR,
        ).forEach { status ->
            assertFailsWith<IllegalArgumentException> {
                finalizedManifest(status).validateAccepted()
            }
        }
    }

    @Test
    fun `measured cell requires complete numeric evidence`() {
        assertFailsWith<IllegalArgumentException> {
            measuredCell(metrics = CodecMatrixMetrics(latencyMs = 1.0)).validateFinalized()
        }
    }

    @Test
    fun `unmeasured cell requires fixed reason and rerun guidance`() {
        assertFailsWith<IllegalArgumentException> {
            matrixCell(
                status = UNSUPPORTED,
                reasonCode = CodecMatrixReasonCode.NONE,
                rerunGuidance = "",
            ).validateFinalized()
        }
    }

    @Test
    fun `run id and relative paths reject unsafe values`() {
        listOf("short", "Uppercase-id", "../escape-run").forEach { value ->
            assertFailsWith<IllegalArgumentException> {
                CodecMatrixRunId(value)
            }
        }
        listOf(
            "/tmp/result.json",
            "../result.json",
            "fixtures/../result.json",
            " fixtures/result.json",
            "fixtures/result file.json",
            "file:///tmp/result.json",
        ).forEach { value ->
            assertFailsWith<IllegalArgumentException> {
                CodecMatrixRelativePath(value)
            }
        }
    }

    @Test
    fun `N A status serializes as documented`() {
        val encoded = CodecMatrixJson.encode(eligibilityManifest(N_A))

        encoded shouldContain "\"status\": \"N/A\""
    }

    @Test
    fun `strict JSON rejects hash mismatch and unknown or duplicate keys`() {
        val manifest = eligibilityManifest(ELIGIBLE)
        val target = tempDir.resolve("eligibility.json")
        CodecMatrixJson.write(target, manifest)

        assertFailsWith<IllegalArgumentException> {
            CodecMatrixJson.readEligibility(target, CodecMatrixSha256("0".repeat(64)))
        }

        val encoded = CodecMatrixJson.encode(manifest)
        val unknown = encoded.replaceFirst("{", "{\n  \"unknown\": true,")
        Files.writeString(target, unknown)

        assertFailsWith<IllegalArgumentException> {
            CodecMatrixJson.readEligibility(target, CodecMatrixJson.sha256(unknown.toByteArray()))
        }

        val duplicate = encoded.replaceFirst("{", "{\n  \"schemaVersion\": 1,")
        Files.writeString(target, duplicate)

        assertFailsWith<IllegalArgumentException> {
            CodecMatrixJson.readEligibility(target, CodecMatrixJson.sha256(duplicate.toByteArray()))
        }
    }

    @Test
    fun `canonical JSON round trip preserves manifest`() {
        val manifest = eligibilityManifest(ELIGIBLE)
        val target = tempDir.resolve("eligibility.json")
        val hash = CodecMatrixJson.write(target, manifest)

        CodecMatrixJson.readEligibility(target, hash) shouldBeEqualTo manifest
    }

    @Test
    fun `manifest requires exact unique cell cardinality`() {
        val cell = matrixCell(ELIGIBLE)

        assertFailsWith<IllegalArgumentException> {
            CodecMatrixEligibilityManifest(
                runId = CodecMatrixRunId("issue-208-test-run"),
                expectedCellCount = 2,
                cells = listOf(cell),
            ).validateEligibility()
        }
        assertFailsWith<IllegalArgumentException> {
            CodecMatrixEligibilityManifest(
                runId = CodecMatrixRunId("issue-208-test-run"),
                expectedCellCount = 2,
                cells = listOf(cell, cell),
            ).validateEligibility()
        }
    }

    @Test
    fun `strict JSON rejects excessive nesting and oversized strings`() {
        val target = tempDir.resolve("unsafe.json")
        val nested = "{\"value\":".repeat(40) + "0" + "}".repeat(40)
        Files.writeString(target, nested)

        assertFailsWith<IllegalArgumentException> {
            CodecMatrixJson.readEligibility(target, CodecMatrixJson.sha256(nested.toByteArray()))
        }

        val oversized = "{\"value\":\"${"x".repeat(5000)}\"}"
        Files.writeString(target, oversized)

        assertFailsWith<IllegalArgumentException> {
            CodecMatrixJson.readEligibility(target, CodecMatrixJson.sha256(oversized.toByteArray()))
        }
    }

    @Test
    fun `strict JSON rejects oversized bytes and collections before decoding`() {
        val target = tempDir.resolve("bounded.json")
        val manifest = eligibilityManifest(ELIGIBLE)
        val oversizedBytes = CodecMatrixJson.encode(manifest) + " ".repeat(1_048_576)
        Files.writeString(target, oversizedBytes)

        assertFailsWith<IllegalArgumentException> {
            CodecMatrixJson.readEligibility(target, CodecMatrixJson.sha256(oversizedBytes.toByteArray()))
        }

        val oversizedArray = "{\"cells\":[${List(129) { "0" }.joinToString()}]}"
        Files.writeString(target, oversizedArray)
        val error = assertFailsWith<IllegalArgumentException> {
            CodecMatrixJson.readEligibility(target, CodecMatrixJson.sha256(oversizedArray.toByteArray()))
        }
        error.message shouldContain "JSON array exceeds"
    }

    @Test
    fun `hash and numeric values reject malformed evidence`() {
        assertFailsWith<IllegalArgumentException> {
            CodecMatrixSha256("not-a-hash")
        }
        assertFailsWith<IllegalArgumentException> {
            measuredCell(completeMetrics().copy(latencyMs = Double.NaN)).validateFinalized()
        }
        assertFailsWith<IllegalArgumentException> {
            measuredCell(completeMetrics().copy(outputBytes = 0)).validateFinalized()
        }
    }

    private fun eligibilityManifest(status: CodecMatrixCellStatus): CodecMatrixEligibilityManifest =
        CodecMatrixEligibilityManifest(
            runId = CodecMatrixRunId("issue-208-test-run"),
            cells = listOf(matrixCell(status = status)),
        )

    private fun finalizedManifest(status: CodecMatrixCellStatus): CodecMatrixFinalizedManifest =
        CodecMatrixFinalizedManifest(
            runId = CodecMatrixRunId("issue-208-test-run"),
            cells = listOf(
                if (status == MEASURED) measuredCell() else matrixCell(status = status),
            ),
        )

    private fun measuredCell(metrics: CodecMatrixMetrics = completeMetrics()): CodecMatrixCell =
        matrixCell(
            status = MEASURED,
            reasonCode = CodecMatrixReasonCode.NONE,
            rerunGuidance = null,
            metrics = metrics,
        )

    private fun matrixCell(
        status: CodecMatrixCellStatus,
        reasonCode: CodecMatrixReasonCode = reasonCodeFor(status),
        rerunGuidance: String? = if (status.isTerminalUnmeasured) "rerun capability report" else null,
        metrics: CodecMatrixMetrics? = null,
    ): CodecMatrixCell = CodecMatrixCell(
        key = CodecMatrixCellKey(
            backend = CodecMatrixBackendId.JAVA25,
            scenario = CodecMatrixScenario.WEB_PHOTO,
            format = CodecMatrixFormat.WEBP,
            direction = CodecMatrixDirection.ENCODE,
            inputSha256 = CodecMatrixSha256("a".repeat(64)),
        ),
        status = status,
        reasonCode = reasonCode,
        rerunGuidance = rerunGuidance,
        metrics = metrics,
    )

    private fun completeMetrics(): CodecMatrixMetrics = CodecMatrixMetrics(
        latencyMs = 1.25,
        allocationBytesPerOp = 128.0,
        inputBytes = 512L,
        outputBytes = 256L,
        outputSha256 = CodecMatrixSha256("b".repeat(64)),
    )

    private fun reasonCodeFor(status: CodecMatrixCellStatus): CodecMatrixReasonCode = when (status) {
        UNSUPPORTED -> CodecMatrixReasonCode.CAPABILITY_UNAVAILABLE
        SKIPPED -> CodecMatrixReasonCode.CAPABILITY_UNKNOWN
        N_A -> CodecMatrixReasonCode.HOST_BINARY_INCOMPATIBLE
        FAILED_SMOKE -> CodecMatrixReasonCode.SMOKE_FAILED
        ERROR -> CodecMatrixReasonCode.EVIDENCE_INVALID
        ELIGIBLE, MEASURED -> CodecMatrixReasonCode.NONE
    }
}
