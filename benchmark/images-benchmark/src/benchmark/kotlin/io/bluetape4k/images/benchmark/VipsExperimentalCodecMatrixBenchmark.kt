package io.bluetape4k.images.benchmark

import io.bluetape4k.images.vips.VipsEncodeOptions
import io.bluetape4k.images.vips.VipsImage
import io.bluetape4k.images.vips.VipsImageFormat
import io.bluetape4k.images.vips.VipsIncubatingApi
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
import org.openjdk.jmh.infra.BenchmarkParams

@OptIn(VipsIncubatingApi::class)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Benchmark)
class VipsExperimentalCodecMatrixBenchmark {

    @Benchmark
    fun encodeAvifFromJpeg(state: VipsAvifCodecMatrixState, bh: Blackhole) {
        state.open(state.inputBytes).use { image ->
            bh.consume(image.toBytes(VipsImageFormat.AVIF, state.encodeOptions))
        }
    }

    @Benchmark
    fun decodeAvifToJpeg(state: VipsAvifCodecMatrixState, bh: Blackhole) {
        state.open(state.inputBytes).use { image ->
            bh.consume(image.toBytes(VipsImageFormat.JPEG, state.encodeOptions))
        }
    }

    @Benchmark
    fun encodeHeicFromJpeg(state: VipsHeicCodecMatrixState, bh: Blackhole) {
        state.open(state.inputBytes).use { image ->
            bh.consume(image.toBytes(VipsImageFormat.HEIC, state.encodeOptions))
        }
    }

    @Benchmark
    fun decodeHeicToJpeg(state: VipsHeicCodecMatrixState, bh: Blackhole) {
        state.open(state.inputBytes).use { image ->
            bh.consume(image.toBytes(VipsImageFormat.JPEG, state.encodeOptions))
        }
    }
}

@State(Scope.Thread)
class VipsAvifCodecMatrixState {

    @Param("web-photo", "profile")
    lateinit var scenario: String

    val inputBytes: ByteArray get() = delegate.inputBytes
    val encodeOptions: VipsEncodeOptions get() = delegate.encodeOptions

    private val delegate = VipsExperimentalCodecMatrixState()

    @Setup(Level.Trial)
    fun setup(params: BenchmarkParams) {
        delegate.setup(CodecMatrixFormat.AVIF, params.direction(), scenario)
    }

    @TearDown(Level.Trial)
    fun tearDown() = delegate.tearDown()

    fun open(bytes: ByteArray): VipsImage = delegate.open(bytes)
}

@State(Scope.Thread)
class VipsHeicCodecMatrixState {

    @Param("web-photo", "profile")
    lateinit var scenario: String

    val inputBytes: ByteArray get() = delegate.inputBytes
    val encodeOptions: VipsEncodeOptions get() = delegate.encodeOptions

    private val delegate = VipsExperimentalCodecMatrixState()

    @Setup(Level.Trial)
    fun setup(params: BenchmarkParams) {
        delegate.setup(CodecMatrixFormat.HEIC, params.direction(), scenario)
    }

    @TearDown(Level.Trial)
    fun tearDown() = delegate.tearDown()

    fun open(bytes: ByteArray): VipsImage = delegate.open(bytes)
}

internal class VipsExperimentalCodecMatrixState {

    val inputBytes: ByteArray get() = requireNotNull(selectedInput) { "experimental input is not initialized" }
    val encodeOptions = VipsEncodeOptions(quality = 85, effort = 4, lossless = false, stripMetadata = true)

    private var jpegInput: ByteArray? = null
    private var experimentalInput: ByteArray? = null
    private var selectedInput: ByteArray? = null
    private var adapter: CodecMatrixRuntimeAdapter? = null

