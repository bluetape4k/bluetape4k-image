package io.bluetape4k.images.benchmark

import io.bluetape4k.assertions.shouldContain
import java.nio.file.Files
import org.junit.jupiter.api.Test

class VipsJava21BenchmarkContractTest {

    private val buildScript = Files.readString(
        repositoryRoot().resolve("benchmark/images-benchmark/build.gradle.kts"),
    )
    private val stateSource = Files.readString(
        repositoryRoot().resolve(
            "benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/" +
                "VipsBenchmarkState.kt",
        ),
    )

    @Test
    fun `focused Java21 smoke pins JNI selector and report contract`() {
        buildScript.shouldContain("register(\"vipsJava21Smoke\")")
        buildScript.shouldContain("include(\".*VipsBackendBenchmark.*\")")
        buildScript.shouldContain("VIPS_JAVA21_BENCHMARK_WARMUPS")
        buildScript.shouldContain("VIPS_JAVA21_BENCHMARK_ITERATIONS")
        buildScript.shouldContain("advanced(\"jvmForks\", 1)")
        buildScript.shouldContain("tasks.register(\"verifyVipsJava21BenchmarkReport\")")
        buildScript.shouldContain("validateVipsJava21BenchmarkReport")
        buildScript.shouldContain("benchmarkVipsJava21SmokeBenchmark")
        buildScript.shouldContain("requires -Pvips.impl=java21")
        stateSource.shouldContain("vips.benchmark.required")
        stateSource.shouldContain("requires the selected native runtime")
        stateSource.shouldContain("if (nativeRuntimeRequired)")
    }

    @Test
    fun `report validator requires all geometry operations and positive scores`() {
        buildScript.shouldContain("VipsBackendBenchmark.vips_resize")
        buildScript.shouldContain("VipsBackendBenchmark.vips_thumbnail")
        buildScript.shouldContain("VipsBackendBenchmark.vips_crop")
        buildScript.shouldContain("setOf(\"1920x1080\", \"1280x720\")")
        buildScript.shouldContain("score.isFinite() && score > 0.0")
        buildScript.shouldContain("row coverage differs")
    }
}
