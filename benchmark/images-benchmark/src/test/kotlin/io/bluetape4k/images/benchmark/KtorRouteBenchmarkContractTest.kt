package io.bluetape4k.images.benchmark

import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldNotContain
import java.nio.file.Files
import org.junit.jupiter.api.Test

class KtorRouteBenchmarkContractTest {

    @Test
    fun `route benchmark uses a reusable test host and production thumbnail route`() {
        val source = Files.readString(
            repositoryRoot().resolve(
                "benchmark/images-benchmark/src/benchmark/kotlin/io/bluetape4k/images/benchmark/" +
                    "KtorThumbnailRouteBenchmark.kt"
            )
        )

        source.shouldContain("TestApplication")
        source.shouldContain("application.start()")
        source.shouldContain("application.stop()")
        source.shouldContain("bluetape4kImageThumbnailRoutes")
        source.shouldContain("multipart_parseOnly")
        source.shouldContain("image_decodeThumbnail")
        source.shouldContain("route_fullThumbnailResponse")
        source.shouldContain("route_rejectOversize")
        source.shouldContain("encodedBytes=")
        source.shouldContain("@Param(\"1\", \"5\", \"10\", \"30\")")
        source.shouldContain("Mode.SampleTime")
        source.shouldContain("route_concurrentAcceptedBatch")
        source.shouldContain("route_concurrentRejectedBatch")
        source.shouldContain("route_mixedAcceptedRejectedBatch")
        source.shouldContain("CompletableDeferred")
        source.shouldContain("startGate.complete(Unit)")
        source.shouldNotContain("embeddedServer")
    }

    @Test
    fun `Gradle exposes a focused Ktor route benchmark lane`() {
        val build = Files.readString(
            repositoryRoot().resolve("benchmark/images-benchmark/build.gradle.kts")
        )

        build.shouldContain("register(\"ktorRoute\")")
        build.shouldContain("register(\"ktorRouteConcurrency\")")
        build.shouldContain("KtorThumbnailRouteBenchmark")
        build.shouldContain("KtorThumbnailConcurrentRouteBenchmark")
        build.shouldContain("validateKtorRouteConcurrencyReport")
        build.shouldContain("expected one fresh Ktor route concurrency report")
        build.shouldContain("Ktor route concurrency row coverage differs")
        build.shouldNotContain("include(\".*KtorThumbnail.*Benchmark.*\")")
        build.shouldContain("libs.ktor.server.test.host")
    }

    @Test
    fun `report and immutable raw evidence are committed`() {
        val benchmarkRoot = repositoryRoot().resolve("benchmark/images-benchmark")

        Files.isRegularFile(
            benchmarkRoot.resolve("docs/ktor-thumbnail-route-benchmark.md")
        ).shouldBeEqualTo(true)
        Files.isRegularFile(
            benchmarkRoot.resolve(
                "docs/raw/issue-205-20260726-macos-java25/ktor-route.json"
            )
        ).shouldBeEqualTo(true)
        Files.isRegularFile(
            benchmarkRoot.resolve(
                "docs/raw/issue-205-20260726-macos-java25/ktor-route-concurrency.json"
            )
        ).shouldBeEqualTo(true)
    }
}
