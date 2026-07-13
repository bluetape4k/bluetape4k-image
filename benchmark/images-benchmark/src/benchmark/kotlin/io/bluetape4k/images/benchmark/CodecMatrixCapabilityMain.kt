package io.bluetape4k.images.benchmark

import java.nio.file.Files
import java.nio.file.Path

internal data class CodecMatrixCapabilityArguments(
    val backend: CodecMatrixBackend,
    val runId: CodecMatrixRunId,
)

internal fun parseCodecMatrixCapabilityArguments(arguments: Array<String>): CodecMatrixCapabilityArguments {
    require(arguments.size == 4 && arguments[0] == "--backend" && arguments[2] == "--run-id") {
        "usage: CodecMatrixCapabilityMain --backend <java21|java25> --run-id <run-id>"
    }
    return CodecMatrixCapabilityArguments(
        backend = CodecMatrixBackend.parse(arguments[1]),
        runId = CodecMatrixRunId(arguments[3]),
    )
}

internal object CodecMatrixCapabilityMain {
    @JvmStatic
    fun main(arguments: Array<String>) {
        val parsed = parseCodecMatrixCapabilityArguments(arguments)
        val repositoryRoot = Path.of("").toAbsolutePath().normalize()
        val runDirectory = repositoryRoot.resolve(
            "benchmark/images-benchmark/build/codec-matrix/${parsed.runId.value}",
        )
        val preflight = readPreflight(runDirectory, parsed)
        val fixtures = readFixtures(runDirectory, parsed.runId)
        val adapter = if (preflight.status == CodecMatrixCellStatus.ELIGIBLE) {
            CodecMatrixRuntimeAdapter.create(parsed.backend)
        } else {
            null
        }
        val ops = adapter ?: NO_NATIVE_OPS
        val evaluator = CodecMatrixCapabilityEvaluator(ops)
        val cells = ArrayList<CodecMatrixCell>()
        val sizes = ArrayList<CodecMatrixSizeObservation>()

        fixtures.fixtures.forEach { fixture ->
            val jpeg = fixture.inputs.single { it.format == CodecMatrixFormat.JPEG }
            val jpegBytes = readPinned(runDirectory, jpeg)
            STABLE_FORMATS.forEach { format ->
                val target = fixture.inputs.single { it.format == format }
                val targetBytes = readPinned(runDirectory, target)
                val smokeFixture = fixture.smokeFixture(parsed.backend.id, jpeg, jpegBytes, target, targetBytes)
                val capabilities = adapter?.capabilities(format) ?: defaultCapabilities(format)
                capabilities.forEach { capability ->
                    val cell = evaluator.evaluate(capability, smokeFixture, preflight.status)
                    cells += cell
                    if (cell.status == CodecMatrixCellStatus.ELIGIBLE && adapter != null) {
                        sizes += measureSize(adapter, capability, smokeFixture, cell.key)
                    }
                }
            }

            EXPERIMENTAL_FORMATS.forEach { format ->
                val capabilities = adapter?.capabilities(format) ?: defaultCapabilities(format)
                val encodeCapability = capabilities.single { it.direction == CodecMatrixDirection.ENCODE }
                val generatedTarget = if (adapter != null && encodeCapability.support == CodecMatrixCapabilitySupport.AVAILABLE) {
                    adapter.open(jpegBytes).use { it.toBytes(format) }
                } else {
                    null
                }
                val smokeFixture = fixture.smokeFixture(
                    parsed.backend.id,
                    jpeg,
                    jpegBytes,
                    target = null,
                    targetBytes = generatedTarget,
                )
                capabilities.forEach { capability ->
                    cells += evaluator.evaluate(capability, smokeFixture, preflight.status)
                }
            }
        }

        val reportDirectory = repositoryRoot.resolve(
            "benchmark/images-benchmark/build/reports/benchmarks/codec-matrix/${parsed.runId.value}",
        )
        val eligibility = CodecMatrixEligibilityManifest(
            runId = parsed.runId,
            expectedCellCount = cells.size,
            cells = cells,
        ).validateEligibility()
        CodecMatrixJson.write(reportDirectory.resolve("eligibility-${parsed.backend.selector}.json"), eligibility)
        CodecMatrixJson.write(
            reportDirectory.resolve("sizes-${parsed.backend.selector}.json"),
            CodecMatrixSizeManifest(runId = parsed.runId, backend = parsed.backend.id, observations = sizes),
        )
        check(cells.none { it.status == CodecMatrixCellStatus.FAILED_SMOKE || it.status == CodecMatrixCellStatus.ERROR }) {
            "codec matrix capability smoke contains blocking cells"
        }
    }
}