    fun setup(
        format: CodecMatrixFormat,
        direction: CodecMatrixDirection,
        scenarioParameter: String,
    ) {
        require(format == CodecMatrixFormat.AVIF || format == CodecMatrixFormat.HEIC) {
            "experimental benchmark format must be AVIF or HEIC"
        }
        val runId = CodecMatrixRunId(requiredExperimentalProperty("codec.matrix.runId"))
        val backend = CodecMatrixBackend.parse(requiredExperimentalProperty("codec.matrix.backend"))
        val preflightPath = requiredExperimentalPath("codec.matrix.preflight")
        val fixtureManifestPath = requiredExperimentalPath("codec.matrix.fixtureManifest")
        val eligibilityPath = requiredExperimentalPath("codec.matrix.eligibility")
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
        require(eligibilityPath.fileName.toString() == "eligibility-${backend.selector}.json") {
            "eligibility path differs from the selected backend"
        }
        require(eligibilityPath.parent.fileName.toString() == runId.value) {
            "eligibility path differs from the selected run"
        }

        val capabilityCommand = capabilityCommand(runId, backend)
        require(Files.isRegularFile(eligibilityPath)) {
            "eligibility evidence is missing; run: $capabilityCommand"
        }
        val preflight = readExperimentalPreflight(preflightPath)
        require(preflight.runId == runId && preflight.requestedBackend == backend.id) {
            "preflight identity differs"
        }
        require(preflight.requestedSelector == backend.selector && preflight.status == CodecMatrixCellStatus.ELIGIBLE) {
            "preflight does not permit experimental timing"
        }
        val fixtures = readExperimentalStableFixtures(fixtureManifestPath)
        require(fixtures.runId == runId) { "fixture manifest run ID differs" }
        val scenario = scenarioParameter.toExperimentalScenario()
        val fixture = fixtures.fixtures.single { entry -> entry.scenario == scenario }
        val jpeg = fixture.inputs.single { input -> input.format == CodecMatrixFormat.JPEG }
        val loadedJpeg = readPinnedExperimentalInput(runDirectory, jpeg, fixture.derived.dimensions)
        jpegInput = loadedJpeg

        val eligibility = CodecMatrixJson.readEligibility(
            eligibilityPath,
            CodecMatrixJson.sha256(Files.readAllBytes(eligibilityPath)),
        )
        require(eligibility.runId == runId) { "eligibility run ID differs" }
        val cell = eligibility.cells.single { candidate ->
            candidate.key.backend == backend.id &&
                    candidate.key.scenario == scenario &&
                    candidate.key.format == format &&
                    candidate.key.direction == direction
        }
        require(cell.status == CodecMatrixCellStatus.ELIGIBLE) {
            "experimental codec direction is not eligible; run: $capabilityCommand"
        }

        val producerManifest = if (direction == CodecMatrixDirection.DECODE) {
            val manifestPath = runDirectory.resolve("fixtures/experimental-${backend.selector}/manifest.json")
            require(Files.isRegularFile(manifestPath)) {
                "experimental producer evidence is missing; run: $capabilityCommand"
            }
            val manifest = CodecMatrixJson.readExperimental(
                manifestPath,
                CodecMatrixJson.sha256(Files.readAllBytes(manifestPath)),
            )
            require(manifest.runId == runId && manifest.producerBackend == backend.id) {
                "experimental producer identity differs"
            }
            require(manifest.producerJdk == backend.expectedJavaMajor) { "experimental producer JDK differs" }
            require(manifest.command == "prepareExperimentalCodecMatrixFixtures") {
                "experimental producer command differs"
            }
            val target = manifest.entries.single { entry ->
                entry.scenario == scenario && entry.input.format == format
            }.input
            experimentalInput = readPinnedExperimentalInput(runDirectory, target, fixture.derived.dimensions)
            require(cell.key.inputSha256 == target.sha256) { "decode eligibility input hash differs" }
            manifest
        } else {
            require(cell.key.inputSha256 == jpeg.sha256) { "encode eligibility input hash differs" }
            null
        }

        val runtimeAdapter = CodecMatrixRuntimeAdapter.create(backend)
        producerManifest?.let { manifest ->
            require(manifest.libvipsVersion != null && manifest.libvipsVersion == runtimeAdapter.libvipsVersion) {
                "experimental producer libvips version differs"
            }
        }
        val inputForDirection = if (direction == CodecMatrixDirection.ENCODE) {
            requireNotNull(jpegInput)
        } else {
            requireNotNull(experimentalInput)
        }
        runtimeAdapter.openImage(inputForDirection).use { image ->
            require(image.width == fixture.derived.dimensions.width && image.height == fixture.derived.dimensions.height) {
                "experimental native input dimensions differ"
            }
        }
        adapter = runtimeAdapter
        selectedInput = inputForDirection
    }

