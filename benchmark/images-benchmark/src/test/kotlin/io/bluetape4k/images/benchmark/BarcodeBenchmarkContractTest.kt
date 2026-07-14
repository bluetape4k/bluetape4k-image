package io.bluetape4k.images.benchmark

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import java.nio.file.Files
import org.junit.jupiter.api.Test

class BarcodeBenchmarkContractTest {

    private val benchmarkSourcePath = repositoryRoot().resolve(
        "benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/" +
            "ZxingBarcodeExtractionBenchmark.kt",
    )
    private val buildScriptPath = repositoryRoot().resolve("benchmark/images-benchmark/build.gradle.kts")

    @Test
    fun `barcode benchmark isolates setup and measures provider extraction only`() {
        Files.isRegularFile(benchmarkSourcePath).shouldBeEqualTo(true)
        val source = Files.readString(benchmarkSourcePath)

        source.shouldContain("@Param(\"qr\", \"code-128\", \"no-result\")")
        source.shouldContain("@Setup(Level.Trial)")
        source.shouldContain("fun extractBarcodes()")
        source.shouldContain("reader.readBarcodes(image, options)")
        source.substringAfter("@Benchmark").substringBeforeLast('}')
            .contains("immutableImageOf")
            .shouldBeEqualTo(false)
        source.contains("com.google.zxing").shouldBeEqualTo(false)
    }

    @Test
    fun `latency and throughput configurations share class and fixed execution contract`() {
        val build = Files.readString(buildScriptPath)

        listOf("barcodeLatency", "barcodeThroughput").forEach { name ->
            build.shouldContain("register(\"$name\")")
        }
        build.shouldContain("include(\".*ZxingBarcodeExtractionBenchmark.*\")")
        build.shouldContain("warmups = BARCODE_BENCHMARK_WARMUPS")
        build.shouldContain("iterations = BARCODE_BENCHMARK_ITERATIONS")
        build.shouldContain("iterationTime = BARCODE_BENCHMARK_ITERATION_SECONDS")
        build.shouldContain(
            "add(\"benchmarkImplementation\", project(\":bluetape4k-images-barcode-zxing\"))",
        )
        build.contains("implementation(project(\":bluetape4k-images-barcode-zxing\"))")
            .shouldBeEqualTo(false)

        configuration(build, "barcodeLatency").also { configuration ->
            configuration.shouldContain("mode = \"avgt\"")
            configuration.shouldContain("outputTimeUnit = \"ms\"")
            configuration.shouldContain("advanced(\"jvmForks\", 1)")
        }
        configuration(build, "barcodeThroughput").also { configuration ->
            configuration.shouldContain("mode = \"thrpt\"")
            configuration.shouldContain("outputTimeUnit = \"s\"")
            configuration.shouldContain("advanced(\"jvmForks\", 1)")
        }
    }

    private fun configuration(build: String, name: String): String {
        val start = build.indexOf("register(\"$name\")")
        require(start >= 0) { "benchmark configuration not found: $name" }
        val end = build.indexOf("\n        register(\"", start + 1).takeIf { it >= 0 }
            ?: build.indexOf("\n    targets", start)
        return build.substring(start, end)
    }
}
