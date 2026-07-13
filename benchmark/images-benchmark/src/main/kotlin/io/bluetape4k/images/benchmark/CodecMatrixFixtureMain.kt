package io.bluetape4k.images.benchmark

import java.nio.file.Path

internal data class CodecMatrixFixturePaths(
    val generatedSources: Path,
    val backendPreflight: Path,
    val runDirectory: Path,
)

internal fun parseCodecMatrixFixtureRunId(arguments: Array<String>): CodecMatrixRunId {
    require(arguments.size == 2 && arguments[0] == "--run-id") {
        "usage: CodecMatrixFixtureMain --run-id <run-id>"
    }
    return CodecMatrixRunId(arguments[1])
}

internal fun codecMatrixFixturePaths(
    workingDirectory: Path,
    runId: CodecMatrixRunId,
    backend: CodecMatrixBackend = CodecMatrixBackend.JAVA25,
): CodecMatrixFixturePaths {
    require(workingDirectory.isAbsolute) { "working directory must be absolute" }
    require(workingDirectory == workingDirectory.normalize()) { "working directory must be normalized" }
    requireSafeRegularFile(workingDirectory.resolve("settings.gradle.kts"), "repository settings")
    requireSafeRegularFile(
        workingDirectory.resolve("benchmark/images-benchmark/build.gradle.kts"),
        "benchmark module build",
    )
    val moduleBuild = workingDirectory.resolve("benchmark/images-benchmark/build")
    return CodecMatrixFixturePaths(
        generatedSources = moduleBuild.resolve("generated/codec-matrix-source-fixtures"),
        backendPreflight = moduleBuild.resolve("codec-matrix/${runId.value}/preflight-${backend.selector}.json"),
        runDirectory = moduleBuild.resolve("codec-matrix/${runId.value}"),
    )
}

internal object CodecMatrixFixtureMain {
    @JvmStatic
    fun main(arguments: Array<String>) {
        val runId = parseCodecMatrixFixtureRunId(arguments)
        val backend = CodecMatrixBackend.parse(System.getProperty("vips.impl", "java25"))
        val paths = codecMatrixFixturePaths(Path.of("").toAbsolutePath().normalize(), runId, backend)
        requireSafeRegularFile(paths.backendPreflight, "backend preflight")
        prepareCodecMatrixFixtures(
            CodecMatrixFixturePreparationRequest(
                generatedSources = paths.generatedSources,
                runDirectory = paths.runDirectory,
                runId = runId,
            ),
        )
    }
}
