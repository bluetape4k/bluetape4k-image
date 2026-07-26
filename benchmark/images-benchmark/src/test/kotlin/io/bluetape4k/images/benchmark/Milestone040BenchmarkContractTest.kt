package io.bluetape4k.images.benchmark

import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldBeEqualTo
import java.nio.file.Files
import org.junit.jupiter.api.Test

class Milestone040BenchmarkContractTest {

    @Test
    fun milestone040BenchmarkLanesMapToFocusedConfigurationsAndReports() {
        val root = repositoryRoot()
        val build = Files.readString(root.resolve("benchmark/images-benchmark/build.gradle.kts"))
        listOf("algorithmicHotPaths", "batchPipeline", "storageLocal", "storageS3").forEach { name ->
            build.shouldContain("register(\"$name\")")
        }
        build.shouldContain("-Pstorage.s3.enabled=true")
        build.shouldContain("ImageStorageBenchmark.s3_")
        build.shouldContain("S3 storage benchmark is opt-in")

        listOf(
            "docs/storage-backend-benchmark.md",
            "docs/batch-thumbnail-benchmark.md",
            "docs/algorithmic-hot-paths-2026-07.md",
        ).forEach { report ->
            Files.isRegularFile(root.resolve("benchmark/images-benchmark/$report")).shouldBeEqualTo(true)
        }
    }

    @Test
    fun `new benchmark classes exercise the production API boundaries`() {
        val root = repositoryRoot()
        Files.readString(root.resolve("benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/ImageAlgorithmBenchmark.kt"))
            .also { source ->
                source.shouldContain("dominantColors")
                source.shouldContain("histogramSimilarityTo")
                source.shouldContain("BatikSvgRasterizer")
                source.shouldContain("TileProcessor")
            }
        Files.readString(root.resolve("benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/ImageBatchBenchmark.kt"))
            .also { source ->
                source.shouldContain("scrimage_batchSequential")
                source.shouldContain("scrimage_batchBoundedConcurrency")
                source.shouldContain("vips_thumbnailFanout")
            }
        Files.readString(root.resolve("benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/ImageStorageBenchmark.kt"))
            .also { source ->
                source.shouldContain("LocalImageStorage")
                source.shouldContain("S3ImageStorage")
                source.shouldContain("MAX_SIZE_BYTES + 1")
            }
    }
}
