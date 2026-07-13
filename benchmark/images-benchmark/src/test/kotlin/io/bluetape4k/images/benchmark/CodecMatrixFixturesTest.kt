package io.bluetape4k.images.benchmark

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class CodecMatrixFixturesTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var generatedSources: Path

    @BeforeEach
    fun copyCanonicalSources() {
        tempDir = tempDir.toRealPath()
        generatedSources = Files.createDirectories(tempDir.resolve("generated"))
        Files.copy(repositoryRoot().resolve("benchmark/images-benchmark/src/main/resources/bench/cafe.jpg"), generatedSources.resolve("cafe.jpg"))
        Files.copy(repositoryRoot().resolve("images/src/test/resources/images/homer.jpg"), generatedSources.resolve("homer.jpg"))
    }

    @Test
    fun `canonical preparation is deterministic`() {
        val first = prepare("fixture-a-0001", tempDir.resolve("run-a"))
        val second = prepare("fixture-b-0001", tempDir.resolve("run-b"))

        first.fixtures.map { fixture -> fixture.inputs.map(CodecMatrixInput::sha256) }
            .shouldBeEqualTo(second.fixtures.map { fixture -> fixture.inputs.map(CodecMatrixInput::sha256) })
        first.fixtures.map { fixture -> fixture.derived.sha256 }
            .shouldBeEqualTo(second.fixtures.map { fixture -> fixture.derived.sha256 })
    }

    @Test
    fun `canonical scenarios preserve exact sources and target dimensions`() {
        val manifest = prepare("fixture-shape-0001", tempDir.resolve("run"))
        val webPhoto = manifest.fixtures.single { it.scenario == CodecMatrixScenario.WEB_PHOTO }
        val profile = manifest.fixtures.single { it.scenario == CodecMatrixScenario.PROFILE }

        webPhoto.source.path.value.shouldBeEqualTo("generated/codec-matrix-source-fixtures/cafe.jpg")
        webPhoto.source.sha256.shouldBeEqualTo(
            CodecMatrixSha256("ec07fa417de74dfb0c425bf2099ce9c4df508f111e4ab2e7f4057db2eb10205e"),
        )
        webPhoto.source.dimensions.shouldBeEqualTo(CodecMatrixDimensions(4032, 3024))
        webPhoto.derived.dimensions.shouldBeEqualTo(CodecMatrixDimensions(1920, 1080))
        profile.source.path.value.shouldBeEqualTo("generated/codec-matrix-source-fixtures/homer.jpg")
        profile.source.sha256.shouldBeEqualTo(
            CodecMatrixSha256("66a14651276f98767d9459eb6091d3b6881f0c912f01f161b41b38a5ae9577c6"),
        )
        profile.source.dimensions.shouldBeEqualTo(CodecMatrixDimensions(1248, 702))
        profile.derived.dimensions.shouldBeEqualTo(CodecMatrixDimensions(512, 512))
    }

    @Test
    fun `prepared inputs have pinned formats dimensions positive sizes and valid magic`() {
        val manifest = prepare("fixture-formats-0001", tempDir.resolve("run"))

        manifest.fixtures.forEach { fixture ->
            fixture.inputs.map(CodecMatrixInput::format)
                .shouldBeEqualTo(listOf(CodecMatrixFormat.JPEG, CodecMatrixFormat.PNG, CodecMatrixFormat.WEBP))
            fixture.inputs.forEach { input ->
                input.dimensions.shouldBeEqualTo(fixture.derived.dimensions)
                check(input.byteCount > 0)
                input.magic.valid.shouldBeEqualTo(true)
                check(Files.isRegularFile(tempDir.resolve("run").resolve(input.path.value)))
            }
        }
    }

    @Test
    fun `manifest records fixed transform recipe and codec options`() {
        val manifest = prepare("fixture-options-0001", tempDir.resolve("run"))

        manifest.recipe.shouldBeEqualTo(CodecMatrixTransformRecipe.COVER_CENTER_CROP_V1)
        manifest.options.shouldBeEqualTo(
            CodecMatrixCodecOptions(
                jpegQuality = 85,
                jpegProgressive = false,
                pngCompression = 4,
                webpLosslessLevel = -1,
                webpQuality = 85,
                webpMethod = 4,
                webpLossless = false,
                webpNoAlpha = false,
            ),
        )
    }

    @Test
    fun `existing identical fixture content is accepted but changed content is rejected`() {
        val runDirectory = tempDir.resolve("run")
        val first = prepare("fixture-existing-0001", runDirectory)
        prepare("fixture-existing-0001", runDirectory).shouldBeEqualTo(first)
        val manifestPath = runDirectory.resolve("fixtures/manifest.json")
        CodecMatrixJson.readFixture(
            manifestPath,
            CodecMatrixJson.sha256(Files.readAllBytes(manifestPath)),
        ).shouldBeEqualTo(first)

        val input = first.fixtures.first().inputs.first()
        Files.write(runDirectory.resolve(input.path.value), byteArrayOf(1, 2, 3))

        assertFailsWith<IllegalArgumentException> {
            prepare("fixture-existing-0001", runDirectory)
        }
    }

    @Test
    fun `missing non regular and symlinked generated inputs are rejected`() {
        Files.delete(generatedSources.resolve("homer.jpg"))
        assertFailsWith<IllegalArgumentException> {
            prepare("fixture-missing-0001", tempDir.resolve("missing-run"))
        }

        Files.createDirectory(generatedSources.resolve("homer.jpg"))
        assertFailsWith<IllegalArgumentException> {
            prepare("fixture-directory-0001", tempDir.resolve("directory-run"))
        }

        Files.delete(generatedSources.resolve("homer.jpg"))
        Files.createSymbolicLink(generatedSources.resolve("homer.jpg"), generatedSources.resolve("cafe.jpg"))
        assertFailsWith<IllegalArgumentException> {
            prepare("fixture-symlink-0001", tempDir.resolve("symlink-run"))
        }
    }

    @Test
    fun `symlinked source ancestor and non normalized roots are rejected`() {
        val sourceLink = tempDir.resolve("generated-link")
        Files.createSymbolicLink(sourceLink, generatedSources)
        val ancestorLink = tempDir.resolve("ancestor-link")
        Files.createSymbolicLink(ancestorLink, tempDir)

        assertFailsWith<IllegalArgumentException> {
            prepareCodecMatrixFixtures(
                CodecMatrixFixturePreparationRequest(
                    generatedSources = sourceLink,
                    runDirectory = tempDir.resolve("linked-run"),
                    runId = CodecMatrixRunId("fixture-linked-0001"),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            prepareCodecMatrixFixtures(
                CodecMatrixFixturePreparationRequest(
                    generatedSources = ancestorLink.resolve("generated"),
                    runDirectory = tempDir.resolve("ancestor-run"),
                    runId = CodecMatrixRunId("fixture-ancestor-0001"),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            prepareCodecMatrixFixtures(
                CodecMatrixFixturePreparationRequest(
                    generatedSources = generatedSources.resolve("nested/.."),
                    runDirectory = tempDir.resolve("normalized-run"),
                    runId = CodecMatrixRunId("fixture-normal-0001"),
                ),
            )
        }
    }

    @Test
    fun `CLI accepts only run id and derives paths from repository root`() {
        val runId = CodecMatrixRunId("fixture-cli-0001")
        parseCodecMatrixFixtureRunId(arrayOf("--run-id", runId.value)).shouldBeEqualTo(runId)
        codecMatrixFixturePaths(repositoryRoot(), runId, CodecMatrixBackend.JAVA21)
            .backendPreflight
            .endsWith("codec-matrix/${runId.value}/preflight-java21.json")
            .shouldBeEqualTo(true)
        assertFailsWith<IllegalArgumentException> {
            parseCodecMatrixFixtureRunId(arrayOf("--source-root", "/tmp", "--run-id", "fixture-cli-0001"))
        }
        assertFailsWith<IllegalArgumentException> {
            codecMatrixFixturePaths(tempDir, runId)
        }
    }

    private fun prepare(runId: String, runDirectory: Path): CodecMatrixFixtureManifest =
        prepareCodecMatrixFixtures(
            CodecMatrixFixturePreparationRequest(
                generatedSources = generatedSources,
                runDirectory = runDirectory,
                runId = CodecMatrixRunId(runId),
            ),
        )

    private fun repositoryRoot(): Path {
        var candidate: Path? = Path.of("").toAbsolutePath().normalize()
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle.kts"))) return candidate
            candidate = candidate.parent
        }
        error("repository root was not found from the test working directory")
    }
}
