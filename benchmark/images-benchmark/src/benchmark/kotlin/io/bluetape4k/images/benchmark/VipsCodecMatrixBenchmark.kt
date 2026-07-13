package io.bluetape4k.images.benchmark

import io.bluetape4k.images.vips.VipsEncodeOptions
import io.bluetape4k.images.vips.VipsImage
import io.bluetape4k.images.vips.VipsImageFormat
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.benchmark.Warmup
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Threads

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Benchmark)
class VipsCodecMatrixBenchmark {

    @Benchmark
    fun encodePngFromJpeg(state: VipsCodecMatrixState, bh: Blackhole) {
        state.open(state.jpegBytes).use { image ->
            bh.consume(image.toBytes(VipsImageFormat.PNG, state.encodeOptions))
        }
    }

    @Benchmark
    fun decodePngToJpeg(state: VipsCodecMatrixState, bh: Blackhole) {
        state.open(state.pngBytes).use { image ->
            bh.consume(image.toBytes(VipsImageFormat.JPEG, state.encodeOptions))
        }
    }

    @Benchmark
    fun encodeWebpFromJpeg(state: VipsCodecMatrixState, bh: Blackhole) {
        state.open(state.jpegBytes).use { image ->
            bh.consume(image.toBytes(VipsImageFormat.WEBP, state.encodeOptions))
        }
    }

    @Benchmark
    fun decodeWebpToJpeg(state: VipsCodecMatrixState, bh: Blackhole) {
        state.open(state.webpBytes).use { image ->
            bh.consume(image.toBytes(VipsImageFormat.JPEG, state.encodeOptions))
        }
    }
}

@State(Scope.Thread)
class VipsCodecMatrixState {

    @Param("web-photo", "profile")
    lateinit var scenario: String

    val encodeOptions = VipsEncodeOptions(quality = 85, effort = 4, lossless = false, stripMetadata = true)

    val jpegBytes: ByteArray get() = requireNotNull(jpegInput) { "JPEG fixture is not initialized" }
    val pngBytes: ByteArray get() = requireNotNull(pngInput) { "PNG fixture is not initialized" }
    val webpBytes: ByteArray get() = requireNotNull(webpInput) { "WebP fixture is not initialized" }

    private var jpegInput: ByteArray? = null
    private var pngInput: ByteArray? = null
    private var webpInput: ByteArray? = null
    private var adapter: CodecMatrixRuntimeAdapter? = null

