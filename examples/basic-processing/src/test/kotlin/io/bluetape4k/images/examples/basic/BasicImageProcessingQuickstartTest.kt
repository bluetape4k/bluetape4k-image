package io.bluetape4k.images.examples.basic

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.junit5.coroutines.runSuspendIO
import org.junit.jupiter.api.Test
import java.nio.file.Files
import javax.imageio.ImageIO

class BasicImageProcessingQuickstartTest {

    @Test
    fun `generates deterministic basic processing outputs`() = runSuspendIO {
        val outputDirectory = Files.createTempDirectory("basic-processing-example-")

        val outputs = BasicImageProcessingQuickstart.generate(outputDirectory)

        outputs.size shouldBeEqualTo 5
        outputs.forEach { output ->
            Files.exists(output.path).shouldBeTrue()
            output.bytes shouldBeGreaterThan 0L
        }

        val byName = outputs.associateBy { it.fileName }
        byName.getValue("01-cafe-thumbnail.jpg").assertImage(width = 320, height = 240)
        byName.getValue("02-landscape-smart-crop.jpg").assertImage(width = 640, height = 360)
        byName.getValue("03-cafe-converted.png").assertImage(width = 800, height = 600)
        byName.getValue("04-landscape-watermarked.jpg").assertImage(width = 960, height = 540)
        byName.getValue("05-readme-workbench-preview.jpg").assertImage(width = 960, height = 540)
    }

    private fun GeneratedImage.assertImage(width: Int, height: Int) {
        this.width shouldBeEqualTo width
        this.height shouldBeEqualTo height
        val decoded = ImageIO.read(path.toFile())
        decoded.width shouldBeEqualTo width
        decoded.height shouldBeEqualTo height
    }
}