    fun tearDown() {
        jpegInput = null
        experimentalInput = null
        selectedInput = null
        adapter = null
    }

    fun open(bytes: ByteArray): VipsImage =
        requireNotNull(adapter) { "experimental codec runtime is not initialized" }.openImage(bytes)
}

private fun BenchmarkParams.direction(): CodecMatrixDirection = when (benchmark.substringAfterLast('.')) {
    "encodeAvifFromJpeg", "encodeHeicFromJpeg" -> CodecMatrixDirection.ENCODE
    "decodeAvifToJpeg", "decodeHeicToJpeg" -> CodecMatrixDirection.DECODE
    else -> throw IllegalArgumentException("unknown experimental benchmark method: $benchmark")
}

private fun readExperimentalPreflight(path: Path): CodecMatrixPreflightManifest {
    requireSafeRegularFile(path, "experimental codec preflight")
    val bytes = Files.readAllBytes(path)
    return CodecMatrixJson.readPreflight(path, CodecMatrixJson.sha256(bytes))
}

private fun readExperimentalStableFixtures(path: Path): CodecMatrixFixtureManifest {
    requireSafeRegularFile(path, "experimental stable fixture manifest")
    val bytes = Files.readAllBytes(path)
    return CodecMatrixJson.readFixture(path, CodecMatrixJson.sha256(bytes))
}

private fun readPinnedExperimentalInput(
    runDirectory: Path,
    input: CodecMatrixInput,
    expectedDimensions: CodecMatrixDimensions,
): ByteArray {
    require(input.dimensions == expectedDimensions) { "experimental input dimensions differ" }
    val path = runDirectory.resolve(input.path.value).normalize()
    require(path.startsWith(runDirectory)) { "experimental input escapes the run directory" }
    requireSafeRegularFile(path, "experimental codec input")
    val bytes = Files.readAllBytes(path)
    require(bytes.size.toLong() == input.byteCount) { "experimental input byte count differs" }
    require(CodecMatrixJson.sha256(bytes) == input.sha256) { "experimental input hash differs" }
    require(codecMatrixMagic(input.format, bytes) == input.magic && input.magic.valid) {
        "experimental input magic differs"
    }
    return bytes
}

private fun String.toExperimentalScenario(): CodecMatrixScenario = when (this) {
    "web-photo" -> CodecMatrixScenario.WEB_PHOTO
    "profile" -> CodecMatrixScenario.PROFILE
    else -> throw IllegalArgumentException("unknown codec matrix scenario: $this")
}

private fun capabilityCommand(runId: CodecMatrixRunId, backend: CodecMatrixBackend): String =
    "./gradlew :bluetape4k-images-benchmark:codecMatrixCapabilityReport " +
            "-Pcodec.matrix.runId=${runId.value} -Pvips.impl=${backend.selector} --console=plain"

private fun requiredExperimentalProperty(name: String): String =
    System.getProperty(name)?.takeIf(String::isNotBlank)
        ?: throw IllegalArgumentException("required system property is missing: $name")

private fun requiredExperimentalPath(name: String): Path {
    val path = Path.of(requiredExperimentalProperty(name))
    require(path.isAbsolute && path == path.normalize()) { "$name must be absolute and normalized" }
    return path
}