private fun readPreflight(
    runDirectory: Path,
    arguments: CodecMatrixCapabilityArguments,
): CodecMatrixPreflightManifest {
    val path = runDirectory.resolve("preflight-${arguments.backend.selector}.json")
    val manifest = CodecMatrixJson.readPreflight(path, CodecMatrixJson.sha256(Files.readAllBytes(path)))
    require(manifest.runId == arguments.runId && manifest.requestedBackend == arguments.backend.id) {
        "preflight identity differs from the requested run"
    }
    return manifest
}

private fun readFixtures(runDirectory: Path, runId: CodecMatrixRunId): CodecMatrixFixtureManifest {
    val path = runDirectory.resolve("fixtures/manifest.json")
    val manifest = CodecMatrixJson.readFixture(path, CodecMatrixJson.sha256(Files.readAllBytes(path)))
    require(manifest.runId == runId) { "fixture run ID differs" }
    return manifest
}

private fun readPinned(runDirectory: Path, input: CodecMatrixInput): ByteArray {
    val path = runDirectory.resolve(input.path.value)
    val bytes = Files.readAllBytes(path)
    require(CodecMatrixJson.sha256(bytes) == input.sha256) { "pinned input hash differs" }
    require(codecMatrixMagic(input.format, bytes).valid) { "pinned input magic differs" }
    return bytes
}

private fun CodecMatrixFixtureEntry.smokeFixture(
    backend: CodecMatrixBackendId,
    jpeg: CodecMatrixInput,
    jpegBytes: ByteArray,
    target: CodecMatrixInput?,
    targetBytes: ByteArray?,
): CodecMatrixSmokeFixture = CodecMatrixSmokeFixture(
    backend = backend,
    scenario = scenario,
    dimensions = derived.dimensions,
    jpegBytes = jpegBytes,
    jpegSha256 = jpeg.sha256,
    targetBytes = targetBytes,
    targetSha256 = target?.sha256 ?: targetBytes?.let(CodecMatrixJson::sha256),
)

private fun measureSize(
    ops: CodecMatrixCodecOps,
    capability: CodecMatrixDirectionalCapability,
    fixture: CodecMatrixSmokeFixture,
    key: CodecMatrixCellKey,
): CodecMatrixSizeObservation {
    val input = if (capability.direction == CodecMatrixDirection.ENCODE) {
        fixture.jpegBytes
    } else {
        requireNotNull(fixture.targetBytes)
    }
    val outputFormat = if (capability.direction == CodecMatrixDirection.ENCODE) capability.format else CodecMatrixFormat.JPEG
    val output = ops.open(input).use { it.toBytes(outputFormat) }
    return CodecMatrixSizeObservation(key, input.size.toLong(), output.size.toLong(), CodecMatrixJson.sha256(output))
}

private fun defaultCapabilities(format: CodecMatrixFormat): List<CodecMatrixDirectionalCapability> =
    CodecMatrixDirection.entries.map { direction ->
        CodecMatrixDirectionalCapability(format, direction, CodecMatrixCapabilitySupport.AVAILABLE)
    }

private val NO_NATIVE_OPS = object : CodecMatrixCodecOps {
    override fun open(bytes: ByteArray): CodecMatrixCodecHandle = error("native operations are forbidden by preflight")
}

private val STABLE_FORMATS = listOf(CodecMatrixFormat.JPEG, CodecMatrixFormat.PNG, CodecMatrixFormat.WEBP)
private val EXPERIMENTAL_FORMATS = listOf(CodecMatrixFormat.AVIF, CodecMatrixFormat.HEIC)
