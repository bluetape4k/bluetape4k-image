package io.bluetape4k.images.benchmark

import io.bluetape4k.assertions.shouldContain
import java.nio.file.Files
import org.junit.jupiter.api.Test

class VipsTransformBenchmarkContractTest {

    private val benchmarkSource = Files.readString(
        repositoryRoot().resolve(
            "benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/" +
                "VipsTransformBenchmark.kt",
        ),
    )

    private val buildScript = Files.readString(
        repositoryRoot().resolve("benchmark/images-benchmark/build.gradle.kts"),
    )

    @Test
    fun `benchmark pins chain fan out and lifecycle close paths`() {
        benchmarkSource.shouldContain("fun vips_chain")
        benchmarkSource.shouldContain("fun vips_fanOut")
        benchmarkSource.shouldContain("chainLength")
        benchmarkSource.shouldContain("fanOut")
        benchmarkSource.shouldContain("current.close()")
        benchmarkSource.shouldContain("derived.toBytes()")
    }

    @Test
    fun `build registers receipt validator and vips transform benchmark`() {
        buildScript.shouldContain("validateVipsTransformReceipt")
        buildScript.shouldContain("VipsTransformReceiptValidateMain")
        buildScript.shouldContain("register(\"vipsTransform\")")
        buildScript.shouldContain("VipsTransformBenchmark")
    }
}