    @Setup(Level.Trial)
    fun setup() {
        val runId = CodecMatrixRunId(requiredSystemProperty("codec.matrix.runId"))
        val backend = CodecMatrixBackend.parse(requiredSystemProperty("codec.matrix.backend"))
        val preflightPath = requiredAbsolutePath("codec.matrix.preflight")
        val fixtureManifestPath = requiredAbsolutePath("codec.matrix.fixtureManifest")
        val runDirectory = requireNotNull(fixtureManifestPath.parent?.parent) {
            "fixture manifest must have a run directory"
        }
        require(runDirectory.fileName.toString() == runId.value) { "fixture run directory differs from run ID" }
        require(fixtureManifestPath == runDirectory.resolve("fixtures/manifest.json")) {
            "fixture manifest path differs from the selected run"
        }
        require(preflightPath == runDirectory.resolve("preflight-${backend.selector}.json")) {
            "preflight path differs from the selected run and backend"
        }

        val preflight = readPreflight(preflightPath)
        require(preflight.runId == runId) { "preflight run ID differs" }
        require(preflight.requestedSelector == backend.selector) { "preflight selector differs" }
        require(preflight.requestedBackend == backend.id) { "preflight backend differs" }
        require(preflight.status == CodecMatrixCellStatus.ELIGIBLE) {
            "codec matrix backend is not eligible: ${preflight.status}"
        }
        require(preflight.facts.jdkMajor == backend.expectedJavaMajor) { "preflight JDK differs" }

        val manifest = readFixtureManifest(fixtureManifestPath)
        require(manifest.runId == runId) { "fixture manifest run ID differs" }
        require(manifest.recipe == CodecMatrixTransformRecipe.COVER_CENTER_CROP_V1) {
            "fixture transform recipe differs"
        }
        require(manifest.options == EXPECTED_FIXTURE_OPTIONS) { "fixture codec options differ" }
        require(manifest.fixtures.map(CodecMatrixFixtureEntry::scenario).toSet() == CodecMatrixScenario.entries.toSet()) {
            "fixture scenarios differ"
        }

        val loadedFixtures = manifest.fixtures.associate { fixture ->
            fixture.scenario to validateAndLoadFixture(runDirectory, fixture)
        }
        val selectedScenario = scenario.toCodecMatrixScenario()
        val selected = requireNotNull(loadedFixtures[selectedScenario]) { "selected fixture is missing" }
        val runtimeAdapter = CodecMatrixRuntimeAdapter.create(backend)
        loadedFixtures.values.flatMap(Map<CodecMatrixFormat, LoadedFixtureInput>::values).forEach { input ->
            runtimeAdapter.openImage(input.bytes).use { image ->
                require(image.width == input.dimensions.width && image.height == input.dimensions.height) {
                    "native fixture dimensions differ"
                }
            }
        }

        adapter = runtimeAdapter
        jpegInput = requireNotNull(selected[CodecMatrixFormat.JPEG]).bytes
        pngInput = requireNotNull(selected[CodecMatrixFormat.PNG]).bytes
        webpInput = requireNotNull(selected[CodecMatrixFormat.WEBP]).bytes
    }

    @TearDown(Level.Trial)
    fun tearDown() {
        jpegInput = null
        pngInput = null
        webpInput = null
        adapter = null
    }

    fun open(bytes: ByteArray): VipsImage =
        requireNotNull(adapter) { "codec matrix runtime is not initialized" }.openImage(bytes)
}

private class LoadedFixtureInput(
    val bytes: ByteArray,
    val dimensions: CodecMatrixDimensions,
)

private fun readPreflight(path: Path): CodecMatrixPreflightManifest {
    requireSafeRegularFile(path, "codec matrix preflight")
    val bytes = Files.readAllBytes(path)
    return CodecMatrixJson.readPreflight(path, CodecMatrixJson.sha256(bytes))
}

private fun readFixtureManifest(path: Path): CodecMatrixFixtureManifest {
    requireSafeRegularFile(path, "codec matrix fixture manifest")
    val bytes = Files.readAllBytes(path)
    return CodecMatrixJson.readFixture(path, CodecMatrixJson.sha256(bytes))
}

private fun validateAndLoadFixture(
    runDirectory: Path,
    fixture: CodecMatrixFixtureEntry,
): Map<CodecMatrixFormat, LoadedFixtureInput> {
    val expected = fixture.scenario.expectedFixtureDefinition()
    require(fixture.source.path.value == expected.sourcePath) { "fixture source path differs" }
    require(fixture.source.sha256 == expected.sourceSha256) { "fixture source hash differs" }
    require(fixture.source.dimensions == expected.sourceDimensions) { "fixture source dimensions differ" }
    require(fixture.derived.dimensions == expected.targetDimensions) { "fixture target dimensions differ" }
    require(fixture.inputs.map(CodecMatrixInput::format).toSet() == STABLE_FIXTURE_FORMATS) {
        "stable fixture formats differ"
    }

    val scenarioName = fixture.scenario.parameterName()
    val loaded = fixture.inputs.associate { input ->
        require(input.path.value == "fixtures/$scenarioName/input.${input.format.extension()}") {
            "fixture input path differs"
        }
        require(input.dimensions == expected.targetDimensions) { "fixture input dimensions differ" }
        val path = runDirectory.resolve(input.path.value).normalize()
        require(path.startsWith(runDirectory)) { "fixture input escapes the run directory" }
        requireSafeRegularFile(path, "codec matrix fixture input")
        val bytes = Files.readAllBytes(path)
        require(bytes.size.toLong() == input.byteCount) { "fixture input byte count differs" }
        require(CodecMatrixJson.sha256(bytes) == input.sha256) { "fixture input hash differs" }
        require(codecMatrixMagic(input.format, bytes) == input.magic && input.magic.valid) {
            "fixture input magic differs"
        }
        input.format to LoadedFixtureInput(bytes, input.dimensions)
    }
    val png = requireNotNull(fixture.inputs.singleOrNull { input -> input.format == CodecMatrixFormat.PNG })
    require(fixture.derived.path == png.path) { "derived fixture path differs from PNG input" }
    require(fixture.derived.sha256 == png.sha256) { "derived fixture hash differs from PNG input" }
    require(fixture.derived.byteCount == png.byteCount) { "derived fixture byte count differs from PNG input" }
    return loaded
}

