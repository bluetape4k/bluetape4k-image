package io.bluetape4k.images.benchmark

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import java.nio.file.Files
import java.nio.file.Path
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test

class BarcodeBenchmarkTaskFunctionalTest {

    @Test
    fun `task listing exposes barcode benchmark and finalizer tasks`() {
        val result = runner(":bluetape4k-images-benchmark:tasks", "--all").build()

        listOf(
            "benchmarkBarcodeLatencyBenchmark",
            "benchmarkBarcodeThroughputBenchmark",
            "finalizeBarcodeBenchmarkEvidence",
        ).forEach(result.output::shouldContain)
    }

    @Test
    fun `invalid run id fails before evidence lookup`() {
        val result = runner(
            ":bluetape4k-images-benchmark:finalizeBarcodeBenchmarkEvidence",
            "-Pbarcode.benchmark.runId=invalid",
            "-Pbarcode.benchmark.cpu=Functional Test CPU",
        ).buildAndFail()

        result.output.shouldContain("invalid barcode benchmark run ID: invalid")
    }

    @Test
    fun `missing staged reports fail without creating accepted evidence`() {
        val runId = "issue-272-20990101-functional-missing"
        val staging = stagingDirectory(runId)
        val accepted = acceptedDirectory(runId)
        staging.toFile().deleteRecursively()
        accepted.toFile().deleteRecursively()
        try {
            val result = finalizeRunner(runId).buildAndFail()

            result.output.shouldContain("staged barcode latency report is missing")
            Files.exists(accepted).shouldBeEqualTo(false)
        } finally {
            staging.toFile().deleteRecursively()
            accepted.toFile().deleteRecursively()
        }
    }

    @Test
    fun `wrong row set mode and metric fail without creating accepted evidence`() {
        assertInvalidReport(
            runId = "issue-272-20990101-functional-rows",
            latency = report(mode = "avgt", unit = "ms/op", scenarios = listOf("qr", "code-128")),
            expectedMessage = "barcode benchmark row coverage differs",
        )
        assertInvalidReport(
            runId = "issue-272-20990101-functional-mode",
            latency = report(mode = "thrpt", unit = "ops/s"),
            expectedMessage = "barcode benchmark mode must be avgt",
        )
        assertInvalidReport(
            runId = "issue-272-20990101-functional-metric",
            latency = report(mode = "avgt", unit = "ms/op").replace("\"scoreError\":0.1", "\"scoreError\":null"),
            expectedMessage = "barcode benchmark score error is missing",
        )
    }

    private fun assertInvalidReport(runId: String, latency: String, expectedMessage: String) {
        val staging = stagingDirectory(runId)
        val accepted = acceptedDirectory(runId)
        staging.toFile().deleteRecursively()
        accepted.toFile().deleteRecursively()
        Files.createDirectories(staging)
        Files.writeString(staging.resolve("latency.json"), latency)
        Files.writeString(staging.resolve("throughput.json"), report(mode = "thrpt", unit = "ops/s"))
        try {
            val result = finalizeRunner(runId).buildAndFail()

            result.output.shouldContain(expectedMessage)
            Files.exists(accepted).shouldBeEqualTo(false)
        } finally {
            staging.toFile().deleteRecursively()
            accepted.toFile().deleteRecursively()
        }
    }

    private fun report(
        mode: String,
        unit: String,
        scenarios: List<String> = listOf("qr", "code-128", "no-result"),
    ): String = scenarios.joinToString(prefix = "[", postfix = "]", separator = ",") { scenario ->
        """
        {
          "benchmark":"io.bluetape4k.images.benchmark.ZxingBarcodeExtractionBenchmark.extractBarcodes",
          "mode":"$mode",
          "threads":1,
          "forks":1,
          "warmupIterations":3,
          "warmupTime":"1 s",
          "measurementIterations":5,
          "measurementTime":"1 s",
          "params":{"scenario":"$scenario"},
          "primaryMetric":{"score":1.0,"scoreError":0.1,"scoreUnit":"$unit"}
        }
        """.trimIndent()
    }

    private fun finalizeRunner(runId: String): GradleRunner = runner(
        ":bluetape4k-images-benchmark:finalizeBarcodeBenchmarkEvidence",
        "-Pbarcode.benchmark.runId=$runId",
        "-Pbarcode.benchmark.cpu=Functional Test CPU",
    )

    private fun stagingDirectory(runId: String): Path = repositoryRoot().resolve(
        "benchmark/images-benchmark/build/barcode-benchmark/$runId",
    )

    private fun acceptedDirectory(runId: String): Path = repositoryRoot().resolve(
        "benchmark/images-benchmark/docs/raw/$runId",
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
