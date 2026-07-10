package io.bluetape4k.images.benchmark

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.nio.JpegWriter
import io.bluetape4k.images.immutableImageOf
import io.bluetape4k.images.suspendLoadImage
import io.bluetape4k.images.suspendWrite
import io.bluetape4k.images.vips.VipsEncodeOptions
import io.bluetape4k.images.vips.VipsImage
import io.bluetape4k.images.vips.VipsImageFormat
import io.bluetape4k.images.vips.VipsRuntime
import io.bluetape4k.okio.asSink
import io.bluetape4k.okio.asSource
import io.bluetape4k.okio.buffered
import io.bluetape4k.okio.coroutines.asSuspendedSink
import io.bluetape4k.okio.coroutines.asSuspendedSource
import io.bluetape4k.okio.coroutines.buffered as bufferedSuspended
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.Blackhole
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.benchmark.Warmup
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Param
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.InputStream
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.util.concurrent.TimeUnit
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * Measures load-transform-write pipelines for large generated image files.
 *
 * The benchmark keeps fixture generation deterministic and local to JMH setup
 * so the repository does not need to commit huge binary assets. Scores are
 * latency snapshots for complete image pipelines; lower ms/op is better.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsPrepend = ["--enable-native-access=ALL-UNNAMED"])
@State(Scope.Benchmark)
class ImageLargeStreamingBenchmark {

    companion object {
        private val JPEG_WRITER = JpegWriter(82, false)
        private val SUSPEND_JPEG_WRITER = io.bluetape4k.images.coroutines.SuspendJpegWriter(82, false)
        private val VIPS_ENCODE_OPTIONS = VipsEncodeOptions(quality = 82, effort = 4)
    }

    @Param("large-photo", "ocr-document")
    lateinit var scenario: String

    private lateinit var tempDir: Path
    private lateinit var inputPath: Path
    private lateinit var inputBytes: ByteArray
    private lateinit var config: WorkloadConfig
    private lateinit var vipsSupport: VipsLargePipelineSupport

    @Setup(Level.Trial)
    fun setup() {
        config = WorkloadConfig.of(scenario)
        tempDir = Files.createTempDirectory("bt4k-image-large-streaming-")
        try {
            inputPath = tempDir.resolve("${config.name}.jpg")
            writeJpeg(config.createImage(), inputPath, quality = 0.88f)
            inputBytes = Files.readAllBytes(inputPath)
            vipsSupport = VipsLargePipelineSupport.createRequiredFfm()
        } catch (cause: Throwable) {
            try {
                cleanupTempDir()
            } catch (cleanupFailure: Throwable) {
                cause.addSuppressed(cleanupFailure)
            }
            throw cause
        }
    }

    @TearDown(Level.Trial)
    fun tearDown() {
        if (!::tempDir.isInitialized) {
            return
        }
        cleanupTempDir()
    }

    private fun cleanupTempDir() {
        Files.walk(tempDir).use { paths ->
            paths.sorted(Comparator.reverseOrder())
                .forEach(Files::deleteIfExists)
        }
    }

    @Benchmark
    fun scrimage_byteArray_pipeline(bh: Blackhole) {
        val bytes = transform(immutableImageOf(inputBytes)).bytes(JPEG_WRITER)
        bh.consume(bytes)
    }

    @Benchmark
    fun scrimage_path_pipeline(bh: Blackhole) {
        val output = createOutput("scrimage-path")
        try {
            transform(immutableImageOf(inputPath)).forWriter(JPEG_WRITER).write(output)
            bh.consume(Files.size(output))
        } finally {
            Files.deleteIfExists(output)
        }
    }

    @Benchmark
    fun scrimage_inputStream_pipeline(bh: Blackhole) {
        val output = createOutput("scrimage-stream")
        try {
            Files.newInputStream(inputPath).use { input ->
                Files.newOutputStream(output, CREATE, WRITE, TRUNCATE_EXISTING).use { stream ->
                    transform(immutableImageOf(input)).forWriter(JPEG_WRITER).write(stream)
                }
            }
            bh.consume(Files.size(output))
        } finally {
            Files.deleteIfExists(output)
        }
    }

    @Benchmark
    fun scrimage_okioSourceSink_pipeline(bh: Blackhole) {
        val output = createOutput("scrimage-okio")
        try {
            Files.newInputStream(inputPath).asSource().buffered().use { source ->
                Files.newOutputStream(output, CREATE, WRITE, TRUNCATE_EXISTING).asSink().buffered().use { sink ->
                    transform(immutableImageOf(source)).forWriter(JPEG_WRITER).write(sink.outputStream())
                    sink.flush()
                }
            }
            bh.consume(Files.size(output))
        } finally {
            Files.deleteIfExists(output)
        }
    }

