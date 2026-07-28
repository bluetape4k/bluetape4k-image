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
 * benchmark fixture용 shared image set loader입니다.
 *
 * allocation 및 throughput evidence가 실제 compressed image를 반영하도록 `images/src/test/resources/images` 아래의
 * checked-in image fixture를 선호합니다. classpath resource는 fallback으로 남기며, optional fixture를 사용할 수 없을 때만
 * synthetic image를 사용합니다.
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

    /** resize, encode, vips benchmark에 사용하는 natural photo image입니다. */
    fun naturalPhoto(name: String): ImmutableImage = when (name) {
        "cafe"      -> cafe
        "landscape" -> landscape
        else        -> error("Unknown natural photo benchmark image: $name")
    }

    /** natural cafe photo fixture입니다(4032x3024). */
    val cafe: ImmutableImage by lazy {
        loadOrSynthesize("cafe.jpg", "/bench/cafe.jpg", 4032, 3024)
    }

    /** natural landscape photo fixture입니다(4032x3024). */
    val landscape: ImmutableImage by lazy {
        loadOrSynthesize("landscape.jpg", "/bench/landscape.jpg", 4032, 3024)
    }

    /** allocation benchmark가 사용하는 backward-compatible 4K photo alias입니다. */
    val photo4k: ImmutableImage by lazy { landscape }

    /** disk에서 사용할 수 있을 때의 landscape fixture path입니다. */
    val photo4kPath: Path? by lazy { fixturePath("landscape.jpg") }

    /** Homer illustration PNG fixture입니다(1248x702). */
    val document: ImmutableImage by lazy {
        loadOrSynthesize("homer.png", "/bench/document.png", 1248, 702)
    }

    /** Homer JPEG thumbnail fixture입니다(1248x702). */
    val thumbnail: ImmutableImage by lazy {
        loadOrSynthesize("homer.jpg", "/bench/thumbnail.jpg", 1248, 702)
    }

    /** disk에서 사용할 수 있을 때의 Homer JPEG fixture path입니다. */
    val thumbnailPath: Path? by lazy { fixturePath("homer.jpg") }
}
