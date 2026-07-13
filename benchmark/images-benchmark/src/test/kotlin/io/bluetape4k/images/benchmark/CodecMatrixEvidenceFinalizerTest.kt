package io.bluetape4k.images.benchmark

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.junit5.concurrency.MultithreadingTester
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class CodecMatrixEvidenceFinalizerTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var stagingRoot: Path
    private lateinit var acceptedRoot: Path
    private lateinit var failedRoot: Path

    @BeforeEach
    fun setUp() {
        tempDir = tempDir.toRealPath()
        stagingRoot = Files.createDirectories(tempDir.resolve("staging"))
        acceptedRoot = Files.createDirectories(tempDir.resolve("accepted"))
        failedRoot = Files.createDirectories(tempDir.resolve("failed"))
    }

    @Test
    fun `CLI accepts only run lineage arguments`() {
        parseCodecMatrixFinalizeArguments(arrayOf("--run-id", "finalize-run-0001"))
            .runId.shouldBeEqualTo(CodecMatrixRunId("finalize-run-0001"))
        parseCodecMatrixFinalizeArguments(
            arrayOf("--run-id", "finalize-run-0002", "--supersedes", "finalize-run-0001"),
        ).supersedes.shouldBeEqualTo(CodecMatrixRunId("finalize-run-0001"))
        parseCodecMatrixFinalizeArguments(
            arrayOf(
                "--run-id",
                "finalize-run-0003",
                "--replaces-failed-attempt",
                "finalize-run-0001",
            ),
        ).replacesFailedAttempt.shouldBeEqualTo(CodecMatrixRunId("finalize-run-0001"))
        assertFailsWith<IllegalArgumentException> {
            parseCodecMatrixFinalizeArguments(arrayOf("--run-id", "finalize-run-0001", "--output", "/tmp/out"))
        }
        assertFailsWith<IllegalArgumentException> {
            parseCodecMatrixFinalizeArguments(
                arrayOf("--run-id", "finalize-run-0001", "--run-id", "finalize-run-0002"),
            )
        }
    }

    @Test
    fun `pre benchmark measured and incomplete numeric cells are rejected`() {
        val request = request("finalize-bad-0001")
        writeEligibility(request, CodecMatrixCellStatus.MEASURED)
        assertFailsWith<IllegalArgumentException> { finalizeCodecMatrixEvidence(request) }

        writeEligibility(request, CodecMatrixCellStatus.ELIGIBLE)
        assertFailsWith<IllegalArgumentException> { finalizeCodecMatrixEvidence(request) }
    }

    @Test
    fun `complete evidence is atomically promoted and never overwritten`() {
        val request = request("finalize-good-0001")
        val artifactBytes = "pinned protocol".toByteArray()
        val artifactPath = request.stagingDirectory.resolve("protocol.txt")
        Files.createDirectories(request.stagingDirectory)
        Files.write(artifactPath, artifactBytes)
        writeEligibility(
            request,
            CodecMatrixCellStatus.ELIGIBLE,
            artifacts = listOf(
                CodecMatrixArtifact(
                    CodecMatrixRelativePath("protocol.txt"),
                    CodecMatrixJson.sha256(artifactBytes),
                    artifactBytes.size.toLong(),
                ),
            ),
        )
        writeMeasurement(request)

        val manifest = finalizeCodecMatrixEvidence(request)

        manifest.runId.shouldBeEqualTo(request.runId)
        Files.isRegularFile(acceptedRoot.resolve("${request.runId.value}/run-manifest.json")).shouldBeEqualTo(true)
        Files.readAllBytes(acceptedRoot.resolve("${request.runId.value}/protocol.txt"))
            .contentEquals(artifactBytes).shouldBeEqualTo(true)
        assertFailsWith<IllegalArgumentException> { finalizeCodecMatrixEvidence(request) }
    }

    @Test
    fun `blocking smoke creates immutable failure ledger instead of accepted evidence`() {
        val request = request("finalize-failed-0001")
        writeEligibility(request, CodecMatrixCellStatus.FAILED_SMOKE)

        assertFailsWith<CodecMatrixBlockingEvidenceException> {
            finalizeCodecMatrixEvidence(request)
        }
        Files.isRegularFile(failedRoot.resolve("${request.runId.value}/attempt-manifest.json"))
            .shouldBeEqualTo(true)
        Files.exists(acceptedRoot.resolve(request.runId.value)).shouldBeEqualTo(false)
        assertFailsWith<IllegalArgumentException> { finalizeCodecMatrixEvidence(request) }
    }

    @Test
    fun `artifact hash mismatch and leaked local data are rejected`() {
        val hashRequest = request("finalize-hash-0001")
        Files.createDirectories(hashRequest.stagingDirectory)
        Files.writeString(hashRequest.stagingDirectory.resolve("artifact.bin"), "actual")
        writeEligibility(
            hashRequest,
            CodecMatrixCellStatus.UNSUPPORTED,
            artifacts = listOf(
                CodecMatrixArtifact(
                    CodecMatrixRelativePath("artifact.bin"),
                    CodecMatrixSha256("c".repeat(64)),
                    6,
                ),
            ),
        )
        assertFailsWith<IllegalArgumentException> { finalizeCodecMatrixEvidence(hashRequest) }

        val leakageRequest = request("finalize-leak-0001")
        writeEligibility(
            leakageRequest,
            CodecMatrixCellStatus.UNSUPPORTED,
            reason = "failed at /Users/example/private/image.png",
        )
        assertFailsWith<IllegalArgumentException> { finalizeCodecMatrixEvidence(leakageRequest) }
    }

    @Test
    fun `strict JSON and symbolic links in the staging tree are rejected`() {
        val invalidRequest = request("finalize-json-0001")
        writeEligibility(invalidRequest, CodecMatrixCellStatus.UNSUPPORTED)
        Files.writeString(invalidRequest.stagingDirectory.resolve("eligibility.json"), "{} trailing")
        assertFailsWith<IllegalArgumentException> { finalizeCodecMatrixEvidence(invalidRequest) }

        val linkRequest = request("finalize-link-0001")
        writeEligibility(linkRequest, CodecMatrixCellStatus.UNSUPPORTED)
        val outside = Files.writeString(tempDir.resolve("outside.txt"), "outside")
        Files.createSymbolicLink(linkRequest.stagingDirectory.resolve("linked.txt"), outside)
        assertFailsWith<IllegalArgumentException> { finalizeCodecMatrixEvidence(linkRequest) }

        val realStagingRoot = Files.createDirectories(tempDir.resolve("real-staging"))
        val linkedStagingRoot = tempDir.resolve("linked-staging")
        Files.createSymbolicLink(linkedStagingRoot, realStagingRoot)
        val ancestorRequest = CodecMatrixFinalizeRequest(
            runId = CodecMatrixRunId("finalize-link-0002"),
            stagingRoot = linkedStagingRoot,
            acceptedRoot = acceptedRoot,
            failedRoot = failedRoot,
        )
        writeEligibility(ancestorRequest, CodecMatrixCellStatus.UNSUPPORTED)
        assertFailsWith<IllegalArgumentException> { finalizeCodecMatrixEvidence(ancestorRequest) }

        val oversizedRequest = request("finalize-json-0002")
        Files.createDirectories(oversizedRequest.stagingDirectory)
        Files.write(
            oversizedRequest.stagingDirectory.resolve("eligibility.json"),
            ByteArray(1_048_577) { 'x'.code.toByte() },
        )
        assertFailsWith<IllegalArgumentException> { finalizeCodecMatrixEvidence(oversizedRequest) }
    }

    @Test
    fun `atomic move failure leaves no accepted or partial promotion directory`() {
        val request = request("finalize-atomic-0001")
        writeEligibility(request, CodecMatrixCellStatus.ELIGIBLE)
        writeMeasurement(request)

        assertFailsWith<IllegalStateException> {
            finalizeCodecMatrixEvidence(request) { source, target ->
                throw AtomicMoveNotSupportedException(source.toString(), target.toString(), "unsupported")
            }
        }

        Files.exists(acceptedRoot.resolve(request.runId.value)).shouldBeEqualTo(false)
        Files.list(acceptedRoot).use { paths ->
            paths.noneMatch { it.fileName.toString().contains("promotion") }.shouldBeEqualTo(true)
        }
    }

    @Test
    fun `only one concurrent finalizer can accept a run`() {
        val request = request("finalize-race-0001")
        writeEligibility(request, CodecMatrixCellStatus.ELIGIBLE)
        writeMeasurement(request)
        val acceptedCount = AtomicInteger()
        MultithreadingTester()
            .workers(2)
            .rounds(1)
            .add {
                if (runCatching { finalizeCodecMatrixEvidence(request) }.isSuccess) {
                    acceptedCount.incrementAndGet()
                }
            }
            .run()

        acceptedCount.get().shouldBeEqualTo(1)
        Files.isRegularFile(acceptedRoot.resolve("${request.runId.value}/run-manifest.json"))
            .shouldBeEqualTo(true)
    }

    @Test
    fun `supersedes records lineage without replacing either run`() {
        val first = request("finalize-lineage-0001")
        writeEligibility(first, CodecMatrixCellStatus.ELIGIBLE)
        writeMeasurement(first)
        finalizeCodecMatrixEvidence(first)

        val second = request("finalize-lineage-0002", supersedes = first.runId)
        writeEligibility(second, CodecMatrixCellStatus.ELIGIBLE)
        writeMeasurement(second)
        val manifest = finalizeCodecMatrixEvidence(second)

        manifest.supersedes.shouldBeEqualTo(first.runId)
        Files.isDirectory(acceptedRoot.resolve(first.runId.value)).shouldBeEqualTo(true)
        Files.isDirectory(acceptedRoot.resolve(second.runId.value)).shouldBeEqualTo(true)

        val missing = request(
            "finalize-lineage-0003",
            supersedes = CodecMatrixRunId("missing-accepted-0001"),
        )
        writeEligibility(missing, CodecMatrixCellStatus.ELIGIBLE)
        writeMeasurement(missing)
        assertFailsWith<IllegalArgumentException> { finalizeCodecMatrixEvidence(missing) }
    }

    @Test
    fun `replacement points to immutable failed ledger by run ID and hash`() {
        val failed = request("finalize-replaced-0001")
        writeEligibility(failed, CodecMatrixCellStatus.FAILED_SMOKE)
        assertFailsWith<CodecMatrixBlockingEvidenceException> { finalizeCodecMatrixEvidence(failed) }
        val failedManifest = failedRoot.resolve("${failed.runId.value}/attempt-manifest.json")
        val originalBytes = Files.readAllBytes(failedManifest)

        val replacement = request("finalize-replaced-0002", replacesFailedAttempt = failed.runId)
        writeEligibility(replacement, CodecMatrixCellStatus.ELIGIBLE)
        writeMeasurement(replacement)
        val manifest = finalizeCodecMatrixEvidence(replacement)

        manifest.replacesFailedAttempt.shouldBeEqualTo(
            CodecMatrixFailedAttemptReference(failed.runId, CodecMatrixJson.sha256(originalBytes)),
        )
        Files.readAllBytes(failedManifest).contentEquals(originalBytes).shouldBeEqualTo(true)

        val missing = request(
            "finalize-replaced-0003",
            replacesFailedAttempt = CodecMatrixRunId("missing-failed-0001"),
        )
        writeEligibility(missing, CodecMatrixCellStatus.ELIGIBLE)
        writeMeasurement(missing)
        assertFailsWith<IllegalArgumentException> { finalizeCodecMatrixEvidence(missing) }

        val claimedFailedId = CodecMatrixRunId("claimed-failed-0001")
        CodecMatrixJson.write(
            failedRoot.resolve("${claimedFailedId.value}/attempt-manifest.json"),
            CodecMatrixFailedAttemptManifest(
                runId = CodecMatrixRunId("different-failed-0001"),
                expectedCellCount = 1,
                cells = listOf(
                    matrixCell(
                        CodecMatrixCellStatus.FAILED_SMOKE,
                        CodecMatrixReasonCode.SMOKE_FAILED,
                        "smoke failed",
                        "rerun after diagnosis",
                    ),
                ),
            ),
        )
        val mismatched = request("finalize-replaced-0004", replacesFailedAttempt = claimedFailedId)
        writeEligibility(mismatched, CodecMatrixCellStatus.ELIGIBLE)
        writeMeasurement(mismatched)
        assertFailsWith<IllegalArgumentException> { finalizeCodecMatrixEvidence(mismatched) }
    }

    @Test
    fun `N A backend accepts terminal coverage and forbids numeric artifacts`() {
        val accepted = request("finalize-na-run-0001")
        writeEligibility(accepted, nACells())
        finalizeCodecMatrixEvidence(accepted).cells.all { it.status == CodecMatrixCellStatus.N_A }
            .shouldBeEqualTo(true)

        val polluted = request("finalize-na-run-0002")
        writeEligibility(polluted, nACells())
        Files.writeString(polluted.stagingDirectory.resolve("allocation-java21.json"), "{}")
        assertFailsWith<IllegalArgumentException> { finalizeCodecMatrixEvidence(polluted) }

        val incomplete = request("finalize-na-run-0003")
        writeEligibility(incomplete, nACells().dropLast(1))
        assertFailsWith<IllegalArgumentException> { finalizeCodecMatrixEvidence(incomplete) }
    }

    @Test
    fun `backend keyed evidence preserves unavailable runtime preflight and terminal cells`() {
        val request = request("finalize-runtime-matrix-0001")
        val java25Cells = CodecMatrixScenario.entries.flatMap { scenario ->
            listOf(
                CodecMatrixFormat.PNG,
                CodecMatrixFormat.WEBP,
                CodecMatrixFormat.AVIF,
                CodecMatrixFormat.HEIC,
            ).flatMap { format ->
                CodecMatrixDirection.entries.map { direction ->
                    matrixCell(
                        status = CodecMatrixCellStatus.UNSUPPORTED,
                        reasonCode = CodecMatrixReasonCode.CAPABILITY_UNAVAILABLE,
                        reason = "codec operation is unavailable",
                        rerunGuidance = "rerun codec capability smoke",
                        backend = CodecMatrixBackendId.JAVA25,
                        scenario = scenario,
                        format = format,
                        direction = direction,
                    )
                }
            }
        }
        writeBackendEligibility(request, CodecMatrixBackend.JAVA25, java25Cells)
        writePreflight(request, CodecMatrixBackend.JAVA21, CodecMatrixCellStatus.N_A)
        writePreflight(request, CodecMatrixBackend.JAVA25, CodecMatrixCellStatus.ELIGIBLE)

        val manifest = finalizeCodecMatrixEvidence(request)

        manifest.expectedCellCount.shouldBeEqualTo(32)
        manifest.cells.count { it.key.backend == CodecMatrixBackendId.JAVA21 }
            .shouldBeEqualTo(16)
        manifest.cells.filter { it.key.backend == CodecMatrixBackendId.JAVA21 }
            .all { it.status == CodecMatrixCellStatus.N_A }
            .shouldBeEqualTo(true)
        manifest.artifacts.map { it.path.value }.toSet().shouldBeEqualTo(
            setOf("preflight-java21.json", "preflight-java25.json"),
        )
        Files.isRegularFile(acceptedRoot.resolve("${request.runId.value}/eligibility-java25.json"))
            .shouldBeEqualTo(true)
        Files.isRegularFile(acceptedRoot.resolve("${request.runId.value}/preflight-java21.json"))
            .shouldBeEqualTo(true)
    }

    @Test
    fun `raw JMH latency allocation and size evidence join by exact cell key`() {
        val request = request("finalize-jmh-run-0001")
        writeEligibility(request, CodecMatrixCellStatus.ELIGIBLE)
        writeJmh(
            request.stagingDirectory.resolve("latency-java25-codecMatrix.json"),
            primaryScore = 2.5,
        )
        writeJmh(
            request.stagingDirectory.resolve("allocation-java25-codecMatrix.json"),
            primaryScore = 2.6,
            allocationScore = 2048.0,
        )
        CodecMatrixJson.write(
            request.stagingDirectory.resolve("sizes-java25.json"),
            CodecMatrixSizeManifest(
                runId = request.runId,
                backend = CodecMatrixBackendId.JAVA25,
                observations = listOf(
                    CodecMatrixSizeObservation(
                        key = matrixCell(CodecMatrixCellStatus.ELIGIBLE).key,
                        inputBytes = 100,
                        outputBytes = 75,
                        outputSha256 = CodecMatrixSha256("d".repeat(64)),
                    ),
                ),
            ),
        )

        val manifest = finalizeCodecMatrixEvidence(request)

        manifest.cells.single().metrics.shouldBeEqualTo(
            CodecMatrixMetrics(2.5, 2048.0, 100, 75, CodecMatrixSha256("d".repeat(64))),
        )
        val sanitized = acceptedRoot.resolve(
            "${request.runId.value}/evidence/latency-java25-codecMatrix.json",
        )
        Files.isRegularFile(sanitized).shouldBeEqualTo(true)
        Files.readString(sanitized).contains("/Users/private").shouldBeEqualTo(false)
        Files.readString(sanitized).contains("password=leaked").shouldBeEqualTo(false)
    }

    private fun request(
        runId: String,
        supersedes: CodecMatrixRunId? = null,
        replacesFailedAttempt: CodecMatrixRunId? = null,
    ) = CodecMatrixFinalizeRequest(
        runId = CodecMatrixRunId(runId),
        stagingRoot = stagingRoot,
        acceptedRoot = acceptedRoot,
        failedRoot = failedRoot,
        supersedes = supersedes,
        replacesFailedAttempt = replacesFailedAttempt,
    )

    private fun writeEligibility(
        request: CodecMatrixFinalizeRequest,
        status: CodecMatrixCellStatus,
        artifacts: List<CodecMatrixArtifact> = emptyList(),
        reason: String? = null,
    ) {
        if (status == CodecMatrixCellStatus.MEASURED) {
            CodecMatrixJson.write(
                request.stagingDirectory.resolve("eligibility.json"),
                CodecMatrixFinalizedManifest(
                    runId = request.runId,
                    cells = listOf(
                        matrixCell(
                            status,
                            metrics = CodecMatrixMetrics(1.0, 1.0, 1, 1, CodecMatrixSha256("b".repeat(64))),
                        ),
                    ),
                ),
            )
            return
        }
        val cell = if (status == CodecMatrixCellStatus.ELIGIBLE || status == CodecMatrixCellStatus.MEASURED) {
            matrixCell(status)
        } else {
            matrixCell(
                status,
                reasonCode = if (status == CodecMatrixCellStatus.N_A) {
                    CodecMatrixReasonCode.HOST_BINARY_INCOMPATIBLE
                } else {
                    CodecMatrixReasonCode.SMOKE_FAILED
                },
                reason = reason ?: "encode smoke failed",
                rerunGuidance = "rerun capability smoke",
            )
        }
        CodecMatrixJson.write(
            request.stagingDirectory.resolve("eligibility.json"),
            CodecMatrixEligibilityManifest(runId = request.runId, cells = listOf(cell), artifacts = artifacts),
        )
    }

    private fun writeEligibility(request: CodecMatrixFinalizeRequest, cells: List<CodecMatrixCell>) {
        CodecMatrixJson.write(
            request.stagingDirectory.resolve("eligibility.json"),
            CodecMatrixEligibilityManifest(
                runId = request.runId,
                expectedCellCount = cells.size,
                cells = cells,
            ),
        )
    }

    private fun writeBackendEligibility(
        request: CodecMatrixFinalizeRequest,
        backend: CodecMatrixBackend,
        cells: List<CodecMatrixCell>,
    ) {
        CodecMatrixJson.write(
            request.stagingDirectory.resolve("eligibility-${backend.selector}.json"),
            CodecMatrixEligibilityManifest(
                runId = request.runId,
                expectedCellCount = cells.size,
                cells = cells,
            ),
        )
    }

    private fun writePreflight(
        request: CodecMatrixFinalizeRequest,
        backend: CodecMatrixBackend,
        status: CodecMatrixCellStatus,
    ) {
        CodecMatrixJson.write(
            request.stagingDirectory.resolve("preflight-${backend.selector}.json"),
            CodecMatrixPreflightManifest(
                runId = request.runId,
                requestedBackend = backend.id,
                requestedSelector = backend.selector,
                status = status,
                reasonCode = if (status == CodecMatrixCellStatus.ELIGIBLE) {
                    CodecMatrixReasonCode.NONE
                } else {
                    CodecMatrixReasonCode.CAPABILITY_UNKNOWN
                },
                reason = if (status == CodecMatrixCellStatus.ELIGIBLE) {
                    null
                } else {
                    "JNI binary architecture is unavailable"
                },
                facts = CodecMatrixPreflightFacts(
                    architecture = CodecMatrixArchitecture.ARM64,
                    jdkMajor = backend.expectedJavaMajor,
                    gitSha = "a".repeat(40),
                    gitDirty = false,
                ),
            ),
        )
    }

    private fun nACells(): List<CodecMatrixCell> = CodecMatrixScenario.entries.flatMap { scenario ->
        listOf(
            CodecMatrixFormat.PNG,
            CodecMatrixFormat.WEBP,
            CodecMatrixFormat.AVIF,
            CodecMatrixFormat.HEIC,
        ).flatMap { format ->
            CodecMatrixDirection.entries.map { direction ->
                matrixCell(
                    status = CodecMatrixCellStatus.N_A,
                    reasonCode = CodecMatrixReasonCode.HOST_BINARY_INCOMPATIBLE,
                    reason = "host binary is incompatible",
                    rerunGuidance = "rerun on a compatible host",
                    backend = CodecMatrixBackendId.JAVA21,
                    scenario = scenario,
                    format = format,
                    direction = direction,
                )
            }
        }
    }

    private fun writeMeasurement(request: CodecMatrixFinalizeRequest) {
        CodecMatrixJson.write(
            request.stagingDirectory.resolve("measurement.json"),
            CodecMatrixFinalizedManifest(
                runId = request.runId,
                cells = listOf(
                    matrixCell(
                        CodecMatrixCellStatus.MEASURED,
                        metrics = CodecMatrixMetrics(
                            latencyMs = 1.25,
                            allocationBytesPerOp = 1024.0,
                            inputBytes = 100,
                            outputBytes = 80,
                            outputSha256 = CodecMatrixSha256("b".repeat(64)),
                        ),
                    ),
                ),
            ),
        )
    }

    private fun writeJmh(
        path: Path,
        primaryScore: Double,
        allocationScore: Double? = null,
    ) {
        val secondary = allocationScore?.let { score ->
            """
            ,"secondaryMetrics":{"gc.alloc.rate.norm":{"score":$score,"scoreUnit":"B/op"}}
            """.trimIndent()
        }.orEmpty()
        Files.writeString(
            path,
            """
            [{
              "benchmark":"io.bluetape4k.images.benchmark.VipsCodecMatrixBenchmark.encodePngFromJpeg",
              "params":{"scenario":"profile"},
              "mode":"avgt",
              "threads":1,
              "forks":1,
              "warmupIterations":1,
              "warmupTime":"1 s",
              "measurementIterations":3,
              "measurementTime":"1 s",
              "jvm":"/Users/private/jdk/bin/java",
              "jvmArgs":["-Dpassword=leaked"],
              "primaryMetric":{"score":$primaryScore,"scoreUnit":"ms/op"}
              $secondary
            }]
            """.trimIndent(),
        )
    }

    private fun matrixCell(
        status: CodecMatrixCellStatus,
        reasonCode: CodecMatrixReasonCode = CodecMatrixReasonCode.NONE,
        reason: String? = null,
        rerunGuidance: String? = null,
        metrics: CodecMatrixMetrics? = null,
        backend: CodecMatrixBackendId = CodecMatrixBackendId.JAVA25,
        scenario: CodecMatrixScenario = CodecMatrixScenario.PROFILE,
        format: CodecMatrixFormat = CodecMatrixFormat.PNG,
        direction: CodecMatrixDirection = CodecMatrixDirection.ENCODE,
    ) = CodecMatrixCell(
        key = CodecMatrixCellKey(
            backend = backend,
            scenario = scenario,
            format = format,
            direction = direction,
            inputSha256 = CodecMatrixSha256("a".repeat(64)),
        ),
        status = status,
        reasonCode = reasonCode,
        reason = reason,
        rerunGuidance = rerunGuidance,
        metrics = metrics,
    )
}
