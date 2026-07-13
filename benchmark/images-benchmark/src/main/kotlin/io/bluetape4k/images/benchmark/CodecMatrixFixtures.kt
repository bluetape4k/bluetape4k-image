package io.bluetape4k.images.benchmark

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.Position
import com.sksamuel.scrimage.nio.ImmutableImageLoader
import com.sksamuel.scrimage.nio.JpegWriter
import com.sksamuel.scrimage.nio.PngWriter
import com.sksamuel.scrimage.webp.WebpWriter
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID

private const val CAFE_SOURCE = "cafe.jpg"
private const val HOMER_SOURCE = "homer.jpg"
private const val FIXTURE_MANIFEST = "fixtures/manifest.json"

private val JPEG_WRITER = JpegWriter(85, false)
private val PNG_WRITER = PngWriter(4)
private val WEBP_WRITER = WebpWriter(-1, 85, 4, false, false)

private val FIXED_CODEC_OPTIONS = CodecMatrixCodecOptions(
    jpegQuality = 85,
    jpegProgressive = false,
    pngCompression = 4,
    webpLosslessLevel = -1,
    webpQuality = 85,
    webpMethod = 4,
    webpLossless = false,
    webpNoAlpha = false,
)

internal data class CodecMatrixFixturePreparationRequest(
    val generatedSources: Path,
    val runDirectory: Path,
    val runId: CodecMatrixRunId,
)

private data class CanonicalFixtureDefinition(
    val scenario: CodecMatrixScenario,
    val sourceName: String,
    val sourceDimensions: CodecMatrixDimensions,
    val targetDimensions: CodecMatrixDimensions,
)

private data class PreparedFixture(
    val entry: CodecMatrixFixtureEntry,
    val files: Map<CodecMatrixRelativePath, ByteArray>,
)

internal fun prepareCodecMatrixFixtures(
    request: CodecMatrixFixturePreparationRequest,
): CodecMatrixFixtureManifest {
    val requestedSources = requireAbsoluteNormalized(request.generatedSources, "generatedSources")
    val requestedRunDirectory = requireAbsoluteNormalized(request.runDirectory, "runDirectory")
    requireNoSymlinkInExistingPath(requestedSources)
    requireNoSymlinkInExistingPath(requestedRunDirectory)
    require(!Files.isSymbolicLink(requestedSources)) { "generatedSources must not be a symbolic link" }
    require(!Files.isSymbolicLink(requestedRunDirectory)) { "runDirectory must not be a symbolic link" }
    val generatedSources = requestedSources.toRealPath()
    val runDirectory = canonicalizePotentialPath(requestedRunDirectory)
    requireSafeDirectory(generatedSources, "generatedSources")

    val prepared = canonicalFixtureDefinitions().map { definition ->
        prepareFixture(generatedSources, definition)
    }
    val manifest = CodecMatrixFixtureManifest(
        runId = request.runId,
        recipe = CodecMatrixTransformRecipe.COVER_CENTER_CROP_V1,
        options = FIXED_CODEC_OPTIONS,
        fixtures = prepared.map(PreparedFixture::entry),
    )
    val expectedFiles = buildMap {
        prepared.forEach { putAll(it.files) }
        put(CodecMatrixRelativePath(FIXTURE_MANIFEST), CodecMatrixJson.encode(manifest).toByteArray())
    }

    publishFixtures(runDirectory, expectedFiles)
    return manifest
}

private fun canonicalFixtureDefinitions(): List<CanonicalFixtureDefinition> = listOf(
    CanonicalFixtureDefinition(
        scenario = CodecMatrixScenario.WEB_PHOTO,
        sourceName = CAFE_SOURCE,
        sourceDimensions = CodecMatrixDimensions(4032, 3024),
        targetDimensions = CodecMatrixDimensions(1920, 1080),
    ),
    CanonicalFixtureDefinition(
        scenario = CodecMatrixScenario.PROFILE,
        sourceName = HOMER_SOURCE,
        sourceDimensions = CodecMatrixDimensions(1248, 702),
        targetDimensions = CodecMatrixDimensions(512, 512),
    ),
)