private class ExpectedFixtureDefinition(
    val sourcePath: String,
    val sourceSha256: CodecMatrixSha256,
    val sourceDimensions: CodecMatrixDimensions,
    val targetDimensions: CodecMatrixDimensions,
)

private fun CodecMatrixScenario.expectedFixtureDefinition(): ExpectedFixtureDefinition = when (this) {
    CodecMatrixScenario.WEB_PHOTO -> ExpectedFixtureDefinition(
        sourcePath = "generated/codec-matrix-source-fixtures/cafe.jpg",
        sourceSha256 = CodecMatrixSha256("ec07fa417de74dfb0c425bf2099ce9c4df508f111e4ab2e7f4057db2eb10205e"),
        sourceDimensions = CodecMatrixDimensions(4032, 3024),
        targetDimensions = CodecMatrixDimensions(1920, 1080),
    )
    CodecMatrixScenario.PROFILE -> ExpectedFixtureDefinition(
        sourcePath = "generated/codec-matrix-source-fixtures/homer.jpg",
        sourceSha256 = CodecMatrixSha256("66a14651276f98767d9459eb6091d3b6881f0c912f01f161b41b38a5ae9577c6"),
        sourceDimensions = CodecMatrixDimensions(1248, 702),
        targetDimensions = CodecMatrixDimensions(512, 512),
    )
}

private fun String.toCodecMatrixScenario(): CodecMatrixScenario = when (this) {
    "web-photo" -> CodecMatrixScenario.WEB_PHOTO
    "profile" -> CodecMatrixScenario.PROFILE
    else -> throw IllegalArgumentException("unknown codec matrix scenario: $this")
}

private fun CodecMatrixScenario.parameterName(): String = when (this) {
    CodecMatrixScenario.WEB_PHOTO -> "web-photo"
    CodecMatrixScenario.PROFILE -> "profile"
}

private fun CodecMatrixFormat.extension(): String = when (this) {
    CodecMatrixFormat.JPEG -> "jpg"
    CodecMatrixFormat.PNG -> "png"
    CodecMatrixFormat.WEBP -> "webp"
    else -> throw IllegalArgumentException("stable fixture extension is unavailable for $this")
}

private fun requiredSystemProperty(name: String): String =
    System.getProperty(name)?.takeIf(String::isNotBlank)
        ?: throw IllegalArgumentException("required system property is missing: $name")

private fun requiredAbsolutePath(name: String): Path {
    val path = Path.of(requiredSystemProperty(name))
    require(path.isAbsolute && path == path.normalize()) { "$name must be absolute and normalized" }
    return path
}

private val EXPECTED_FIXTURE_OPTIONS = CodecMatrixCodecOptions(
    jpegQuality = 85,
    jpegProgressive = false,
    pngCompression = 4,
    webpLosslessLevel = -1,
    webpQuality = 85,
    webpMethod = 4,
    webpLossless = false,
    webpNoAlpha = false,
)

private val STABLE_FIXTURE_FORMATS = setOf(
    CodecMatrixFormat.JPEG,
    CodecMatrixFormat.PNG,
    CodecMatrixFormat.WEBP,
)
