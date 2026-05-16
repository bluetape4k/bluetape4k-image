package io.bluetape4k.images.batch

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.images.AbstractImageTest
import io.bluetape4k.junit5.tempfolder.TempFolder
import io.bluetape4k.junit5.tempfolder.TempFolderTest
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

@TempFolderTest
class ImageDimensionProbeTest : AbstractImageTest() {

    @Test
    fun `probeImagePixelCount returns width times height for valid jpeg`(tempFolder: TempFolder) {
        val width = 120
        val height = 80
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val file = tempFolder.createFile("sample.jpg")
        ImageIO.write(img, "jpg", file)

        val pixelCount = probeImagePixelCount(file.toPath())

        pixelCount.shouldNotBeNull()
        pixelCount shouldBeEqualTo (width.toLong() * height.toLong())
    }

    @Test
    fun `probeImagePixelCount returns null for non-image file`(tempFolder: TempFolder) {
        val textFile = tempFolder.createFile("broken.txt")
        Files.writeString(textFile.toPath(), "this is not an image")

        val result = probeImagePixelCount(textFile.toPath())

        result shouldBeEqualTo null
    }

    @Test
    fun `probeImagePixelCount works on png file`(tempFolder: TempFolder) {
        val width = 64
        val height = 48
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val file = tempFolder.createFile("sample.png")
        ImageIO.write(img, "png", file)

        val pixelCount = probeImagePixelCount(file.toPath())

        pixelCount.shouldNotBeNull()
        pixelCount shouldBeEqualTo (width.toLong() * height.toLong())
        pixelCount shouldBeGreaterThan 0L
    }

    @Test
    fun `probeImagePixelCount returns positive count for existing test jpeg`() {
        val path = Path.of("$BASE_PATH/homer.jpg")
        val pixelCount = probeImagePixelCount(path)

        pixelCount.shouldNotBeNull()
        pixelCount shouldBeGreaterThan 0L
    }
}