private fun prepareFixture(
    generatedSources: Path,
    definition: CanonicalFixtureDefinition,
): PreparedFixture {
    val sourcePath = generatedSources.resolve(definition.sourceName).normalize()
    require(sourcePath.parent == generatedSources) { "fixture source must be a direct child of generatedSources" }
    requireSafeRegularFile(sourcePath, "generated fixture ${definition.sourceName}")
    val sourceBytes = Files.readAllBytes(sourcePath)
    val loader = ImmutableImageLoader.create().detectMetadata(false).detectOrientation(false)
    val sourceImage = loader.fromBytes(sourceBytes)
    require(sourceImage.codecDimensions() == definition.sourceDimensions) {
        "unexpected source dimensions for ${definition.sourceName}: ${sourceImage.width}x${sourceImage.height}"
    }

    val target = definition.targetDimensions
    val derived = sourceImage.cover(target.width, target.height, Position.Center)
    require(derived.codecDimensions() == target) { "derived dimensions differ from the canonical target" }

    val encoded = linkedMapOf(
        CodecMatrixFormat.JPEG to derived.forWriter(JPEG_WRITER).bytes(),
        CodecMatrixFormat.PNG to derived.forWriter(PNG_WRITER).bytes(),
        CodecMatrixFormat.WEBP to derived.forWriter(WEBP_WRITER).bytes(),
    )
    val scenarioPath = definition.scenario.serializedName()
    val files = linkedMapOf<CodecMatrixRelativePath, ByteArray>()
    val inputs = encoded.map { (format, bytes) ->
        val path = CodecMatrixRelativePath("fixtures/$scenarioPath/input.${format.extension()}")
        val magic = codecMatrixMagic(format, bytes)
        require(magic.valid) { "invalid ${format.name} magic for ${definition.scenario}" }
        require(bytes.isNotEmpty()) { "encoded ${format.name} fixture is empty" }
        val decoded = loader.fromBytes(bytes)
        require(decoded.codecDimensions() == target) { "encoded ${format.name} dimensions differ from the target" }
        files[path] = bytes
        CodecMatrixInput(
            format = format,
            path = path,
            sha256 = CodecMatrixJson.sha256(bytes),
            byteCount = bytes.size.toLong(),
            dimensions = target,
            magic = magic,
        )
    }
    val pngInput = inputs.single { it.format == CodecMatrixFormat.PNG }
    val sourceRecord = CodecMatrixFileRecord(
        path = CodecMatrixRelativePath("generated/codec-matrix-source-fixtures/${definition.sourceName}"),
        sha256 = CodecMatrixJson.sha256(sourceBytes),
        byteCount = sourceBytes.size.toLong(),
        dimensions = definition.sourceDimensions,
    )
    val derivedRecord = CodecMatrixFileRecord(
        path = pngInput.path,
        sha256 = pngInput.sha256,
        byteCount = pngInput.byteCount,
        dimensions = target,
    )
    return PreparedFixture(
        entry = CodecMatrixFixtureEntry(
            scenario = definition.scenario,
            source = sourceRecord,
            derived = derivedRecord,
            inputs = inputs,
        ),
        files = files,
    )
}

private fun publishFixtures(
    runDirectory: Path,
    expectedFiles: Map<CodecMatrixRelativePath, ByteArray>,
) {
    val fixtureDirectory = runDirectory.resolve("fixtures")
    if (Files.exists(fixtureDirectory, LinkOption.NOFOLLOW_LINKS)) {
        requireSafeDirectory(fixtureDirectory, "existing fixture directory")
        val empty = Files.newDirectoryStream(fixtureDirectory).use { entries ->
            !entries.iterator().hasNext()
        }
        if (empty) {
            Files.delete(fixtureDirectory)
        } else {
            validateExistingFixtures(runDirectory, expectedFiles)
            return
        }
    }

    Files.createDirectories(runDirectory)
    val stagingDirectory = runDirectory.resolve(".fixtures.tmp-${UUID.randomUUID()}")
    try {
        expectedFiles.forEach { (relativePath, bytes) ->
            val stagingPath = stagingDirectory.resolve(relativePath.value.removePrefix("fixtures/"))
            Files.createDirectories(requireNotNull(stagingPath.parent))
            Files.write(stagingPath, bytes)
        }
        Files.move(stagingDirectory, fixtureDirectory, StandardCopyOption.ATOMIC_MOVE)
    } catch (e: Exception) {
        deleteRecursively(stagingDirectory)
        if (Files.exists(fixtureDirectory, LinkOption.NOFOLLOW_LINKS)) {
            validateExistingFixtures(runDirectory, expectedFiles)
            return
        }
        throw e
    }
}

private fun validateExistingFixtures(
    runDirectory: Path,
    expectedFiles: Map<CodecMatrixRelativePath, ByteArray>,
) {
    val fixtureDirectory = runDirectory.resolve("fixtures")
    requireSafeDirectory(fixtureDirectory, "existing fixture directory")
    val actualPaths = Files.walk(fixtureDirectory).use { paths ->
        paths.filter { path -> !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) }.map { path ->
            requireSafeRegularFile(path, "existing fixture content")
            CodecMatrixRelativePath(runDirectory.relativize(path).joinToString("/") { it.toString() })
        }.toList().toSet()
    }
    require(actualPaths == expectedFiles.keys) { "existing fixture file set differs from canonical content" }
    expectedFiles.forEach { (relativePath, expectedBytes) ->
        val actualPath = runDirectory.resolve(relativePath.value)
        require(Files.readAllBytes(actualPath).contentEquals(expectedBytes)) {
            "existing fixture differs from canonical content: ${relativePath.value}"
        }
    }
}

