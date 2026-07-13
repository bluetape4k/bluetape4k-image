package io.bluetape4k.images.benchmark

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

internal object CodecMatrixExperimentalFixtureMain {
    @JvmStatic
    fun main(arguments: Array<String>) {
        val parsed = parseCodecMatrixCapabilityArguments(arguments)
        val repositoryRoot = Path.of("").toAbsolutePath().normalize()
        val runDirectory = repositoryRoot.resolve(
            "benchmark/images-benchmark/build/codec-matrix/${parsed.runId.value}",
        )
        val reportDirectory = repositoryRoot.resolve(
            "benchmark/images-benchmark/build/reports/benchmarks/codec-matrix/${parsed.runId.value}",
        )
        val eligibilityPath = reportDirectory.resolve("eligibility-${parsed.backend.selector}.json")
        val eligibility = CodecMatrixJson.readEligibility(
            eligibilityPath,
            CodecMatrixJson.sha256(Files.readAllBytes(eligibilityPath)),
        )
        require(eligibility.runId == parsed.runId) { "eligibility run ID differs" }
        EXPERIMENTAL_OUTPUT_FORMATS.forEach { format ->
            val parameterFile = codecMatrixParameterFile(runDirectory, format)
            val latencyFile = runDirectory.resolve(
                "staging/latency-${parsed.backend.selector}-${format.configurationName()}.json",
            )
            writeCodecMatrixBenchmarkParameters(
                parameterFile,
                renderCodecMatrixBenchmarkParameters(format, eligibility, latencyFile),
            )
        }
        if (eligibility.cells.none { cell ->
            cell.key.format in EXPERIMENTAL_OUTPUT_FORMATS && cell.status == CodecMatrixCellStatus.ELIGIBLE
        }) {
            return
        }
        val fixturePath = runDirectory.resolve("fixtures/manifest.json")
        val fixtures = CodecMatrixJson.readFixture(
            fixturePath,
            CodecMatrixJson.sha256(Files.readAllBytes(fixturePath)),
        )
        val adapter = CodecMatrixRuntimeAdapter.create(parsed.backend)
        val entries = ArrayList<CodecMatrixExperimentalFixtureEntry>()
        val experimentalSizes = ArrayList<CodecMatrixSizeObservation>()
        fixtures.fixtures.forEach { fixture ->
            val jpeg = fixture.inputs.single { it.format == CodecMatrixFormat.JPEG }
            val jpegBytes = Files.readAllBytes(runDirectory.resolve(jpeg.path.value))
            EXPERIMENTAL_OUTPUT_FORMATS.forEach { format ->
                val encodeCell = eligibility.cells.single { cell ->
                    cell.key.scenario == fixture.scenario &&
                            cell.key.format == format &&
                            cell.key.direction == CodecMatrixDirection.ENCODE
                }
                if (encodeCell.status != CodecMatrixCellStatus.ELIGIBLE) return@forEach
                val output = adapter.open(jpegBytes).use { image ->
                    require(image.width == fixture.derived.dimensions.width && image.height == fixture.derived.dimensions.height) {
                        "experimental source dimensions differ"
                    }
                    image.toBytes(format)
                }
                require(codecMatrixMagic(format, output).valid) { "experimental output magic differs" }
                experimentalSizes += CodecMatrixSizeObservation(
                    key = encodeCell.key,
                    inputBytes = jpegBytes.size.toLong(),
                    outputBytes = output.size.toLong(),
                    outputSha256 = CodecMatrixJson.sha256(output),
                )
                val decodeCell = eligibility.cells.single { cell ->
                    cell.key.scenario == fixture.scenario &&
                            cell.key.format == format &&
                            cell.key.direction == CodecMatrixDirection.DECODE
                }
                if (decodeCell.status == CodecMatrixCellStatus.ELIGIBLE) {
                    require(decodeCell.key.inputSha256 == CodecMatrixJson.sha256(output)) {
                        "experimental producer output is not deterministic"
                    }
                    val decodedJpeg = adapter.open(output).use { it.toBytes(CodecMatrixFormat.JPEG) }
                    require(codecMatrixMagic(CodecMatrixFormat.JPEG, decodedJpeg).valid) {
                        "experimental decode output magic differs"
                    }
                    experimentalSizes += CodecMatrixSizeObservation(
                        key = decodeCell.key,
                        inputBytes = output.size.toLong(),
                        outputBytes = decodedJpeg.size.toLong(),
                        outputSha256 = CodecMatrixJson.sha256(decodedJpeg),
                    )
                }
                val relative = CodecMatrixRelativePath(
                    "fixtures/experimental-${parsed.backend.selector}/${fixture.scenario.pathName()}/input.${format.extensionName()}",
                )
                writeImmutable(runDirectory.resolve(relative.value), output)
                entries += CodecMatrixExperimentalFixtureEntry(
                    scenario = fixture.scenario,
                    input = CodecMatrixInput(
                        format = format,
                        path = relative,
                        sha256 = CodecMatrixJson.sha256(output),
                        byteCount = output.size.toLong(),
                        dimensions = fixture.derived.dimensions,
                        magic = codecMatrixMagic(format, output),
                    ),
                )
            }
        }
        require(entries.isNotEmpty()) { "no experimental encode fixtures are eligible" }
        val manifest = CodecMatrixExperimentalFixtureManifest(
            runId = parsed.runId,
            producerBackend = parsed.backend.id,
            producerJdk = Runtime.version().feature(),
            libvipsVersion = adapter.libvipsVersion,
            entries = entries,
        )
        val manifestTarget = runDirectory.resolve("fixtures/experimental-${parsed.backend.selector}/manifest.json")
        val manifestBytes = CodecMatrixJson.encode(manifest).toByteArray(StandardCharsets.UTF_8)
        if (Files.exists(manifestTarget)) {
            require(Files.readAllBytes(manifestTarget).contentEquals(manifestBytes)) {
                "existing experimental fixture manifest differs"
            }
        } else {
            CodecMatrixJson.write(manifestTarget, manifest)
        }
        val baseSizesPath = reportDirectory.resolve("sizes-${parsed.backend.selector}.json")
        val baseSizes = CodecMatrixJson.readSizes(
            baseSizesPath,
            CodecMatrixJson.sha256(Files.readAllBytes(baseSizesPath)),
        )
        require(baseSizes.runId == parsed.runId && baseSizes.backend == parsed.backend.id) {
            "base size artifact identity differs"
        }
        CodecMatrixJson.write(
            reportDirectory.resolve("sizes-${parsed.backend.selector}-staged.json"),
            baseSizes.copy(observations = baseSizes.observations + experimentalSizes),
        )
    }
}