    @Benchmark
    fun scrimage_suspendedSourceSink_pipeline(bh: Blackhole) {
        val output = createOutput("scrimage-suspended")
        try {
            val size = runBlocking {
                val sourceChannel = AsynchronousFileChannel.open(inputPath, READ)
                val sinkChannel = AsynchronousFileChannel.open(output, CREATE, WRITE, TRUNCATE_EXISTING)
                val source = sourceChannel.asSuspendedSource().bufferedSuspended()
                val sink = sinkChannel.asSuspendedSink().bufferedSuspended()
                try {
                    val image = transform(suspendLoadImage(source))
                    image.suspendWrite(SUSPEND_JPEG_WRITER, sink)
                    sink.flush()
                } finally {
                    sink.close()
                    source.close()
                }
                Files.size(output)
            }
            bh.consume(size)
        } finally {
            Files.deleteIfExists(output)
        }
    }

    @Benchmark
    fun vips_byteArray_pipeline(bh: Blackhole) {
        val bytes = vipsSupport.createFromBytes(inputBytes).use { image ->
            image.resize(config.targetWidth, config.targetHeight).use { resized ->
                resized.toBytes(VipsImageFormat.JPEG, VIPS_ENCODE_OPTIONS)
            }
        }
        bh.consume(bytes)
    }

    @Benchmark
    fun vips_path_pipeline(bh: Blackhole) {
        val output = createOutput("vips-path")
        try {
            vipsSupport.createFromPath(inputPath).use { image ->
                image.resize(config.targetWidth, config.targetHeight).use { resized ->
                    resized.writeTo(output, VipsImageFormat.JPEG, VIPS_ENCODE_OPTIONS)
                }
            }
            bh.consume(Files.size(output))
        } finally {
            Files.deleteIfExists(output)
        }
    }

    @Benchmark
    fun vips_inputStream_pipeline(bh: Blackhole) {
        val output = createOutput("vips-stream")
        try {
            Files.newInputStream(inputPath).use { input ->
                vipsSupport.createFromStream(input).use { image ->
                    image.resize(config.targetWidth, config.targetHeight).use { resized ->
                        Files.newOutputStream(output, CREATE, WRITE, TRUNCATE_EXISTING).use { stream ->
                            resized.writeTo(stream, VipsImageFormat.JPEG, VIPS_ENCODE_OPTIONS)
                        }
                    }
                }
            }
            bh.consume(Files.size(output))
        } finally {
            Files.deleteIfExists(output)
        }
    }

    private fun transform(image: ImmutableImage): ImmutableImage =
        image.scaleTo(config.targetWidth, config.targetHeight)

    private fun createOutput(prefix: String): Path =
        Files.createTempFile(tempDir, "$prefix-", ".jpg")

    private class WorkloadConfig(
        val name: String,
        val targetWidth: Int,
        val targetHeight: Int,
        val createImage: () -> BufferedImage,
    ) {
        companion object {
            fun of(scenario: String): WorkloadConfig =
                when (scenario) {
                    "large-photo" -> WorkloadConfig(
                        name = "large-photo",
                        targetWidth = 1920,
                        targetHeight = 1440,
                        createImage = { createLargePhoto(4032, 3024) },
                    )
                    "ocr-document" -> WorkloadConfig(
                        name = "ocr-document",
                        targetWidth = 1240,
                        targetHeight = 1754,
                        createImage = { createOcrDocument(2480, 3508) },
                    )
                    else -> error("Unknown large streaming benchmark scenario: $scenario")
                }
        }
    }
}

private fun createLargePhoto(width: Int, height: Int): BufferedImage {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    try {
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.paint = GradientPaint(0f, 0f, Color(72, 128, 184), width.toFloat(), height.toFloat(), Color(236, 184, 112))
        graphics.fillRect(0, 0, width, height)
        for (y in 0 until height step 96) {
            for (x in 0 until width step 96) {
                val shade = (x * 31 + y * 17) and 0xFF
                graphics.color = Color((shade + 40) % 256, (shade + 96) % 256, (shade + 160) % 256, 72)
                graphics.fillOval(x - 28, y - 20, 148, 112)
            }
        }
        graphics.color = Color(255, 255, 255, 86)
        for (y in 280 until height step 420) {
            graphics.fillRoundRect(260, y, width - 520, 90, 40, 40)
        }
    } finally {
        graphics.dispose()
    }
    return image
}

