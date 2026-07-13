package io.bluetape4k.images.benchmark

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class CodecMatrixBenchmarkContractTest {

    @TempDir
    lateinit var tempDir: Path

    private val buildScript = Files.readString(
        repositoryRoot().resolve("benchmark/images-benchmark/build.gradle.kts"),
    )
    private val stableBenchmarkSource = repositoryRoot()
        .resolve("benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/VipsCodecMatrixBenchmark.kt")
        .let { path -> if (Files.isRegularFile(path)) Files.readString(path) else "" }
    private val experimentalBenchmarkSource = repositoryRoot()
        .resolve(
            "benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/" +
                    "VipsExperimentalCodecMatrixBenchmark.kt",
        )
        .let { path -> if (Files.isRegularFile(path)) Files.readString(path) else "" }

    @Test
    fun `backend selector and focused benchmark configurations are exact`() {
        buildScript.shouldContain("providers.gradleProperty(\"vips.impl\").orElse(\"java25\").get()")
        buildScript.shouldContain("vipsImpl == \"java21\" || vipsImpl == \"java25\"")
        buildScript.shouldContain("vips.impl must be java21 or java25")
        buildScript.shouldContain("named(\"main\")")
        buildScript.shouldContain("exclude(\".*VipsExperimentalCodecMatrixBenchmark.*\")")
        listOf("codecMatrix", "codecMatrixAvif", "codecMatrixHeic").forEach { configuration ->
            buildScript.shouldContain("register(\"$configuration\")")
        }
        buildScript.shouldContain("include(\".*VipsCodecMatrixBenchmark.*\")")
        buildScript.shouldContain("include(\".*VipsExperimentalCodecMatrixBenchmark.encodeAvifFromJpeg.*\")")
        buildScript.shouldContain("include(\".*VipsExperimentalCodecMatrixBenchmark.decodeAvifToJpeg.*\")")
        buildScript.shouldContain("include(\".*VipsExperimentalCodecMatrixBenchmark.encodeHeicFromJpeg.*\")")
        buildScript.shouldContain("include(\".*VipsExperimentalCodecMatrixBenchmark.decodeHeicToJpeg.*\")")
    }

    @Test
    fun `codec matrix execution tasks expose the approved entrypoints and dependencies`() {
        listOf(
            "syncCodecMatrixSourceFixtures",
            "codecMatrixPreflight",
            "prepareCodecMatrixFixtures",
            "codecMatrixCapabilityReport",
            "prepareExperimentalCodecMatrixFixtures",
            "finalizeCodecMatrixEvidence",
            "stageCodecMatrixProfilerJar",
        ).forEach { taskName -> buildScript.shouldContain("\"$taskName\"") }
        listOf(
            "CodecMatrixPreflightMain",
            "CodecMatrixFixtureMain",
            "CodecMatrixCapabilityMain",
            "CodecMatrixExperimentalFixtureMain",
            "CodecMatrixFinalizeMain",
        ).forEach(buildScript::shouldContain)
        buildScript.shouldContain("testImplementation(gradleTestKit())")
        buildScript.shouldContain("dependsOn(codecMatrixPreflight, syncCodecMatrixSourceFixtures)")
        buildScript.shouldContain("dependsOn(codecMatrixCapabilityReport)")
        buildScript.shouldContain("dependsOn(prepareExperimentalCodecMatrixFixtures)")
        buildScript.shouldContain("onlyIf(\"at least one experimental codec direction is eligible\")")
        buildScript.shouldContain("setArgs(listOf(parameterFileValue.absolutePath))")
        buildScript.shouldContain("requireNoBlockingCodecMatrixEligibility(eligibilityFile)")
        buildScript.shouldContain("task.name in codecMatrixExecutionTaskNames")
        listOf(
            "codec.matrix.backend",
            "codec.matrix.runId",
            "codec.matrix.preflight",
            "codec.matrix.fixtureManifest",
            "codec.matrix.eligibility",
        ).forEach(buildScript::shouldContain)
        buildScript.shouldContain("javaLauncher.set(selectedJavaLauncher)")
        buildScript.shouldContain("classpath = sourceSets.main.get().runtimeClasspath")
        buildScript.shouldContain("classpath = sourceSets.named(\"benchmark\").get().runtimeClasspath")
        buildScript.shouldContain("systemProperty(\"vips.impl\", vipsImpl)")
        buildScript.shouldContain("if (vipsImpl == \"java25\")")
        buildScript.shouldContain("jvmArgs(\"--enable-native-access=ALL-UNNAMED\")")
        buildScript.shouldContain(
            "tasks.withType<JavaExec>().matching { task -> task.name in codecMatrixNativeTaskNames }.configureEach",
        )
        buildScript.contains("tasks.withType<JavaExec>().configureEach").shouldBeEqualTo(false)
        buildScript.shouldContain("environment(\"DYLD_LIBRARY_PATH\", homebrewVipsLibraryDirectory.absolutePath)")
        buildScript.shouldContain("codecMatrixSupersedes.orNull?.let")
        buildScript.shouldContain("listOf(\"--supersedes\", it)")
        buildScript.shouldContain("codecMatrixReplacesFailedAttempt.orNull?.let")
        buildScript.shouldContain("listOf(\"--replaces-failed-attempt\", it)")
    }

    @Test
    fun `profiler jar staging verifies exact archive freshness classes and hash`() {
        buildScript.shouldContain("tasks.named<Jar>(\"benchmarkBenchmarkJar\")")
        buildScript.shouldContain("benchmarkJar.flatMap(Jar::getArchiveFile)")
        buildScript.shouldContain("tasks.withType<Jar>().configureEach")
        buildScript.shouldContain("inputs.file(layout.projectDirectory.file(\"build.gradle.kts\"))")
        buildScript.shouldContain("exclude(\"META-INF/*.SF\", \"META-INF/*.RSA\", \"META-INF/*.DSA\", \"META-INF/*.EC\")")
        buildScript.shouldContain("generated codec matrix profiler jar is stale")
        buildScript.shouldContain("VipsCodecMatrixBenchmark")
        buildScript.shouldContain("VipsExperimentalCodecMatrixBenchmark")
        buildScript.shouldContain("io/bluetape4k/images/benchmark/jmh_generated/")
        buildScript.shouldContain("_jmhTest.class")
        buildScript.shouldContain("codecMatrixSha256(targetJar)")
        buildScript.shouldContain("copyCodecMatrixInputImmutable(archive, targetJar)")
    }

    @Test
    fun `stable codec matrix pins protocol fixtures options and strict native execution`() {
        listOf(
            "class VipsCodecMatrixBenchmark",
            "class VipsCodecMatrixState",
            "fun encodePngFromJpeg",
            "fun decodePngToJpeg",
            "fun encodeWebpFromJpeg",
            "fun decodeWebpToJpeg",
            "@Threads(1)",
            "@Fork(1)",
            "@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)",
            "@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)",
            "@BenchmarkMode(Mode.AverageTime)",
            "@OutputTimeUnit(TimeUnit.MILLISECONDS)",
            "@State(Scope.Thread)",
            "@Param(\"web-photo\", \"profile\")",
            "VipsEncodeOptions(quality = 85, effort = 4, lossless = false, stripMetadata = true)",
            "CodecMatrixJson.readPreflight",
            "CodecMatrixJson.readFixture",
            "CodecMatrixRuntimeAdapter.create",
            "VipsImageFormat.PNG",
            "VipsImageFormat.WEBP",
            "VipsImageFormat.JPEG",
            "bh.consume(image.toBytes",
        ).forEach(stableBenchmarkSource::shouldContain)
        stableBenchmarkSource.windowed("@Benchmark\n".length).count { it == "@Benchmark\n" }
            .shouldBeEqualTo(4)
        stableBenchmarkSource.contains("vipsAvailable").shouldBeEqualTo(false)
        stableBenchmarkSource.contains("bh.consume(null)").shouldBeEqualTo(false)
        stableBenchmarkSource.contains("shutdown(").shouldBeEqualTo(false)
        stableBenchmarkSource.contains("catch (").shouldBeEqualTo(false)
    }

    @Test
    fun `experimental codec lanes gate each invoked direction and pinned input`() {
        listOf(
            "class VipsExperimentalCodecMatrixBenchmark",
            "class VipsAvifCodecMatrixState",
            "class VipsHeicCodecMatrixState",
            "class VipsExperimentalCodecMatrixState",
            "fun encodeAvifFromJpeg(state: VipsAvifCodecMatrixState",
            "fun decodeAvifToJpeg(state: VipsAvifCodecMatrixState",
            "fun encodeHeicFromJpeg(state: VipsHeicCodecMatrixState",
            "fun decodeHeicToJpeg(state: VipsHeicCodecMatrixState",
            "@OptIn(VipsIncubatingApi::class)",
            "BenchmarkParams",
            "CodecMatrixDirection.ENCODE",
            "CodecMatrixDirection.DECODE",
            "CodecMatrixCellStatus.ELIGIBLE",
            "CodecMatrixJson.readEligibility",
            "CodecMatrixJson.readExperimental",
            "codecMatrixCapabilityReport",
            "jpegInput",
            "experimentalInput",
            "VipsImageFormat.AVIF",
            "VipsImageFormat.HEIC",
            "VipsImageFormat.JPEG",
            "bh.consume(image.toBytes",
            "@Threads(1)",
            "@Fork(1)",
            "@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)",
            "@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)",
        ).forEach(experimentalBenchmarkSource::shouldContain)
        experimentalBenchmarkSource.windowed("@Benchmark\n".length).count { it == "@Benchmark\n" }
            .shouldBeEqualTo(4)
        experimentalBenchmarkSource.contains("vipsAvailable").shouldBeEqualTo(false)
        experimentalBenchmarkSource.contains("bh.consume(null)").shouldBeEqualTo(false)
        experimentalBenchmarkSource.contains("catch (").shouldBeEqualTo(false)
        experimentalBenchmarkSource.contains("?: return").shouldBeEqualTo(false)
    }

    @Test
    fun `focused protocol stays pinned to one warmup three measurements and one fork`() {
        count("warmups = CODEC_MATRIX_WARMUPS").shouldBeEqualTo(3)
        count("iterations = CODEC_MATRIX_ITERATIONS").shouldBeEqualTo(3)
        count("iterationTime = CODEC_MATRIX_ITERATION_SECONDS").shouldBeEqualTo(3)
        count("advanced(\"jvmForks\", 1)").shouldBeEqualTo(3)
        listOf("codecMatrix", "codecMatrixAvif", "codecMatrixHeic").forEach { name ->
            configuration(name).shouldContain("mode = \"avgt\"")
            configuration(name).shouldContain("outputTimeUnit = \"ms\"")
            configuration(name).shouldContain("reportFormat = \"json\"")
        }
    }

    @Test
    fun `experimental parameter renderer includes only fully eligible directions`() {
        val eligibility = eligibility(
            encodeStatus = CodecMatrixCellStatus.ELIGIBLE,
            decodeStatus = CodecMatrixCellStatus.UNSUPPORTED,
        )

        val rendered = renderCodecMatrixBenchmarkParameters(
            format = CodecMatrixFormat.AVIF,
            eligibility = eligibility,
            reportFile = tempDir.resolve("avif.json"),
        )

        rendered.shouldContain("configurationName:codecMatrixAvif")
        rendered.shouldContain("include:.*VipsExperimentalCodecMatrixBenchmark.encodeAvifFromJpeg.*")
        rendered.contains("decodeAvifToJpeg").shouldBeEqualTo(false)
        rendered.shouldContain("warmups:1")
        rendered.shouldContain("iterations:3")
        rendered.shouldContain("advanced:jvmForks=1")
    }

    @Test
    fun `experimental parameter renderer emits no includes for terminal unmeasured format`() {
        val rendered = renderCodecMatrixBenchmarkParameters(
            format = CodecMatrixFormat.HEIC,
            eligibility = eligibility(
                format = CodecMatrixFormat.HEIC,
                encodeStatus = CodecMatrixCellStatus.SKIPPED,
                decodeStatus = CodecMatrixCellStatus.UNSUPPORTED,
            ),
            reportFile = tempDir.resolve("heic.json"),
        )

        rendered.lineSequence().none { it.startsWith("include:") }.shouldBeEqualTo(true)
    }

    @Test
    fun `experimental parameter renderer rejects blocking and partial scenario coverage`() {
        assertFailsWith<IllegalArgumentException> {
            renderCodecMatrixBenchmarkParameters(
                CodecMatrixFormat.AVIF,
                eligibility(CodecMatrixFormat.AVIF, CodecMatrixCellStatus.FAILED_SMOKE, CodecMatrixCellStatus.UNSUPPORTED),
                tempDir.resolve("blocking.json"),
            )
        }
        val partial = eligibility(
            format = CodecMatrixFormat.AVIF,
            encodeStatus = CodecMatrixCellStatus.ELIGIBLE,
            decodeStatus = CodecMatrixCellStatus.UNSUPPORTED,
        ).copy(
            cells = eligibility(
                format = CodecMatrixFormat.AVIF,
                encodeStatus = CodecMatrixCellStatus.ELIGIBLE,
                decodeStatus = CodecMatrixCellStatus.UNSUPPORTED,
            ).cells.map { cell ->
                if (cell.key.scenario == CodecMatrixScenario.PROFILE &&
                    cell.key.direction == CodecMatrixDirection.ENCODE
                ) {
                    terminalCell(cell.key, CodecMatrixCellStatus.SKIPPED)
                } else {
                    cell
                }
            },
        )
        assertFailsWith<IllegalArgumentException> {
            renderCodecMatrixBenchmarkParameters(
                CodecMatrixFormat.AVIF,
                partial,
                tempDir.resolve("partial.json"),
            )
        }
    }

    private fun count(value: String): Int = buildScript.windowed(value.length).count { it == value }

    private fun configuration(name: String): String {
        val start = buildScript.indexOf("register(\"$name\")")
        require(start >= 0) { "benchmark configuration not found: $name" }
        val end = buildScript.indexOf("\n        register(\"", start + 1).takeIf { it >= 0 }
            ?: buildScript.indexOf("\n    targets", start)
        return buildScript.substring(start, end)
    }

    private fun eligibility(
        format: CodecMatrixFormat = CodecMatrixFormat.AVIF,
        encodeStatus: CodecMatrixCellStatus,
        decodeStatus: CodecMatrixCellStatus,
    ): CodecMatrixEligibilityManifest {
        val cells = CodecMatrixScenario.entries.flatMap { scenario ->
            CodecMatrixDirection.entries.map { direction ->
                val key = CodecMatrixCellKey(
                    backend = CodecMatrixBackendId.JAVA25,
                    scenario = scenario,
                    format = format,
                    direction = direction,
                    inputSha256 = CodecMatrixSha256("a".repeat(64)),
                )
                val status = if (direction == CodecMatrixDirection.ENCODE) encodeStatus else decodeStatus
                if (status == CodecMatrixCellStatus.ELIGIBLE) {
                    CodecMatrixCell(key, status)
                } else {
                    terminalCell(key, status)
                }
            }
        }
        return CodecMatrixEligibilityManifest(
            runId = CodecMatrixRunId("parameter-render-0001"),
            expectedCellCount = cells.size,
            cells = cells,
        )
    }

    private fun terminalCell(key: CodecMatrixCellKey, status: CodecMatrixCellStatus) = CodecMatrixCell(
        key = key,
        status = status,
        reasonCode = when (status) {
            CodecMatrixCellStatus.FAILED_SMOKE -> CodecMatrixReasonCode.SMOKE_FAILED
            CodecMatrixCellStatus.UNSUPPORTED -> CodecMatrixReasonCode.CAPABILITY_UNAVAILABLE
            else -> CodecMatrixReasonCode.CAPABILITY_UNKNOWN
        },
        reason = "codec direction is unavailable",
        rerunGuidance = "rerun capability smoke",
    )
}

internal fun repositoryRoot(): Path {
    var current = Path.of("").toAbsolutePath().normalize()
    while (!Files.isRegularFile(current.resolve("settings.gradle.kts"))) {
        current = requireNotNull(current.parent) { "repository root not found" }
    }
    return current
}
