package io.bluetape4k.images.benchmark

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import java.nio.file.Files
import org.junit.jupiter.api.Test

class OcrBenchmarkContractTest {

    private val sourcePath = repositoryRoot().resolve(
        "benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/" +
            "TesseractOcrExtractionBenchmark.kt",
    )
    private val buildScriptPath = repositoryRoot().resolve("benchmark/images-benchmark/build.gradle.kts")

    @Test
    fun `OCR benchmark isolates fixture setup and has explicit preprocessing`() {
        val source = Files.readString(sourcePath)

        source.shouldContain("@Param(\"clean-text-v2-001\")")
        source.shouldContain("lateinit var fixtureId: String")
        source.shouldContain("@Setup(Level.Trial)")
        source.shouldContain("OcrBenchmarkEnvironment.requireLanguages")
        source.shouldContain("OcrBenchmarkCorpusV2.loadFixture(fixtureId)")
        source.shouldContain("fixture.verifyOutput")
        source.shouldContain("fun extractText(blackhole: Blackhole)")
        source.shouldContain("fun preprocessAndExtract(blackhole: Blackhole)")
        source.shouldContain("normalizeRightRotation(image)")
        source.shouldContain("private fun normalizeRightRotation")
        source.shouldContain("BufferedImage.TYPE_INT_RGB")
        source.shouldContain("GrayscaleFilter()")
        source.contains("OcrBenchmarkFixtures.load").shouldBeEqualTo(false)
        source.shouldContain("OcrBenchmarkCorpusScenario.ROTATED")
        OcrBenchmarkCorpusV2
            .loadManifest()
            .fixtures
            .filter { it.expectedOutcome != OcrBenchmarkExpectedOutcome.ERROR }
            .forEach { fixture -> source.shouldContain(fixture.fixtureId) }
    }

    @Test
    fun `OCR latency and throughput tasks use one isolated host-native class`() {
        val build = Files.readString(buildScriptPath)

        listOf("ocrLatency", "ocrThroughput").forEach { name ->
            build.shouldContain("register(\"$name\")")
        }
        build.shouldContain("include(\".*TesseractOcrExtractionBenchmark.*\")")
        build.shouldContain("add(\"benchmarkImplementation\", project(\":bluetape4k-images-ocr\"))")
        build.shouldContain("bench/ocr-v2/manifest.json")
        build.shouldContain("expectedOcrBenchmarkFixtureIds")
        build.shouldContain("params[\"fixtureId\"]")
        build.shouldContain("validateOcrBenchmarkReport")
        build.shouldContain("benchmarkOcrThroughputBenchmark")
    }
}
