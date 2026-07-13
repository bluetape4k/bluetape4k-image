package io.bluetape4k.images.benchmark

import java.io.Serializable
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class CodecMatrixFinalizeArguments(
    val runId: CodecMatrixRunId,
    val supersedes: CodecMatrixRunId? = null,
    val replacesFailedAttempt: CodecMatrixRunId? = null,
): Serializable {
    init {
        require(supersedes != runId) { "a codec matrix run cannot supersede itself" }
        require(replacesFailedAttempt != runId) { "a codec matrix run cannot replace itself" }
    }

    companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

internal data class CodecMatrixFinalizeRequest(
    val runId: CodecMatrixRunId,
    val stagingRoot: Path,
    val acceptedRoot: Path,
    val failedRoot: Path,
    val supersedes: CodecMatrixRunId? = null,
    val replacesFailedAttempt: CodecMatrixRunId? = null,
): Serializable {
    init {
        listOf(
            "stagingRoot" to stagingRoot,
            "acceptedRoot" to acceptedRoot,
            "failedRoot" to failedRoot,
        ).forEach { (label, path) ->
            require(path.isAbsolute) { "$label must be absolute" }
            require(path == path.normalize()) { "$label must be normalized" }
        }
        require(supersedes != runId) { "a codec matrix run cannot supersede itself" }
        require(replacesFailedAttempt != runId) { "a codec matrix run cannot replace itself" }
    }

    val stagingDirectory: Path
        get() = stagingRoot.resolve(runId.value).resolve("staging")

    companion object {
        @JvmField
        val serialVersionUID: Long = 1L
    }
}

internal class CodecMatrixBlockingEvidenceException(message: String): IllegalStateException(message)

internal fun interface CodecMatrixAtomicDirectoryMover {
    fun move(source: Path, target: Path)
}

internal fun parseCodecMatrixFinalizeArguments(arguments: Array<String>): CodecMatrixFinalizeArguments {
    require(arguments.size in 2..6 && arguments.size % 2 == 0) {
        "usage: CodecMatrixFinalizeMain --run-id <run-id> [--supersedes <run-id>] " +
                "[--replaces-failed-attempt <run-id>]"
    }
    val values = LinkedHashMap<String, String>()
    arguments.asList().chunked(2).forEach { pair ->
        val option = pair[0]
        require(option in FINALIZE_OPTIONS) { "unsupported finalizer option: $option" }
        require(values.put(option, pair[1]) == null) { "duplicate finalizer option: $option" }
    }
    val runId = CodecMatrixRunId(requireNotNull(values["--run-id"]) { "--run-id is required" })
    return CodecMatrixFinalizeArguments(
        runId = runId,
        supersedes = values["--supersedes"]?.let(::CodecMatrixRunId),
        replacesFailedAttempt = values["--replaces-failed-attempt"]?.let(::CodecMatrixRunId),
    )
}

internal fun finalizeCodecMatrixEvidence(
    request: CodecMatrixFinalizeRequest,
    mover: CodecMatrixAtomicDirectoryMover = DEFAULT_ATOMIC_MOVER,
): CodecMatrixFinalizedManifest {
    requireSafeExistingDirectory(request.stagingRoot, "staging root")
    requireSafeExistingDirectory(request.acceptedRoot, "accepted root")
    requireSafeExistingDirectory(request.failedRoot, "failed root")
    requireSafeExistingDirectory(request.stagingDirectory, "staging directory")
    validateStagedTree(request.stagingDirectory)

    val lockPath = request.acceptedRoot.resolve(".${request.runId.value}.lock")
    val lockChannel = try {
        FileChannel.open(lockPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    } catch (e: Exception) {
        throw IllegalArgumentException("codec matrix run is already finalized or being finalized: ${request.runId.value}", e)
    }
    try {
        lockChannel.lock().use {
            val acceptedTarget = request.acceptedRoot.resolve(request.runId.value)
            val failedTarget = request.failedRoot.resolve(request.runId.value)
            requireAbsent(acceptedTarget, "accepted run")
            requireAbsent(failedTarget, "failed run")

            val eligibilityPath = request.stagingDirectory.resolve(ELIGIBILITY_FILE)
            requireSafeRegularFile(eligibilityPath, "eligibility evidence")
            val eligibility = CodecMatrixJson.readEligibility(
                eligibilityPath,
                CodecMatrixJson.sha256(Files.readAllBytes(eligibilityPath)),
            )
            require(eligibility.runId == request.runId) { "eligibility run ID differs" }
            validateArtifacts(request.stagingDirectory, eligibility.artifacts)

            if (eligibility.cells.any(CodecMatrixCell::hasBlockingStatus)) {
                promoteFailedAttempt(request, eligibility, failedTarget, mover)
                throw CodecMatrixBlockingEvidenceException(
                    "codec matrix attempt ${request.runId.value} contains blocking evidence",
                )
            }

            val failedReference = validateFailedReplacement(request)
            validateSupersedes(request)
            validateUnavailableBackendArtifacts(request, eligibility)
            val finalized = mergeMeasurement(request, eligibility).copy(
                supersedes = request.supersedes,
                replacesFailedAttempt = failedReference,
            ).validateAccepted()
            promoteAccepted(request, eligibilityPath, finalized, acceptedTarget, mover)
            return finalized
        }
    } finally {
        lockChannel.close()
        Files.deleteIfExists(lockPath)
    }
}

private fun mergeMeasurement(
    request: CodecMatrixFinalizeRequest,
    eligibility: CodecMatrixEligibilityManifest,
): CodecMatrixFinalizedManifest {
    val eligibleKeys = eligibility.cells.filter { it.status == CodecMatrixCellStatus.ELIGIBLE }
        .map(CodecMatrixCell::key)
        .toSet()
    val measurementPath = request.stagingDirectory.resolve(MEASUREMENT_FILE)
    if (eligibleKeys.isEmpty()) {
        val numericArtifacts = listEvidenceFiles(request.stagingDirectory, LATENCY_FILE_PREFIX) +
                listEvidenceFiles(request.stagingDirectory, ALLOCATION_FILE_PREFIX) +
                listEvidenceFiles(request.stagingDirectory, SIZE_FILE_PREFIX)
        require(!Files.exists(measurementPath, LinkOption.NOFOLLOW_LINKS) && numericArtifacts.isEmpty()) {
            "numeric measurement evidence is forbidden when no cells are eligible"
        }
        return CodecMatrixFinalizedManifest(
            runId = request.runId,
            expectedCellCount = eligibility.expectedCellCount,
            cells = eligibility.cells,
            artifacts = eligibility.artifacts,
        )
    }

    val rawLatencyPaths = listEvidenceFiles(request.stagingDirectory, LATENCY_FILE_PREFIX)
    val rawAllocationPaths = listEvidenceFiles(request.stagingDirectory, ALLOCATION_FILE_PREFIX)
    val rawSizePaths = listEvidenceFiles(request.stagingDirectory, SIZE_FILE_PREFIX)
    if (rawLatencyPaths.isNotEmpty() || rawAllocationPaths.isNotEmpty() || rawSizePaths.isNotEmpty()) {
        require(!Files.exists(measurementPath, LinkOption.NOFOLLOW_LINKS)) {
            "normalized and raw measurement evidence must not be mixed"
        }
        return mergeRawMeasurement(
            request,
            eligibility,
            eligibleKeys,
            rawLatencyPaths,
            rawAllocationPaths,
            rawSizePaths,
        )
    }

    requireSafeRegularFile(measurementPath, "measurement evidence")
    val measurement = CodecMatrixJson.readFinalized(
        measurementPath,
        CodecMatrixJson.sha256(Files.readAllBytes(measurementPath)),
    )
    require(measurement.runId == request.runId) { "measurement run ID differs" }
    require(measurement.cells.all { it.status == CodecMatrixCellStatus.MEASURED }) {
        "measurement evidence must contain only MEASURED cells"
    }
    val measuredByKey = measurement.cells.associateBy(CodecMatrixCell::key)
    require(measuredByKey.keys == eligibleKeys) { "measurement cell coverage differs from eligibility" }
    validateArtifacts(request.stagingDirectory, measurement.artifacts)
    return CodecMatrixFinalizedManifest(
        runId = request.runId,
        expectedCellCount = eligibility.expectedCellCount,
        cells = eligibility.cells.map { cell -> measuredByKey[cell.key] ?: cell },
        artifacts = (eligibility.artifacts + measurement.artifacts).distinctBy(CodecMatrixArtifact::path),
    )
}

private fun mergeRawMeasurement(
    request: CodecMatrixFinalizeRequest,
    eligibility: CodecMatrixEligibilityManifest,
    eligibleKeys: Set<CodecMatrixCellKey>,
    latencyPaths: List<Path>,
    allocationPaths: List<Path>,
    sizePaths: List<Path>,
): CodecMatrixFinalizedManifest {
    require(latencyPaths.isNotEmpty()) { "latency JMH evidence is required" }
    require(allocationPaths.isNotEmpty()) { "GC profiler JMH evidence is required" }
    require(sizePaths.isNotEmpty()) { "output size evidence is required" }

    val latency = parseJmhEvidence(request, latencyPaths, eligibleKeys, JmhMetric.LATENCY)
    val allocation = parseJmhEvidence(request, allocationPaths, eligibleKeys, JmhMetric.ALLOCATION)
    require(latency.observations.keys == eligibleKeys) { "latency cell coverage differs from eligibility" }
    require(allocation.observations.keys == eligibleKeys) { "allocation cell coverage differs from eligibility" }

    val sizeObservations = LinkedHashMap<CodecMatrixCellKey, CodecMatrixSizeObservation>()
    sizePaths.forEach { path ->
        requireSafeRegularFile(path, "size evidence")
        val manifest = CodecMatrixJson.readSizes(path, CodecMatrixJson.sha256(Files.readAllBytes(path)))
        require(manifest.runId == request.runId) { "size evidence run ID differs" }
        manifest.observations.forEach { observation ->
            require(observation.key.backend == manifest.backend) { "size evidence backend differs" }
            require(sizeObservations.put(observation.key, observation) == null) {
                "duplicate size observation: ${observation.key}"
            }
        }
    }
    require(sizeObservations.keys == eligibleKeys) { "size cell coverage differs from eligibility" }

    val autoArtifacts = latency.artifacts + allocation.artifacts + sizePaths.map { path ->
        path.toArtifact(request.stagingDirectory)
    }
    return CodecMatrixFinalizedManifest(
        runId = request.runId,
        expectedCellCount = eligibility.expectedCellCount,
        cells = eligibility.cells.map { cell ->
            if (cell.key !in eligibleKeys) return@map cell
            val size = requireNotNull(sizeObservations[cell.key])
            CodecMatrixCell(
                key = cell.key,
                status = CodecMatrixCellStatus.MEASURED,
                metrics = CodecMatrixMetrics(
                    latencyMs = requireNotNull(latency.observations[cell.key]),
                    allocationBytesPerOp = requireNotNull(allocation.observations[cell.key]),
                    inputBytes = size.inputBytes,
                    outputBytes = size.outputBytes,
                    outputSha256 = size.outputSha256,
                ),
            )
        },
        artifacts = (eligibility.artifacts + autoArtifacts).distinctBy(CodecMatrixArtifact::path),
    )
}

private fun parseJmhEvidence(
    request: CodecMatrixFinalizeRequest,
    paths: List<Path>,
    eligibleKeys: Set<CodecMatrixCellKey>,
    metric: JmhMetric,
): ParsedJmhEvidence {
    val observations = LinkedHashMap<CodecMatrixCellKey, Double>()
    val artifacts = ArrayList<CodecMatrixArtifact>()
    paths.forEach { path ->
        requireSafeRegularFile(path, "JMH evidence")
        val root = CodecMatrixJson.readStrictJsonElement(path, CodecMatrixJson.sha256(Files.readAllBytes(path)))
        val rows = root.jsonArray
        require(rows.isNotEmpty() && rows.size <= CODEC_MATRIX_MAX_CELLS) { "JMH row count is outside the matrix limit" }
        val backend = backendFromEvidenceName(path.fileName.toString())
        val sanitizedRows = rows.map { element ->
            val row = element.jsonObject
            validateJmhProtocol(row)
            val boundary = benchmarkBoundary(row.requiredString("benchmark"))
            val scenario = scenarioFromJmh(row.requiredObject("params").requiredString("scenario"))
            val key = eligibleKeys.singleOrNull { candidate ->
                candidate.backend == backend &&
                        candidate.scenario == scenario &&
                        candidate.format == boundary.format &&
                        candidate.direction == boundary.direction
            } ?: throw IllegalArgumentException("JMH row does not identify exactly one eligible cell")
            val score = when (metric) {
                JmhMetric.LATENCY -> row.requiredObject("primaryMetric").requiredFiniteScore("score", "ms/op")
                JmhMetric.ALLOCATION -> row.requiredObject("secondaryMetrics")
                    .requiredObject("gc.alloc.rate.norm")
                    .requiredFiniteScore("score", "B/op")
            }
            require(observations.put(key, score) == null) { "duplicate JMH cell: $key" }
            sanitizeJmhRow(row, metric)
        }
        val relative = CodecMatrixRelativePath("evidence/${path.fileName}")
        val sanitizedPath = request.stagingDirectory.resolve(relative.value)
        Files.createDirectories(requireNotNull(sanitizedPath.parent))
        CodecMatrixJson.writeStrictJson(sanitizedPath, JsonArray(sanitizedRows))
        validateNoEvidenceLeakage(sanitizedPath)
        artifacts += sanitizedPath.toArtifact(request.stagingDirectory)
    }
    return ParsedJmhEvidence(observations, artifacts)
}

private fun validateJmhProtocol(row: JsonObject) {
    require(row.requiredString("mode") == "avgt") { "JMH mode must be avgt" }
    require(row.requiredInt("threads") == 1) { "JMH threads must be 1" }
    require(row.requiredInt("forks") == 1) { "JMH forks must be 1" }
    require(row.requiredInt("warmupIterations") == 1) { "JMH warmupIterations must be 1" }
    require(row.requiredInt("measurementIterations") == 3) { "JMH measurementIterations must be 3" }
    require(row.requiredString("warmupTime") == "1 s") { "JMH warmupTime must be 1 s" }
    require(row.requiredString("measurementTime") == "1 s") { "JMH measurementTime must be 1 s" }
    row.requiredObject("primaryMetric").requiredFiniteScore("score", "ms/op")
}

private fun sanitizeJmhRow(row: JsonObject, metric: JmhMetric): JsonObject {
    val fields = linkedMapOf<String, JsonElement>(
        "benchmark" to requireNotNull(row["benchmark"]),
        "params" to requireNotNull(row["params"]),
        "mode" to requireNotNull(row["mode"]),
        "threads" to requireNotNull(row["threads"]),
        "forks" to requireNotNull(row["forks"]),
        "warmupIterations" to requireNotNull(row["warmupIterations"]),
        "warmupTime" to requireNotNull(row["warmupTime"]),
        "measurementIterations" to requireNotNull(row["measurementIterations"]),
        "measurementTime" to requireNotNull(row["measurementTime"]),
        "primaryMetric" to sanitizedMetric(row.requiredObject("primaryMetric")),
    )
    if (metric == JmhMetric.ALLOCATION) {
        fields["secondaryMetrics"] = JsonObject(
            mapOf("gc.alloc.rate.norm" to sanitizedMetric(
                row.requiredObject("secondaryMetrics").requiredObject("gc.alloc.rate.norm"),
            )),
        )
    }
    return JsonObject(fields)
}

private fun sanitizedMetric(metric: JsonObject): JsonObject = JsonObject(
    mapOf(
        "score" to requireNotNull(metric["score"]),
        "scoreUnit" to requireNotNull(metric["scoreUnit"]),
    ),
)

private fun benchmarkBoundary(benchmark: String): JmhBoundary {
    val method = benchmark.substringAfterLast('.')
    return when (method) {
        "encodePngFromJpeg" -> JmhBoundary(CodecMatrixFormat.PNG, CodecMatrixDirection.ENCODE)
        "decodePngToJpeg" -> JmhBoundary(CodecMatrixFormat.PNG, CodecMatrixDirection.DECODE)
        "encodeWebpFromJpeg" -> JmhBoundary(CodecMatrixFormat.WEBP, CodecMatrixDirection.ENCODE)
        "decodeWebpToJpeg" -> JmhBoundary(CodecMatrixFormat.WEBP, CodecMatrixDirection.DECODE)
        "encodeAvifFromJpeg" -> JmhBoundary(CodecMatrixFormat.AVIF, CodecMatrixDirection.ENCODE)
        "decodeAvifToJpeg" -> JmhBoundary(CodecMatrixFormat.AVIF, CodecMatrixDirection.DECODE)
        "encodeHeicFromJpeg" -> JmhBoundary(CodecMatrixFormat.HEIC, CodecMatrixDirection.ENCODE)
        "decodeHeicToJpeg" -> JmhBoundary(CodecMatrixFormat.HEIC, CodecMatrixDirection.DECODE)
        else -> throw IllegalArgumentException("unsupported codec matrix benchmark method: $method")
    }
}

private fun scenarioFromJmh(value: String): CodecMatrixScenario = when (value) {
    "web-photo" -> CodecMatrixScenario.WEB_PHOTO
    "profile" -> CodecMatrixScenario.PROFILE
    else -> throw IllegalArgumentException("unsupported codec matrix scenario: $value")
}

private fun backendFromEvidenceName(name: String): CodecMatrixBackendId = when {
    name.contains("java21") -> CodecMatrixBackendId.JAVA21
    name.contains("java25") -> CodecMatrixBackendId.JAVA25
    else -> throw IllegalArgumentException("JMH evidence filename does not identify a backend: $name")
}

private fun JsonObject.requiredObject(name: String): JsonObject =
    requireNotNull(this[name]) { "JMH field is required: $name" }.jsonObject

private fun JsonObject.requiredString(name: String): String =
    requireNotNull(this[name]) { "JMH field is required: $name" }.jsonPrimitive.content

private fun JsonObject.requiredInt(name: String): Int =
    requireNotNull(this[name]) { "JMH field is required: $name" }.jsonPrimitive.int

private fun JsonObject.requiredFiniteScore(name: String, unit: String): Double {
    require(requiredString("scoreUnit") == unit) { "JMH score unit must be $unit" }
    return requireNotNull(this[name]) { "JMH score is required" }.jsonPrimitive.double.also { score ->
        require(score.isFinite() && score >= 0.0) { "JMH score must be finite and non-negative" }
    }
}

private fun Path.toArtifact(root: Path): CodecMatrixArtifact {
    val relative = root.relativize(this)
    val bytes = Files.readAllBytes(this)
    return CodecMatrixArtifact(
        path = CodecMatrixRelativePath(relative.joinToString("/") { it.toString() }),
        sha256 = CodecMatrixJson.sha256(bytes),
        byteCount = bytes.size.toLong(),
    )
}

private fun listEvidenceFiles(root: Path, prefix: String): List<Path> =
    Files.newDirectoryStream(root) { path ->
        val name = path.fileName.toString()
        name.startsWith(prefix) && name.endsWith(".json")
    }.use { paths -> paths.toList().sortedBy { it.fileName.toString() } }

private fun validateSupersedes(request: CodecMatrixFinalizeRequest) {
    val runId = request.supersedes ?: return
    val manifestPath = request.acceptedRoot.resolve("${runId.value}/$RUN_MANIFEST_FILE")
    requireSafeRegularFile(manifestPath, "superseded run manifest")
    val manifest = CodecMatrixJson.readFinalized(
        manifestPath,
        CodecMatrixJson.sha256(Files.readAllBytes(manifestPath)),
    )
    require(manifest.runId == runId) { "superseded run manifest identity differs" }
}

private fun validateFailedReplacement(request: CodecMatrixFinalizeRequest): CodecMatrixFailedAttemptReference? {
    val runId = request.replacesFailedAttempt ?: return null
    val manifestPath = request.failedRoot.resolve("${runId.value}/$ATTEMPT_MANIFEST_FILE")
    requireSafeRegularFile(manifestPath, "replaced failed attempt manifest")
    val bytes = Files.readAllBytes(manifestPath)
    val sha256 = CodecMatrixJson.sha256(bytes)
    val manifest = CodecMatrixJson.readFailedAttempt(manifestPath, sha256)
    require(manifest.runId == runId) { "replaced failed attempt identity differs" }
    return CodecMatrixFailedAttemptReference(runId, sha256)
}

private fun promoteAccepted(
    request: CodecMatrixFinalizeRequest,
    eligibilityPath: Path,
    manifest: CodecMatrixFinalizedManifest,
    target: Path,
    mover: CodecMatrixAtomicDirectoryMover,
) {
    val promotion = request.acceptedRoot.resolve(".${request.runId.value}.promotion-${UUID.randomUUID()}")
    try {
        Files.createDirectory(promotion)
        copyRegularFile(eligibilityPath, promotion.resolve(ELIGIBILITY_FILE))
        val measurement = request.stagingDirectory.resolve(MEASUREMENT_FILE)
        if (Files.exists(measurement, LinkOption.NOFOLLOW_LINKS)) {
            copyRegularFile(measurement, promotion.resolve(MEASUREMENT_FILE))
        }
        manifest.artifacts.forEach { artifact ->
            val source = request.stagingDirectory.resolve(artifact.path.value)
            val destination = promotion.resolve(artifact.path.value)
            Files.createDirectories(requireNotNull(destination.parent))
            copyRegularFile(source, destination)
        }
        CodecMatrixJson.write(promotion.resolve(RUN_MANIFEST_FILE), manifest)
        moveDirectoryAtomically(promotion, target, mover)
    } finally {
        deleteOwnedTree(promotion)
    }
}

private fun promoteFailedAttempt(
    request: CodecMatrixFinalizeRequest,
    eligibility: CodecMatrixEligibilityManifest,
    target: Path,
    mover: CodecMatrixAtomicDirectoryMover,
) {
    val terminalCells = eligibility.cells.map { cell ->
        if (cell.status == CodecMatrixCellStatus.ELIGIBLE) {
            cell.copy(
                status = CodecMatrixCellStatus.ERROR,
                reasonCode = CodecMatrixReasonCode.EVIDENCE_INVALID,
                reason = "attempt stopped after blocking evidence",
                rerunGuidance = "diagnose the failed attempt and use a new run ID",
            )
        } else {
            cell.copy(
                reason = cell.reason?.let(::sanitizeCodecMatrixText),
                rerunGuidance = cell.rerunGuidance?.let(::sanitizeCodecMatrixText),
            )
        }
    }
    val ledger = CodecMatrixFailedAttemptManifest(
        runId = request.runId,
        expectedCellCount = eligibility.expectedCellCount,
        cells = terminalCells,
        artifacts = eligibility.artifacts,
    ).validateFailedAttempt()
    val promotion = request.failedRoot.resolve(".${request.runId.value}.promotion-${UUID.randomUUID()}")
    try {
        Files.createDirectory(promotion)
        ledger.artifacts.forEach { artifact ->
            val source = request.stagingDirectory.resolve(artifact.path.value)
            val destination = promotion.resolve(artifact.path.value)
            Files.createDirectories(requireNotNull(destination.parent))
            copyRegularFile(source, destination)
        }
        CodecMatrixJson.write(promotion.resolve(ATTEMPT_MANIFEST_FILE), ledger)
        moveDirectoryAtomically(promotion, target, mover)
    } finally {
        deleteOwnedTree(promotion)
    }
}

private fun validateUnavailableBackendArtifacts(
    request: CodecMatrixFinalizeRequest,
    eligibility: CodecMatrixEligibilityManifest,
) {
    if (eligibility.cells.all { it.status == CodecMatrixCellStatus.N_A }) {
        require(eligibility.cells.map { it.key.backend }.toSet().size == 1) {
            "N/A evidence must describe exactly one backend"
        }
        val actualCoverage = eligibility.cells.map { cell ->
            Triple(cell.key.scenario, cell.key.format, cell.key.direction)
        }.toSet()
        require(actualCoverage == EXPECTED_N_A_COVERAGE) {
            "N/A evidence does not cover every stable and experimental matrix cell"
        }
        require(eligibility.artifacts.isEmpty()) { "N/A evidence must not reference native or numeric artifacts" }
        Files.newDirectoryStream(request.stagingDirectory).use { entries ->
            val names = entries.asSequence().map { it.fileName.toString() }.toSet()
            require(names == setOf(ELIGIBILITY_FILE)) {
                "N/A evidence must not contain native or numeric artifacts"
            }
        }
    }
}

private fun validateArtifacts(root: Path, artifacts: List<CodecMatrixArtifact>) {
    artifacts.forEach { artifact ->
        val path = root.resolve(artifact.path.value).normalize()
        require(path.startsWith(root)) { "artifact escapes the staging directory" }
        requireSafeRegularFile(path, "codec matrix artifact")
        val bytes = Files.readAllBytes(path)
        require(bytes.size.toLong() == artifact.byteCount) { "artifact byte count differs: ${artifact.path.value}" }
        require(CodecMatrixJson.sha256(bytes) == artifact.sha256) { "artifact hash differs: ${artifact.path.value}" }
    }
}

private fun copyRegularFile(source: Path, target: Path) {
    requireSafeRegularFile(source, "promotion source")
    Files.copy(source, target)
}

private fun moveDirectoryAtomically(
    source: Path,
    target: Path,
    mover: CodecMatrixAtomicDirectoryMover,
) {
    requireAbsent(target, "promotion target")
    try {
        mover.move(source, target)
    } catch (e: AtomicMoveNotSupportedException) {
        throw IllegalStateException("atomic evidence promotion is unavailable", e)
    }
}

private fun validateStagedTree(root: Path) {
    Files.walkFileTree(
        root,
        object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(directory: Path, attributes: BasicFileAttributes): FileVisitResult {
                require(!Files.isSymbolicLink(directory) && attributes.isDirectory) {
                    "staging tree contains a non-directory entry: $directory"
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                require(!Files.isSymbolicLink(file) && attributes.isRegularFile) {
                    "staging tree contains a symbolic link or non-regular file: $file"
                }
                val name = file.fileName.toString()
                if (name.substringAfterLast('.', "") in TEXT_EVIDENCE_EXTENSIONS &&
                    !name.startsWith(LATENCY_FILE_PREFIX) &&
                    !name.startsWith(ALLOCATION_FILE_PREFIX)
                ) {
                    validateNoEvidenceLeakage(file)
                }
                return FileVisitResult.CONTINUE
            }
        },
    )
}

private fun validateNoEvidenceLeakage(file: Path) {
    val bytes = Files.readAllBytes(file)
    require(bytes.size <= MAX_LEAKAGE_SCAN_BYTES) { "staged text evidence is too large" }
    val text = bytes.toString(Charsets.UTF_8)
    require(LEAKAGE_PATTERNS.none { pattern -> pattern.containsMatchIn(text) }) {
        "staged evidence contains a local path, secret-like value, or raw exception"
    }
}

private fun requireSafeExistingDirectory(path: Path, label: String) {
    requireNoSymlink(path)
    require(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) { "$label is not a directory" }
}

private fun requireNoSymlink(path: Path) {
    var current = requireNotNull(path.toAbsolutePath().normalize().root)
    path.toAbsolutePath().normalize().forEach { segment ->
        current = current.resolve(segment)
        if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            require(!Files.isSymbolicLink(current)) { "symbolic links are not allowed: $current" }
        }
    }
}

private fun requireAbsent(path: Path, label: String) {
    require(!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) { "$label already exists: $path" }
}

private fun deleteOwnedTree(path: Path) {
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
    Files.walkFileTree(
        path,
        object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                Files.deleteIfExists(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(directory: Path, exception: java.io.IOException?): FileVisitResult {
                Files.deleteIfExists(directory)
                return FileVisitResult.CONTINUE
            }
        },
    )
}

private fun CodecMatrixCell.hasBlockingStatus(): Boolean =
    status == CodecMatrixCellStatus.FAILED_SMOKE || status == CodecMatrixCellStatus.ERROR

internal object CodecMatrixFinalizeMain {
    @JvmStatic
    fun main(arguments: Array<String>) {
        val parsed = parseCodecMatrixFinalizeArguments(arguments)
        val repositoryRoot = Path.of("").toAbsolutePath().normalize()
        requireSafeRegularFile(repositoryRoot.resolve("settings.gradle.kts"), "repository settings")
        val moduleRoot = repositoryRoot.resolve("benchmark/images-benchmark")
        val acceptedRoot = moduleRoot.resolve("docs/raw")
        val failedRoot = acceptedRoot.resolve("failed")
        createSafeDirectories(acceptedRoot)
        createSafeDirectories(failedRoot)
        finalizeCodecMatrixEvidence(
            CodecMatrixFinalizeRequest(
                runId = parsed.runId,
                stagingRoot = moduleRoot.resolve("build/codec-matrix"),
                acceptedRoot = acceptedRoot,
                failedRoot = failedRoot,
                supersedes = parsed.supersedes,
                replacesFailedAttempt = parsed.replacesFailedAttempt,
            ),
        )
    }
}

private fun createSafeDirectories(path: Path) {
    requireNoSymlink(path)
    Files.createDirectories(path)
    requireSafeExistingDirectory(path, "evidence root")
}

private class ParsedJmhEvidence(
    val observations: Map<CodecMatrixCellKey, Double>,
    val artifacts: List<CodecMatrixArtifact>,
)

private class JmhBoundary(
    val format: CodecMatrixFormat,
    val direction: CodecMatrixDirection,
)

private enum class JmhMetric {
    LATENCY,
    ALLOCATION,
}

private val FINALIZE_OPTIONS = setOf("--run-id", "--supersedes", "--replaces-failed-attempt")
private const val ELIGIBILITY_FILE = "eligibility.json"
private const val MEASUREMENT_FILE = "measurement.json"
private const val RUN_MANIFEST_FILE = "run-manifest.json"
private const val ATTEMPT_MANIFEST_FILE = "attempt-manifest.json"
private const val LATENCY_FILE_PREFIX = "latency-"
private const val ALLOCATION_FILE_PREFIX = "allocation-"
private const val SIZE_FILE_PREFIX = "sizes-"
private const val MAX_LEAKAGE_SCAN_BYTES = 1_048_576
private val TEXT_EVIDENCE_EXTENSIONS = setOf("json", "txt", "log", "md")
private val LEAKAGE_PATTERNS = listOf(
    Regex("(?i)(?:password|passwd|token|secret|api[_-]?key)\\s*[:=]\\s*[^\\s,}]+"),
    Regex("(?<![A-Za-z0-9])/(?:Users|home|tmp|private|var)/[^\\s\\\"]+"),
    Regex("(?i)[A-Z]:\\\\(?:Users|Temp|Windows)\\\\[^\\s\\\"]+"),
    Regex("(?i)\\b(?:exception|stacktrace|caused by)\\b"),
)
private val DEFAULT_ATOMIC_MOVER = CodecMatrixAtomicDirectoryMover { source, target ->
    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
}
private val EXPECTED_N_A_COVERAGE = buildSet {
    CodecMatrixScenario.entries.forEach { scenario ->
        listOf(
            CodecMatrixFormat.PNG,
            CodecMatrixFormat.WEBP,
            CodecMatrixFormat.AVIF,
            CodecMatrixFormat.HEIC,
        ).forEach { format ->
            CodecMatrixDirection.entries.forEach { direction -> add(Triple(scenario, format, direction)) }
        }
    }
}
