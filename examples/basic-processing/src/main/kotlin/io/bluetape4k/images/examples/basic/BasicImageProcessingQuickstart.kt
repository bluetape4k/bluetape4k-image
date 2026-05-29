package io.bluetape4k.images.examples.basic

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.images.coroutines.SuspendJpegWriter
import io.bluetape4k.images.coroutines.SuspendPngWriter
import io.bluetape4k.images.suspendLoadImage
import io.bluetape4k.images.suspendWrite
import io.bluetape4k.images.transforms.smartCropTo
import io.bluetape4k.images.withGraphics
import kotlinx.coroutines.runBlocking
import java.awt.Color
import java.awt.Font
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.name

fun main(args: Array<String>) = runBlocking {
    val outputDirectory = args.firstOrNull()?.let(::Path)
        ?: Path("build/tmp/basic-processing")
    val outputs = BasicImageProcessingQuickstart.generate(outputDirectory)

    println("Generated ${outputs.size} images under ${outputDirectory.toAbsolutePath().normalize()}")
    outputs.forEach { output ->
        println("${output.path.fileName}: ${output.width}x${output.height}, ${output.bytes} bytes")
    }
}

object BasicImageProcessingQuickstart {

    suspend fun generate(
        outputDirectory: Path = Path("build/tmp/basic-processing"),
    ): List<GeneratedImage> {
        Files.createDirectories(outputDirectory)

        val cafe = suspendLoadImage(resourcePath(CAFE_IMAGE))
        val landscape = suspendLoadImage(resourcePath(LANDSCAPE_IMAGE))
        val workbench = suspendLoadImage(resourcePath(WORKBENCH_IMAGE))

        return listOf(
            writeImage(
                image = cafe.fit(320, 240),
                output = outputDirectory.resolve("01-cafe-thumbnail.jpg"),
            ),
            writeImage(
                image = landscape.smartCropTo(640, 360),
                output = outputDirectory.resolve("02-landscape-smart-crop.jpg"),
            ),
            writeImage(
                image = cafe.fit(800, 600),
                output = outputDirectory.resolve("03-cafe-converted.png"),
            ),
            writeImage(
                image = landscape.fit(960, 540).watermark("bluetape4k-image"),
                output = outputDirectory.resolve("04-landscape-watermarked.jpg"),
            ),
            writeImage(
                image = workbench.fit(960, 540),
                output = outputDirectory.resolve("05-readme-workbench-preview.jpg"),
            ),
        )
    }

    private suspend fun writeImage(
        image: ImmutableImage,
        output: Path,
    ): GeneratedImage {
        val writer = when (output.extension.lowercase()) {
            "png" -> SuspendPngWriter.MaxCompression
            else -> SuspendJpegWriter.Default.withCompression(86).withProgressive(true)
        }
        val bytes = image.suspendWrite(writer, output)
        return GeneratedImage(
            path = output,
            width = image.width,
            height = image.height,
            bytes = bytes,
        )
    }

    private fun ImmutableImage.watermark(text: String): ImmutableImage =
        withGraphics { graphics ->
            graphics.color = Color(255, 255, 255, 180)
            graphics.font = Font(Font.SANS_SERIF, Font.BOLD, (width / 28).coerceAtLeast(24))
            val metrics = graphics.fontMetrics
            val x = width - metrics.stringWidth(text) - WATERMARK_PADDING
            val y = height - WATERMARK_PADDING
            graphics.drawString(text, x.coerceAtLeast(WATERMARK_PADDING), y)
        }

    private fun resourcePath(resourceName: String): Path {
        val resource = requireNotNull(
            BasicImageProcessingQuickstart::class.java.classLoader.getResource(resourceName)
        ) {
            "Example resource is missing: $resourceName"
        }
        require(resource.protocol == "file") {
            "Example resource must be available as a file for path-based loading: $resourceName"
        }
        return Paths.get(resource.toURI())
    }

    private const val CAFE_IMAGE = "images/cafe.jpg"
    private const val LANDSCAPE_IMAGE = "images/landscape.jpg"
    private const val WORKBENCH_IMAGE = "image-workbench.png"
    private const val WATERMARK_PADDING = 24
}

data class GeneratedImage(
    val path: Path,
    val width: Int,
    val height: Int,
    val bytes: Long,
) {
    val fileName: String = path.name
}