private fun createOcrDocument(width: Int, height: Int): BufferedImage {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    try {
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.color = Color(250, 250, 247)
        graphics.fillRect(0, 0, width, height)
        graphics.color = Color(38, 48, 60)
        graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 86)
        graphics.drawString("OCR SAMPLE INVOICE", 180, 250)
        graphics.font = Font(Font.MONOSPACED, Font.PLAIN, 42)
        val lines = listOf(
            "Invoice No: BT4K-2026-0605",
            "Customer: bluetape4k image benchmark",
            "Workload: large document preprocessing",
            "Language hints: eng, kor, jpn",
            "Amount: 123,456.78",
        )
        lines.forEachIndexed { index, line ->
            graphics.drawString(line, 190, 380 + index * 76)
        }
        graphics.color = Color(194, 202, 214)
        for (row in 0 until 18) {
            val y = 860 + row * 118
            graphics.drawLine(180, y, width - 180, y)
            graphics.drawLine(180, y + 66, width - 180, y + 66)
            graphics.drawLine(180, y, 180, y + 66)
            graphics.drawLine(width - 180, y, width - 180, y + 66)
        }
        graphics.color = Color(62, 72, 86)
        graphics.font = Font(Font.MONOSPACED, Font.PLAIN, 34)
        for (row in 0 until 18) {
            val y = 905 + row * 118
            graphics.drawString("ITEM-${row + 1}".padEnd(12) + " image processing line item", 220, y)
            graphics.drawString("${(row + 1) * 137}.00", width - 520, y)
        }
    } finally {
        graphics.dispose()
    }
    return image
}

private fun writeJpeg(image: BufferedImage, path: Path, quality: Float) {
    val writer = ImageIO.getImageWritersByFormatName("jpeg").asSequence().first()
    try {
        val param = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = quality
        }
        Files.newOutputStream(path, CREATE, WRITE, TRUNCATE_EXISTING).use { output ->
            ImageIO.createImageOutputStream(output).use { imageOutput ->
                writer.output = imageOutput
                writer.write(null, IIOImage(image, null, null), param)
            }
        }
    } finally {
        writer.dispose()
    }
}

private class VipsLargePipelineSupport private constructor(
    private val createFromBytesFn: (ByteArray) -> VipsImage,
    private val createFromPathFn: (Path) -> VipsImage,
    private val createFromStreamFn: (InputStream) -> VipsImage,
) {
    companion object {
        private const val FFM_RUNTIME_CLASS = "io.bluetape4k.images.vips.java25.FfmVipsRuntime"
        private const val FFM_IMAGE_SUPPORT_CLASS = "io.bluetape4k.images.vips.java25.FfmVipsImageSupportKt"
        private const val PROP_VIPS_PATH = "vipsffm.libpath.vips.override"
        private const val PROP_GLIB_PATH = "vipsffm.libpath.glib.override"
        private const val PROP_GOBJECT_PATH = "vipsffm.libpath.gobject.override"
        private const val HOMEBREW_LIB = "/opt/homebrew/lib"

        fun createRequiredFfm(): VipsLargePipelineSupport {
            applyMacOsVipsLibraryPaths()
            return try {
                val runtimeKClass = Class.forName(FFM_RUNTIME_CLASS)
                val runtime = runtimeKClass.getField("INSTANCE").get(null) as VipsRuntime
                runtime.init()

                val supportKClass = Class.forName(FFM_IMAGE_SUPPORT_CLASS)
                val bytesMethod = supportKClass.getMethod("ffmVipsImageOf", ByteArray::class.java)
                val pathMethod = supportKClass.getMethod("ffmVipsImageOf", Path::class.java)
                val streamMethod = supportKClass.getMethod("ffmVipsImageOf", InputStream::class.java)
                VipsLargePipelineSupport(
                    createFromBytesFn = { bytes -> bytesMethod.invoke(null, bytes) as VipsImage },
                    createFromPathFn = { path -> pathMethod.invoke(null, path) as VipsImage },
                    createFromStreamFn = { input -> streamMethod.invoke(null, input) as VipsImage },
                )
            } catch (cause: Throwable) {
                throw IllegalStateException("Java 25 FFM libvips backend is required for this benchmark", cause)
            }
        }

        private fun applyMacOsVipsLibraryPaths() {
            val os = System.getProperty("os.name", "").lowercase()
            if (!os.contains("mac")) return

            listOf(
                PROP_VIPS_PATH to "$HOMEBREW_LIB/libvips.dylib",
                PROP_GLIB_PATH to "$HOMEBREW_LIB/libglib-2.0.dylib",
                PROP_GOBJECT_PATH to "$HOMEBREW_LIB/libgobject-2.0.dylib",
            ).forEach { (prop, path) ->
                if (System.getProperty(prop) == null && Files.exists(Path.of(path))) {
                    System.setProperty(prop, path)
                }
            }
        }
    }

    fun createFromBytes(bytes: ByteArray): VipsImage =
        createFromBytesFn(bytes)

    fun createFromPath(path: Path): VipsImage =
        createFromPathFn(path)

    fun createFromStream(input: InputStream): VipsImage =
        createFromStreamFn(input)
}
