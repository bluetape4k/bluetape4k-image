package io.bluetape4k.images.benchmark

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

internal fun renderCodecMatrixBenchmarkParameters(
    format: CodecMatrixFormat,
    eligibility: CodecMatrixEligibilityManifest,
    reportFile: Path,
): String {
    require(format == CodecMatrixFormat.AVIF || format == CodecMatrixFormat.HEIC) {
        "experimental parameter rendering supports only AVIF and HEIC"
    }
    require(reportFile.isAbsolute && reportFile == reportFile.normalize()) {
        "codec matrix report file must be absolute and normalized"
    }
    eligibility.validateEligibility()
    val formatCells = eligibility.cells.filter { cell -> cell.key.format == format }
    require(formatCells.size == CodecMatrixScenario.entries.size * CodecMatrixDirection.entries.size) {
        "$format eligibility must cover every scenario and direction"
    }
    require(formatCells.map { cell -> cell.key.backend }.toSet().size == 1) {
        "$format eligibility must describe exactly one backend"
    }

    val methods = CodecMatrixDirection.entries.mapNotNull { direction ->
        val cells = formatCells.filter { cell -> cell.key.direction == direction }
        require(cells.map { cell -> cell.key.scenario }.toSet() == CodecMatrixScenario.entries.toSet()) {
            "$format $direction eligibility must cover every scenario"
        }
        require(cells.none { cell ->
            cell.status == CodecMatrixCellStatus.FAILED_SMOKE || cell.status == CodecMatrixCellStatus.ERROR
        }) { "$format $direction contains blocking eligibility" }
        val eligibleCount = cells.count { cell -> cell.status == CodecMatrixCellStatus.ELIGIBLE }
        require(eligibleCount == 0 || eligibleCount == CodecMatrixScenario.entries.size) {
            "$format $direction has partial scenario eligibility"
        }
        if (eligibleCount == 0) null else experimentalMethod(format, direction)
    }

    return buildString {
        appendLine("name:benchmark")
        appendLine("traceFormat:text")
        appendLine("reportFormat:json")
        appendLine("iterations:3")
        appendLine("warmups:1")
        appendLine("iterationTime:1")
        appendLine("iterationTimeUnit:s")
        appendLine("outputTimeUnit:ms")
        appendLine("mode:avgt")
        appendLine("configurationName:${experimentalConfiguration(format)}")
        methods.forEach { method ->
            appendLine("include:.*VipsExperimentalCodecMatrixBenchmark.$method.*")
        }
        appendLine("advanced:jvmForks=1")
        appendLine("reportFile:${reportFile.toString().replace('\\', '/')}")
    }
}

internal fun writeCodecMatrixBenchmarkParameters(target: Path, content: String) {
    require(target.isAbsolute && target == target.normalize()) {
        "codec matrix parameter target must be absolute and normalized"
    }
    if (Files.exists(target)) {
        require(Files.readString(target) == content) { "existing codec matrix parameter file differs" }
        return
    }
    Files.createDirectories(requireNotNull(target.parent))
    val temporary = target.parent.resolve(".${target.fileName}.tmp-${UUID.randomUUID()}")
    try {
        Files.writeString(temporary, content)
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE)
    } finally {
        Files.deleteIfExists(temporary)
    }
}

internal fun codecMatrixParameterFile(runDirectory: Path, format: CodecMatrixFormat): Path =
    runDirectory.resolve("staging/parameters-${experimentalConfiguration(format)}.txt")

private fun experimentalConfiguration(format: CodecMatrixFormat): String = when (format) {
    CodecMatrixFormat.AVIF -> "codecMatrixAvif"
    CodecMatrixFormat.HEIC -> "codecMatrixHeic"
    else -> throw IllegalArgumentException("experimental configuration is unavailable for $format")
}

private fun experimentalMethod(
    format: CodecMatrixFormat,
    direction: CodecMatrixDirection,
): String = when (format to direction) {
    CodecMatrixFormat.AVIF to CodecMatrixDirection.ENCODE -> "encodeAvifFromJpeg"
    CodecMatrixFormat.AVIF to CodecMatrixDirection.DECODE -> "decodeAvifToJpeg"
    CodecMatrixFormat.HEIC to CodecMatrixDirection.ENCODE -> "encodeHeicFromJpeg"
    CodecMatrixFormat.HEIC to CodecMatrixDirection.DECODE -> "decodeHeicToJpeg"
    else -> throw IllegalArgumentException("experimental method is unavailable for $format $direction")
}
