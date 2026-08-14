package io.bluetape4k.images.thumbnail

import com.sksamuel.scrimage.AwtImage
import com.sksamuel.scrimage.metadata.ImageMetadata
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.images.AbstractImageTest
import io.bluetape4k.images.batch.ImageBatchFailureStage
import io.bluetape4k.images.batch.ImageProcessingOptions
import io.bluetape4k.images.coroutines.SuspendImageWriter
import io.bluetape4k.images.coroutines.SuspendJpegWriter
import io.bluetape4k.junit5.tempfolder.TempFolder
import io.bluetape4k.utils.Resourcex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.Test
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.imageio.ImageIO

class ThumbnailPipelineTest: AbstractImageTest() {

    @Test
    fun `thumbnail pipeline writes configured size`(
        tempFolder: TempFolder,
    ) = runTest(timeout = 30.seconds) {
        val source = tempFolder.copyResource(LANDSCAPE_JPG, SOURCE_IMAGE_NAME)
        val outputDir = tempFolder.createDirectory(OUTPUT_DIRECTORY_NAME).toPath()
        Files.write(outputDir.resolve("source-$TEST_THUMB_SUFFIX.jpg"), byteArrayOf(9, 8, 7))
        val pipeline = ThumbnailPipeline.builder()
            .outputDirectory(outputDir)
            .size(TEST_THUMB_WIDTH, TEST_THUMB_HEIGHT, TEST_THUMB_SUFFIX)
            .options(ImageProcessingOptions(parallelism = TEST_PARALLELISM))
            .build()

        val result = pipeline.process(flowOf(source)).single()

        result.status shouldBeInstanceOf ThumbnailStatus.Success::class
        Files.exists(result.output).shouldBeTrue()
        ImageIO.read(result.output.toFile()).width shouldBeEqualTo TEST_THUMB_WIDTH
        ImageIO.read(result.output.toFile()).height shouldBeEqualTo TEST_THUMB_HEIGHT
    }

    @Test
    fun `thumbnail pipeline rejects output path traversal`(
        tempFolder: TempFolder,
    ) = runTest(timeout = 30.seconds) {
        val source = tempFolder.copyResource(LANDSCAPE_JPG, SOURCE_IMAGE_NAME)
        val outputDir = tempFolder.createDirectory(OUTPUT_DIRECTORY_NAME).toPath()
        val failures = mutableListOf<ThumbnailResult>()
        val pipeline = ThumbnailPipeline.builder()
            .outputDirectory(outputDir)
            .size(TEST_THUMB_WIDTH, TEST_THUMB_HEIGHT, TEST_THUMB_SUFFIX)
            .outputName { _, _, _ -> PATH_TRAVERSAL_OUTPUT_NAME }
            .options(ImageProcessingOptions(parallelism = TEST_PARALLELISM, skipFailures = true))
            .onFailure { failures += it }
            .build()

        val results = pipeline.process(flowOf(source)).toList()

        results.single().status shouldBeInstanceOf ThumbnailStatus.Failure::class
        failures.single().stage shouldBeEqualTo ImageBatchFailureStage.VALIDATION
        Files.exists(outputDir.parent.resolve(ESCAPED_OUTPUT_NAME)) shouldBeEqualTo false
    }

    @Test
    fun `thumbnail pipeline fails closed when dimension probe is unavailable`(
        tempFolder: TempFolder,
    ) = runTest(timeout = 30.seconds) {
        val source = tempFolder.createFile(SOURCE_IMAGE_NAME).toPath()
        Files.writeString(source, BROKEN_IMAGE_TEXT)
        val outputDir = tempFolder.createDirectory(OUTPUT_DIRECTORY_NAME).toPath()
        val failures = mutableListOf<ThumbnailResult>()
        val pipeline = ThumbnailPipeline.builder()
            .outputDirectory(outputDir)
            .size(TEST_THUMB_WIDTH, TEST_THUMB_HEIGHT, TEST_THUMB_SUFFIX)
            .options(ImageProcessingOptions(parallelism = TEST_PARALLELISM, skipFailures = true))
            .onFailure { failures += it }
            .build()

        val result = pipeline.process(flowOf(source)).single()

        result.status shouldBeInstanceOf ThumbnailStatus.Failure::class
        result.stage shouldBeEqualTo ImageBatchFailureStage.VALIDATION
        failures.single().stage shouldBeEqualTo ImageBatchFailureStage.VALIDATION
        Files.exists(outputDir.resolve("source-$TEST_THUMB_SUFFIX.jpg")).shouldBeFalse()
    }

