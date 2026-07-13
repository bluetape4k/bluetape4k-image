package io.bluetape4k.images.benchmark

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import java.nio.file.Files
import java.nio.file.Path
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class CodecMatrixBenchmarkTaskFunctionalTest {

    @TempDir
    lateinit var tempDir: Path

    private val testBackend: String
        get() = System.getProperty("codec.matrix.testBackend", "java25")

    @Test
    fun `task listing exposes the exact codec matrix execution surface`() {
        val result = runner(
            ":bluetape4k-images-benchmark:tasks",
            "--all",
            "-Pvips.impl=$testBackend",
        ).build()

        listOf(
            "syncCodecMatrixSourceFixtures",
            "codecMatrixPreflight",
            "prepareCodecMatrixFixtures",
            "codecMatrixCapabilityReport",
            "prepareExperimentalCodecMatrixFixtures",
            "finalizeCodecMatrixEvidence",
            "stageCodecMatrixProfilerJar",
            "benchmarkCodecMatrixBenchmark",
            "benchmarkCodecMatrixAvifBenchmark",
            "benchmarkCodecMatrixHeicBenchmark",
        ).forEach(result.output::shouldContain)
    }

    @Test
    fun `invalid backend selector fails during configuration`() {
        val result = runner(
            ":bluetape4k-images-benchmark:tasks",
            "-Pvips.impl=invalid",
        ).buildAndFail()

        result.output.shouldContain("vips.impl must be java21 or java25: invalid")
    }

    @Test
    fun `test dry run remains isolated from codec execution and native capability tasks`() {
        val result = runner(
            ":bluetape4k-images-benchmark:test",
            "--dry-run",
            "-Pvips.impl=$testBackend",
        ).build()

        val forbidden = listOf(
            "syncCodecMatrixSourceFixtures",
            "codecMatrixPreflight",
            "prepareCodecMatrixFixtures",
            "codecMatrixCapabilityReport",
            "prepareExperimentalCodecMatrixFixtures",
            "finalizeCodecMatrixEvidence",
        )
        forbidden.none(result.output::contains).shouldBeEqualTo(true)
    }

    @Test
    fun `stable benchmark dry run reaches only stable preparation`() {
        val result = runner(
            ":bluetape4k-images-benchmark:benchmarkCodecMatrixBenchmark",
            "--dry-run",
            "-Pcodec.matrix.runId=issue-208-functional-stable",
            "-Pvips.impl=$testBackend",
        ).build()

        assertOrdered(
            result.output,
            "codecMatrixPreflight",
            "syncCodecMatrixSourceFixtures",
            "prepareCodecMatrixFixtures",
            "benchmarkCodecMatrixBenchmark",
        )
        listOf(
            "codecMatrixCapabilityReport",
            "prepareExperimentalCodecMatrixFixtures",
        ).none(result.output::contains).shouldBeEqualTo(true)
    }

    @Test
    fun `experimental benchmark dry run enforces capability before preparation and execution`() {
        val result = runner(
            ":bluetape4k-images-benchmark:benchmarkCodecMatrixAvifBenchmark",
            "--dry-run",
            "-Pcodec.matrix.runId=issue-208-functional-avif",
            "-Pvips.impl=$testBackend",
        ).build()

        assertOrdered(
            result.output,
            "codecMatrixPreflight",
            "prepareCodecMatrixFixtures",
            "codecMatrixCapabilityReport",
            "prepareExperimentalCodecMatrixFixtures",
            "benchmarkCodecMatrixAvifBenchmark",
        )
    }

    @Test
    fun `compile generation jar build check and test stay isolated from codec execution`() {
        val result = runner(
            ":bluetape4k-images-benchmark:build",
            ":bluetape4k-images-benchmark:check",
            ":bluetape4k-images-benchmark:test",
            ":bluetape4k-images-benchmark:benchmarkClasses",
            ":bluetape4k-images-benchmark:benchmarkBenchmarkGenerate",
            ":bluetape4k-images-benchmark:benchmarkBenchmarkCompile",
            ":bluetape4k-images-benchmark:benchmarkBenchmarkJar",
            "--dry-run",
            "-Pvips.impl=$testBackend",
        ).build()

        listOf(
            "syncCodecMatrixSourceFixtures",
            "codecMatrixPreflight",
            "prepareCodecMatrixFixtures",
            "codecMatrixCapabilityReport",
            "prepareExperimentalCodecMatrixFixtures",
            "finalizeCodecMatrixEvidence",
        ).none(result.output::contains).shouldBeEqualTo(true)
    }

    @Test
    fun `zero eligible experimental parameter skips generated execution task`() {
        val runId = "issue-208-functional-zero"
        val runDirectory = codecMatrixRunDirectory(runId)
        try {
            writeParameterFile(runId, includes = emptyList())

            val result = experimentalRunner(runId).build()

            result.output.shouldContain(":bluetape4k-images-benchmark:benchmarkCodecMatrixAvifBenchmark SKIPPED")
        } finally {
            runDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `one direction parameter and matrix properties reach generated JavaExec`() {
        val runId = "issue-208-functional-one-direction"
        val runDirectory = codecMatrixRunDirectory(runId)
        val reportDirectory = codecMatrixReportDirectory(runId)
        val parameterFile = writeParameterFile(
            runId,
            includes = listOf(".*VipsExperimentalCodecMatrixBenchmark.encodeAvifFromJpeg.*"),
        )
        writeEligibility(runId, "ELIGIBLE")
        val initScript = tempDir.resolve("probe-codec-matrix.gradle")
        Files.writeString(
            initScript,
            """
            import org.gradle.api.Action
            import org.gradle.api.tasks.StopExecutionException

            gradle.taskGraph.whenReady { graph ->
                def task = graph.allTasks.find {
                    it.path == ':bluetape4k-images-benchmark:benchmarkCodecMatrixAvifBenchmark'
                }
                if (task != null) {
                    task.actions.add(2, { ignored ->
                        println('CODEC_MATRIX_PROBE_ARGS=' + task.args.join('|'))
                        println('CODEC_MATRIX_PROBE_PROPERTIES=' + task.systemProperties.toString())
                        println('CODEC_MATRIX_PROBE_PARAMETERS=' + new File(task.args[0]).text.replace('\\n', '|'))
                        throw new StopExecutionException('functional probe completed')
                    } as Action)
                }
            }
            """.trimIndent(),
        )
        try {
            val result = experimentalRunner(runId, "--init-script", initScript.toString()).build()

            result.output.shouldContain("CODEC_MATRIX_PROBE_ARGS=${parameterFile.toAbsolutePath()}")
            result.output.shouldContain("include:.*VipsExperimentalCodecMatrixBenchmark.encodeAvifFromJpeg.*")
            result.output.contains("decodeAvifToJpeg").shouldBeEqualTo(false)
            listOf(
                "codec.matrix.backend:$testBackend",
                "codec.matrix.runId:$runId",
                "codec.matrix.preflight:${runDirectory.resolve("preflight-$testBackend.json")}",
                "codec.matrix.fixtureManifest:${runDirectory.resolve("fixtures/manifest.json")}",
                "codec.matrix.eligibility:${reportDirectory.resolve("eligibility-$testBackend.json")}",
            ).forEach(result.output::shouldContain)
        } finally {
            runDirectory.toFile().deleteRecursively()
            reportDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `blocking experimental eligibility fails before generated JavaExec`() {
        val runId = "issue-208-functional-blocking"
        val runDirectory = codecMatrixRunDirectory(runId)
        val reportDirectory = codecMatrixReportDirectory(runId)
        writeParameterFile(
            runId,
            includes = listOf(".*VipsExperimentalCodecMatrixBenchmark.encodeAvifFromJpeg.*"),
        )
        writeEligibility(runId, "FAILED_SMOKE")
        try {
            val result = experimentalRunner(runId).buildAndFail()

            result.output.shouldContain("codec matrix eligibility contains blocking status: [FAILED_SMOKE]")
        } finally {
            runDirectory.toFile().deleteRecursively()
            reportDirectory.toFile().deleteRecursively()
        }
    }

    private fun assertOrdered(output: String, vararg taskNames: String) {
        val positions = taskNames.map { taskName ->
            output.indexOf(":bluetape4k-images-benchmark:$taskName").also { position ->
                (position >= 0).shouldBeEqualTo(true)
            }
        }
        positions.zipWithNext().all { (left, right) -> left < right }.shouldBeEqualTo(true)
    }

    private fun experimentalRunner(runId: String, vararg extraArguments: String): GradleRunner = runner(
        ":bluetape4k-images-benchmark:benchmarkCodecMatrixAvifBenchmark",
        "-x",
        ":bluetape4k-images-benchmark:prepareExperimentalCodecMatrixFixtures",
        "-Pcodec.matrix.runId=$runId",
        "-Pvips.impl=$testBackend",
        *extraArguments,
    )

    private fun writeParameterFile(runId: String, includes: List<String>): Path {
        val parameterFile = codecMatrixRunDirectory(runId).resolve("staging/parameters-codecMatrixAvif.txt")
        Files.createDirectories(parameterFile.parent)
        Files.writeString(
            parameterFile,
            buildString {
                appendLine(
                    "reportFile:${codecMatrixRunDirectory(runId).resolve(
                        "staging/latency-$testBackend-codecMatrixAvif.json",
                    )}",
                )
                includes.forEach { include -> appendLine("include:$include") }
            },
        )
        return parameterFile
    }

    private fun writeEligibility(runId: String, status: String) {
        val eligibility = codecMatrixReportDirectory(runId).resolve("eligibility-$testBackend.json")
        Files.createDirectories(eligibility.parent)
        Files.writeString(eligibility, """{"cells":[{"status":"$status"}]}""")
    }

    private fun codecMatrixRunDirectory(runId: String): Path = repositoryRoot().resolve(
        "benchmark/images-benchmark/build/codec-matrix/$runId",
    )

    private fun codecMatrixReportDirectory(runId: String): Path = repositoryRoot().resolve(
        "benchmark/images-benchmark/build/reports/benchmarks/codec-matrix/$runId",
    )

    private fun runner(vararg arguments: String): GradleRunner {
        val childArguments = mutableListOf("--console=plain", *arguments)
        System.getProperty("codec.matrix.testCatalogPath")?.let { catalogPath ->
            childArguments += "-Pbluetape4kDependenciesCatalogPath=$catalogPath"
        }
        return GradleRunner.create()
            .withProjectDir(repositoryRoot().toFile())
            .withArguments(childArguments)
            .forwardOutput()
    }
}
