package io.bluetape4k.images.benchmark

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.nio.ImmutableImageLoader
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import java.awt.Color
import java.awt.image.BufferedImage

/**
 * Shared image set loader for JMH benchmarks.
 *
 * Loads images from `src/main/resources/bench/`.
 * Natural photo benchmarks use real fixture resources. Synthetic fallback is
 * kept only for optional resources that are not committed yet.
 *
 * Example:
 * ```kotlin
 * val image = BenchmarkImageSets.naturalPhoto("cafe")
 * val doc = BenchmarkImageSets.document
 * val thumb = BenchmarkImageSets.thumbnail
 * ```
 */
object BenchmarkImageSets : KLogging() {

    /**
     * Loads an image from a classpath resource or returns a synthetic image when absent.
     *
     * @param resourcePath classpath resource path, for example `/bench/cafe.jpg`
     * @param width synthetic fallback width in pixels
     * @param height synthetic fallback height in pixels
     * @return loaded or synthetic [ImmutableImage]
     */
    private fun loadOrSynthesize(resourcePath: String, width: Int, height: Int): ImmutableImage {
        val stream = BenchmarkImageSets::class.java.getResourceAsStream(resourcePath)
        return if (stream != null) {
            log.debug { "Loading benchmark image: $resourcePath" }
            stream.use { ImmutableImageLoader.create().fromStream(it) }
        } else {
            val buffered = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            val g = buffered.createGraphics()
            g.color = Color(100, 150, 200)
            g.fillRect(0, 0, width, height)
            g.dispose()
            ImmutableImage.fromAwt(buffered)
        }
    }

    /** Natural photo image used by resize and encode benchmarks. */
    fun naturalPhoto(name: String): ImmutableImage = when (name) {
        "cafe"      -> cafe
        "landscape" -> landscape
        else        -> error("Unknown natural photo benchmark image: $name")
    }

    /** Natural cafe photo (4032×3024). */
    val cafe: ImmutableImage by lazy { loadOrSynthesize("/bench/cafe.jpg", 4032, 3024) }

    /** Natural landscape photo (4032×3024). */
    val landscape: ImmutableImage by lazy { loadOrSynthesize("/bench/landscape.jpg", 4032, 3024) }

    /** Document-like image (1240×1754, A4). Returns a synthetic image until a fixture is added. */
    val document: ImmutableImage by lazy { loadOrSynthesize("/bench/document.png", 1240, 1754) }

    /** Thumbnail image (256×256). Returns a synthetic image until a fixture is added. */
    val thumbnail: ImmutableImage by lazy { loadOrSynthesize("/bench/thumbnail.jpg", 256, 256) }
}