    @Test
    fun `thumbnail pipeline preserves existing output when writer fails`(
        tempFolder: TempFolder,
    ) = runTest(timeout = 30.seconds) {
        val source = tempFolder.copyResource(LANDSCAPE_JPG, SOURCE_IMAGE_NAME)
        val outputDir = tempFolder.createDirectory(OUTPUT_DIRECTORY_NAME).toPath()
        val output = outputDir.resolve("source-$TEST_THUMB_SUFFIX.jpg")
        val existing = byteArrayOf(9, 8, 7)
        Files.write(output, existing)
        val failures = mutableListOf<ThumbnailResult>()
        val pipeline = ThumbnailPipeline.builder()
            .outputDirectory(outputDir)
            .size(TEST_THUMB_WIDTH, TEST_THUMB_HEIGHT, TEST_THUMB_SUFFIX)
            .writer(FailingImageWriter)
            .options(ImageProcessingOptions(parallelism = TEST_PARALLELISM, skipFailures = true))
            .onFailure { failures += it }
            .build()

        val result = pipeline.process(flowOf(source)).single()

        result.status shouldBeInstanceOf ThumbnailStatus.Failure::class
        result.stage shouldBeEqualTo ImageBatchFailureStage.WRITE
        failures.single().stage shouldBeEqualTo ImageBatchFailureStage.WRITE
        Files.readAllBytes(output).contentEquals(existing).shouldBeTrue()
        Files.list(outputDir).use { stream -> stream.count() shouldBeEqualTo 1L }
    }

    @Test
    fun `thumbnail pipeline preserves existing output when writer is cancelled`(
        tempFolder: TempFolder,
    ) = runTest(timeout = 30.seconds) {
        val source = tempFolder.copyResource(LANDSCAPE_JPG, SOURCE_IMAGE_NAME)
        val outputDir = tempFolder.createDirectory(OUTPUT_DIRECTORY_NAME).toPath()
        val output = outputDir.resolve("source-$TEST_THUMB_SUFFIX.jpg")
        val existing = byteArrayOf(9, 8, 7)
        Files.write(output, existing)
        val pipeline = ThumbnailPipeline.builder()
            .outputDirectory(outputDir)
            .size(TEST_THUMB_WIDTH, TEST_THUMB_HEIGHT, TEST_THUMB_SUFFIX)
            .writer(CancellingImageWriter)
            .options(ImageProcessingOptions(parallelism = TEST_PARALLELISM))
            .build()

        assertFailsWith<CancellationException> {
            pipeline.process(flowOf(source)).toList()
        }

        Files.readAllBytes(output).contentEquals(existing).shouldBeTrue()
        Files.list(outputDir).use { stream -> stream.count() shouldBeEqualTo 1L }
    }

    @Test
    fun `thumbnail format rejects blank and path separator extension`() {
        assertFailsWith<IllegalArgumentException> { ThumbnailFormat(SuspendJpegWriter.Default, BLANK_EXTENSION) }
        assertFailsWith<IllegalArgumentException> { ThumbnailFormat(SuspendJpegWriter.Default, PATH_EXTENSION) }
    }

    private fun TempFolder.copyResource(resourcePath: String, fileName: String) =
        createFile(fileName).toPath().also { target ->
            val input = Resourcex.getInputStream(resourcePath)
                ?: error("테스트 리소스를 찾을 수 없습니다: $resourcePath")
            input.use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
        }

    private companion object {
        private const val SOURCE_IMAGE_NAME = "source.jpg"
        private const val OUTPUT_DIRECTORY_NAME = "thumbs"
        private const val ESCAPED_OUTPUT_NAME = "escape.jpg"
        private const val PATH_TRAVERSAL_OUTPUT_NAME = "../$ESCAPED_OUTPUT_NAME"
        private const val BLANK_EXTENSION = " "
        private const val PATH_EXTENSION = "../jpg"
        private const val TEST_PARALLELISM = 1
        private const val TEST_THUMB_WIDTH = 80
        private const val TEST_THUMB_HEIGHT = 60
        private const val TEST_THUMB_SUFFIX = "small"
        private const val BROKEN_IMAGE_TEXT = "not an image"
    }

    private object FailingImageWriter : SuspendImageWriter {
        override fun write(image: AwtImage, metadata: ImageMetadata, out: OutputStream) {
            out.write(byteArrayOf(0x00, 0x01, 0x02))
            throw IOException("fixture writer failure")
        }
    }

    private object CancellingImageWriter : SuspendImageWriter {
        override fun write(image: AwtImage, metadata: ImageMetadata, out: OutputStream) {
            out.write(byteArrayOf(0x00, 0x01, 0x02))
            throw CancellationException("fixture cancellation")
        }
    }
}
