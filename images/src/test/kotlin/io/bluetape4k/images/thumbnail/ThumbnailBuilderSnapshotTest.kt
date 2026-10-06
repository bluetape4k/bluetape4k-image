package io.bluetape4k.images.thumbnail

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.images.coroutines.SuspendPngWriter
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO

class ThumbnailBuilderSnapshotTest {

    companion object: KLoggingChannel()

    @Test
    fun `builder changes do not alter an existing pipeline or pending flow`(@TempDir directory: Path) = runSuspendIO {
        val source = sourceImage(directory)
        val builder = ThumbnailPipeline.builder()
            .outputDirectory(directory.resolve("first"))
            .format(ThumbnailFormat(SuspendPngWriter.MaxCompression, "png"))
            .size(5, 5, "small")
            .size(6, 6, "medium")

        val first = builder.build()
        val pending = first.process(flowOf(source))

        builder.size(7, 7, "large")
            .outputDirectory(directory.resolve("second"))

        val second = builder.build()

        val firstResults = pending.toList()
        firstResults.map { it.size.width }.sorted() shouldBeEqualTo listOf(5, 6)
        firstResults.forEach { result ->
            log.debug { "first result=$result" }
            result.status.shouldBeInstanceOf<ThumbnailStatus.Success>()
            result.output.parent shouldBeEqualTo directory.resolve("first")
            ImageIO.read(result.output.toFile()).width shouldBeEqualTo result.size.width
            ImageIO.read(result.output.toFile()).height shouldBeEqualTo result.size.height
        }

        val secondResults = second.process(flowOf(source)).toList()
        secondResults.map { it.size.width }.sorted() shouldBeEqualTo listOf(5, 6, 7)
        secondResults.forEach { result ->
            log.debug { "second result=$result" }
            result.status.shouldBeInstanceOf<ThumbnailStatus.Success>()
            result.output.parent shouldBeEqualTo directory.resolve("second")
            ImageIO.read(result.output.toFile()).width shouldBeEqualTo result.size.width
            ImageIO.read(result.output.toFile()).height shouldBeEqualTo result.size.height
        }
    }

    @Test
    fun `empty builder keeps its default size after reuse`(@TempDir directory: Path) = runSuspendIO {
        val source = sourceImage(directory)
        val builder = ThumbnailPipeline.builder()
            .outputDirectory(directory.resolve("default"))
            .format(ThumbnailFormat(SuspendPngWriter.MaxCompression, "png"))

        val defaultPipeline = builder.build()
        val customPipeline = builder.size(5, 5)
            .outputDirectory(directory.resolve("custom"))
            .build()

        val defaultResult = defaultPipeline.process(flowOf(source)).single()
        log.debug { "default result=$defaultResult" }
        defaultResult.status.shouldBeInstanceOf<ThumbnailStatus.Success>()
        ImageIO.read(defaultResult.output.toFile()).width shouldBeEqualTo 320
        ImageIO.read(defaultResult.output.toFile()).height shouldBeEqualTo 240


        val customResult = customPipeline.process(flowOf(source)).single()
        log.debug { "custom result=$customResult" }
        customResult.status.shouldBeInstanceOf<ThumbnailStatus.Success>()
        ImageIO.read(customResult.output.toFile()).width shouldBeEqualTo 5
        ImageIO.read(customResult.output.toFile()).height shouldBeEqualTo 5
    }

    private fun sourceImage(directory: Path): Path =
        directory
            .resolve("source.png")
            .also { path ->
                ImageIO.write(
                    BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB),
                    "png",
                    path.toFile()
                )
            }
}
