package io.bluetape4k.images.benchmark

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.nio.ImmutableImageLoader
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.logging.warn
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path

/**
 * Shared image set loader for benchmark fixtures.
 *
 * Prefer checked-in image fixtures under `images/src/test/resources/images` so
 * allocation and throughput evidence reflects real compressed images. Classpath
 * resources remain as a fallback, and synthetic images are used only when an
 * optional fixture is unavailable.
 */
object BenchmarkImageSets : KLogging() {

    private val loader: ImmutableImageLoader = ImmutableImageLoader.create()

    private fun loadOrSynthesize(
        fixtureName: String,
        resourcePath: String,
        width: Int,
        height: Int,
    ): ImmutableImage {
        val fixturePath = fixturePath(fixtureName)
        return when {
            fixturePath != null -> {
                log.debug { "Loading benchmark fixture: $fixturePath" }
                loader.fromPath(fixturePath)
            }
            BenchmarkImageSets::class.java.getResourceAsStream(resourcePath) != null -> {
                log.debug { "Loading benchmark image: $resourcePath" }
                BenchmarkImageSets::class.java.getResourceAsStream(resourcePath)!!.use(loader::fromStream)
            }
            else -> {
                log.warn {
                    "Benchmark image not found: $fixtureName, $resourcePath; using synthetic image (${width}x$height)"
                }
                val buffered = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
                val graphics = buffered.createGraphics()
                graphics.color = Color(100, 150, 200)
                graphics.fillRect(0, 0, width, height)
                graphics.dispose()
                ImmutableImage.fromAwt(buffered)
            }
        }
    }

    fun fixturePath(fixtureName: String): Path? =
        listOf(
            Path.of("images/src/test/resources/images", fixtureName),
            Path.of("../images/src/test/resources/images", fixtureName),
        ).firstOrNull(Files::isRegularFile)

    /** Natural photo image used by resize, encode, and vips benchmarks. */
    fun naturalPhoto(name: String): ImmutableImage = when (name) {
        "cafe"      -> cafe
        "landscape" -> landscape
        else        -> error("Unknown natural photo benchmark image: $name")
    }

    /** Natural cafe photo fixture (4032x3024). */
    val cafe: ImmutableImage by lazy {
        loadOrSynthesize("cafe.jpg", "/bench/cafe.jpg", 4032, 3024)
    }

    /** Natural landscape photo fixture (4032x3024). */
    val landscape: ImmutableImage by lazy {
        loadOrSynthesize("landscape.jpg", "/bench/landscape.jpg", 4032, 3024)
    }

    /** Backward-compatible 4K photo alias used by allocation benchmarks. */
    val photo4k: ImmutableImage by lazy { landscape }

    /** Landscape fixture path when available on disk. */
    val photo4kPath: Path? by lazy { fixturePath("landscape.jpg") }

    /** Homer illustration PNG fixture (1248x702). */
    val document: ImmutableImage by lazy {
        loadOrSynthesize("homer.png", "/bench/document.png", 1248, 702)
    }

    /** Homer JPEG thumbnail fixture (1248x702). */
    val thumbnail: ImmutableImage by lazy {
        loadOrSynthesize("homer.jpg", "/bench/thumbnail.jpg", 1248, 702)
    }

    /** Homer JPEG fixture path when available on disk. */
    val thumbnailPath: Path? by lazy { fixturePath("homer.jpg") }
}
