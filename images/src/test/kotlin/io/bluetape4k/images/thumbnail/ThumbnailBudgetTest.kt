package io.bluetape4k.images.thumbnail

import com.sksamuel.scrimage.AwtImage
import com.sksamuel.scrimage.metadata.ImageMetadata
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.images.batch.ImageBatchException
import io.bluetape4k.images.batch.ImageBatchFailureStage
import io.bluetape4k.images.batch.ImageProcessingOptions
import io.bluetape4k.images.batch.probeImagePixelCount
import io.bluetape4k.images.coroutines.SuspendPngWriter
import io.bluetape4k.images.coroutines.SuspendImageWriter
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.seconds

class ThumbnailBudgetTest {

    @Test
    fun `fit and smart crop reject excessive output before writing`(@TempDir directory: Path) = runTest {
        val source = sourceImage(directory)
        for ((index, crop) in listOf(ThumbnailCrop.Fit, ThumbnailCrop.Smart()).withIndex()) {
            val output = directory.resolve("output-$index")
            val pipeline = ThumbnailPipeline.builder()
                .outputDirectory(output)
                .size(100, 100)
                .crop(crop)
                .format(ThumbnailFormat(SuspendPngWriter.MaxCompression, "png"))
                .options(ImageProcessingOptions(maxPixels = 25, maxInFlightPixels = 25))
                .build()

            val failure = assertFailsWith<ImageBatchException> {
                pipeline.process(flowOf(source)).single()
            }
            failure.stage shouldBeEqualTo ImageBatchFailureStage.VALIDATION
            Files.exists(output).shouldBeFalse()
        }
    }

    @Test
    fun `decoded input cannot exceed the reserved probe budget`(@TempDir directory: Path) = runTest {
        val source = sourceImage(directory)
        val output = directory.resolve("changed-input")
        mockkStatic("io.bluetape4k.images.batch.ImageDimensionProbeKt")
        try {
            every { probeImagePixelCount(source) } returns 24L
            val pipeline = ThumbnailPipeline.builder()
                .outputDirectory(output)
                .size(5, 5)
                .options(ImageProcessingOptions(maxPixels = 25, maxInFlightPixels = 50))
                .build()

            val failure = assertFailsWith<ImageBatchException> {
                pipeline.process(flowOf(source)).single()
            }
            failure.stage shouldBeEqualTo ImageBatchFailureStage.VALIDATION
            Files.exists(output).shouldBeFalse()
        } finally {
            unmockkStatic("io.bluetape4k.images.batch.ImageDimensionProbeKt")
        }
    }

    @Test
    fun `combined input and output must fit the in flight budget`(@TempDir directory: Path) = runTest {
        val source = sourceImage(directory)
        val pipeline = ThumbnailPipeline.builder()
            .outputDirectory(directory.resolve("combined"))
            .size(5, 5)
            .format(ThumbnailFormat(SuspendPngWriter.MaxCompression, "png"))
            .options(ImageProcessingOptions(maxPixels = 25, maxInFlightPixels = 49))
            .build()

        val failure = assertFailsWith<ImageBatchException> {
            pipeline.process(flowOf(source)).single()
        }
        failure.stage shouldBeEqualTo ImageBatchFailureStage.VALIDATION
    }

    @Test
    fun `skip failures continues to an exact budget thumbnail`(@TempDir directory: Path) = runTest {
        val source = sourceImage(directory)
        val observedFailures = mutableListOf<ThumbnailResult>()
        val pipeline = ThumbnailPipeline.builder()
            .outputDirectory(directory.resolve("mixed"))
            .size(100, 100, "large")
            .size(5, 5, "exact")
            .format(ThumbnailFormat(SuspendPngWriter.MaxCompression, "png"))
            .options(ImageProcessingOptions(maxPixels = 25, maxInFlightPixels = 50, skipFailures = true))
            .onFailure { observedFailures += it }
            .build()

        val results = pipeline.process(flowOf(source)).toList()
        results.single { it.size.suffix == "large" }.stage shouldBeEqualTo ImageBatchFailureStage.VALIDATION
        results.single { it.size.suffix == "exact" }.status shouldBeInstanceOf ThumbnailStatus.Success::class
        observedFailures.size shouldBeEqualTo 1
    }