internal fun codecMatrixMagic(format: CodecMatrixFormat, bytes: ByteArray): CodecMatrixMagic {
    val valid = when (format) {
        CodecMatrixFormat.JPEG -> bytes.startsWith(0xFF, 0xD8, 0xFF)
        CodecMatrixFormat.PNG -> bytes.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        CodecMatrixFormat.WEBP -> bytes.startsWithAscii("RIFF") && bytes.hasAsciiAt(8, "WEBP")
        CodecMatrixFormat.AVIF -> bytes.hasIsoBmffBrand("avif") || bytes.hasIsoBmffBrand("avis")
        CodecMatrixFormat.HEIC -> listOf("heic", "heix", "hevc", "hevx").any(bytes::hasIsoBmffBrand)
    }
    val signature = when (format) {
        CodecMatrixFormat.JPEG -> "FFD8FF"
        CodecMatrixFormat.PNG -> "89504E470D0A1A0A"
        CodecMatrixFormat.WEBP -> "RIFF....WEBP"
        CodecMatrixFormat.AVIF -> "ftypavif"
        CodecMatrixFormat.HEIC -> "ftypheic"
    }
    return CodecMatrixMagic(signature, valid)
}

private fun ByteArray.hasIsoBmffBrand(brand: String): Boolean =
    hasAsciiAt(4, "ftyp") && hasAsciiAt(8, brand)

private fun ImmutableImage.codecDimensions(): CodecMatrixDimensions = CodecMatrixDimensions(width, height)

private fun CodecMatrixScenario.serializedName(): String = when (this) {
    CodecMatrixScenario.WEB_PHOTO -> "web-photo"
    CodecMatrixScenario.PROFILE -> "profile"
}

private fun CodecMatrixFormat.extension(): String = when (this) {
    CodecMatrixFormat.JPEG -> "jpg"
    CodecMatrixFormat.PNG -> "png"
    CodecMatrixFormat.WEBP -> "webp"
    CodecMatrixFormat.AVIF -> "avif"
    CodecMatrixFormat.HEIC -> "heic"
}

private fun ByteArray.startsWith(vararg expected: Int): Boolean =
    size >= expected.size && expected.indices.all { index -> this[index].toInt() and 0xFF == expected[index] }

private fun ByteArray.startsWithAscii(expected: String): Boolean = hasAsciiAt(0, expected)

private fun ByteArray.hasAsciiAt(offset: Int, expected: String): Boolean =
    size >= offset + expected.length && expected.indices.all { index -> this[offset + index].toInt() == expected[index].code }

private fun requireAbsoluteNormalized(path: Path, label: String): Path {
    require(path.isAbsolute) { "$label must be absolute" }
    require(path == path.normalize()) { "$label must be normalized" }
    return path
}

private fun requireSafeDirectory(path: Path, label: String) {
    requireNoSymlinkInExistingPath(path)
    require(!Files.isSymbolicLink(path)) { "$label must not be a symbolic link" }
    require(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) { "$label is not a directory" }
}

internal fun requireSafeRegularFile(path: Path, label: String) {
    requireNoSymlinkInExistingPath(path)
    require(!Files.isSymbolicLink(path)) { "$label must not be a symbolic link" }
    require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) { "$label is not a regular file" }
}

private fun canonicalizePotentialPath(path: Path): Path {
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return path.toRealPath()
    val missingSegments = ArrayDeque<Path>()
    var existing: Path? = path
    while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
        missingSegments.addFirst(existing.fileName)
        existing = existing.parent
    }
    var canonical = requireNotNull(existing) { "path has no existing ancestor: $path" }.toRealPath()
    missingSegments.forEach { canonical = canonical.resolve(it) }
    return canonical
}

private fun requireNoSymlinkInExistingPath(path: Path) {
    var current = requireNotNull(path.toAbsolutePath().normalize().root)
    path.toAbsolutePath().normalize().forEach { segment ->
        current = current.resolve(segment)
        if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            require(!Files.isSymbolicLink(current)) { "symbolic links are not allowed: $current" }
        }
    }
}

private fun deleteRecursively(root: Path) {
    if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return
    Files.walkFileTree(
        root,
        object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.deleteIfExists(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult {
                Files.deleteIfExists(dir)
                return FileVisitResult.CONTINUE
            }
        },
    )
}