private fun writeImmutable(target: Path, bytes: ByteArray) {
    if (Files.exists(target)) {
        require(Files.readAllBytes(target).contentEquals(bytes)) { "existing experimental fixture differs" }
        return
    }
    Files.createDirectories(requireNotNull(target.parent))
    val temporary = target.parent.resolve(".${target.fileName}.tmp-${UUID.randomUUID()}")
    try {
        Files.write(temporary, bytes)
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE)
    } finally {
        Files.deleteIfExists(temporary)
    }
}

private fun CodecMatrixScenario.pathName(): String = when (this) {
    CodecMatrixScenario.WEB_PHOTO -> "web-photo"
    CodecMatrixScenario.PROFILE -> "profile"
}

private fun CodecMatrixFormat.extensionName(): String = when (this) {
    CodecMatrixFormat.AVIF -> "avif"
    CodecMatrixFormat.HEIC -> "heic"
    else -> error("experimental extension requested for $this")
}

private fun CodecMatrixFormat.configurationName(): String = when (this) {
    CodecMatrixFormat.AVIF -> "codecMatrixAvif"
    CodecMatrixFormat.HEIC -> "codecMatrixHeic"
    else -> error("experimental configuration requested for $this")
}

private val EXPERIMENTAL_OUTPUT_FORMATS = listOf(CodecMatrixFormat.AVIF, CodecMatrixFormat.HEIC)