    @Test
    fun `two sizes cannot hold input and output beyond the combined budget`(@TempDir directory: Path) =
        runTest(timeout = 10.seconds) {
            val secondEntered = CountDownLatch(1)
            val writes = AtomicInteger()
            val active = AtomicInteger()
            val maximum = AtomicInteger()
            val writer = object : SuspendImageWriter {
                override fun write(image: AwtImage, metadata: ImageMetadata, out: OutputStream) {
                    val count = active.incrementAndGet()
                    maximum.accumulateAndGet(count, ::maxOf)
                    try {
                        if (writes.incrementAndGet() == 1) {
                            // 첫 writer를 유지해 입력만 예약하는 회귀에서 두 번째 writer의 진입을 관측합니다.
                            secondEntered.await(1, TimeUnit.SECONDS)
                        } else {
                            secondEntered.countDown()
                        }
                        SuspendPngWriter.MaxCompression.write(image, metadata, out)
                    } finally {
                        active.decrementAndGet()
                    }
                }
            }

            val results = twoSizes(directory, writer).build().process(flowOf(sourceImage(directory))).toList()

            results.size shouldBeEqualTo 2
            results.all { it.status is ThumbnailStatus.Success }.shouldBeTrue()
            maximum.get() shouldBeEqualTo 1
        }

    @Test
    fun `write failure returns combined permits to the next size`(
        @TempDir directory: Path,
    ) = runTest(timeout = 10.seconds) {
        val writes = AtomicInteger()
        val writer = object : SuspendImageWriter {
            override fun write(image: AwtImage, metadata: ImageMetadata, out: OutputStream) {
                if (writes.incrementAndGet() == 1) throw IOException("first write fails")
                SuspendPngWriter.MaxCompression.write(image, metadata, out)
            }
        }
        val pipeline = twoSizes(directory, writer).build()

        val results = pipeline.process(flowOf(sourceImage(directory))).toList()

        writes.get() shouldBeEqualTo 2
        results.count { it.status is ThumbnailStatus.Success } shouldBeEqualTo 1
        results.single { it.status is ThumbnailStatus.Failure }.stage shouldBeEqualTo ImageBatchFailureStage.WRITE
    }

    @Test
    fun `cancellation while holding combined permits preserves cancellation`(@TempDir directory: Path) =
        runTest(timeout = 10.seconds) {
            // 동기 writer 내부의 정확한 취소 지점을 고정하므로 반복 실행형 stress tester 대신 신호를 사용합니다.
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val writes = AtomicInteger()
            val failures = AtomicInteger()
            val writer = object : SuspendImageWriter {
                override fun write(image: AwtImage, metadata: ImageMetadata, out: OutputStream) {
                    writes.incrementAndGet()
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS)) { "writer was not released" }
                    SuspendPngWriter.MaxCompression.write(image, metadata, out)
                }
            }
            val pipeline = twoSizes(directory, writer).onFailure { failures.incrementAndGet() }.build()
            val job = launch { pipeline.process(flowOf(sourceImage(directory))).toList() }
            try {
                withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) }.shouldBeTrue()
                job.cancel()
            } finally {
                release.countDown()
            }
            job.join()

            job.isCancelled.shouldBeTrue()
            writes.get() shouldBeEqualTo 1
            failures.get() shouldBeEqualTo 0
            Files.list(directory.resolve("two-sizes")).use { it.count() shouldBeEqualTo 0L }
        }

    private fun twoSizes(directory: Path, writer: SuspendImageWriter): ThumbnailPipeline.Builder =
        ThumbnailPipeline.builder()
            .outputDirectory(directory.resolve("two-sizes"))
            .size(5, 5, "first")
            .size(5, 5, "second")
            .format(ThumbnailFormat(writer, "png"))
            .options(ImageProcessingOptions(
                parallelism = 2,
                maxPixels = 25,
                maxInFlightPixels = 50,
                skipFailures = true,
            ))

    private fun sourceImage(directory: Path): Path = directory.resolve("source.png").also {
        ImageIO.write(BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB), "png", it.toFile())
    }
}
