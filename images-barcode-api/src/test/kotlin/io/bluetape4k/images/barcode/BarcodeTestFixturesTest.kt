package io.bluetape4k.images.barcode

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContentEqual
import io.bluetape4k.assertions.shouldNotBeBlank
import io.bluetape4k.images.ImageDimensions
import io.bluetape4k.images.barcode.testfixtures.BarcodeTestFixtures
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.support.toUtf8Bytes
import org.junit.jupiter.api.Test

class BarcodeTestFixturesTest {

    companion object: KLogging()

    @Test
    fun `blank image fixture has deterministic size`() {
        val image = BarcodeTestFixtures.blankImage()

        log.debug { "image=$image" }
        image.width shouldBeEqualTo 180
        image.height shouldBeEqualTo 120
    }

    @Test
    fun `rotated fixture swaps dimensions`() {
        val image = BarcodeTestFixtures.blankImage(ImageDimensions(width = 64, height = 32))

        val rotated = BarcodeTestFixtures.rotateClockwise(image)
        log.debug { "rotated=$rotated" }

        rotated.width shouldBeEqualTo 32
        rotated.height shouldBeEqualTo 64
    }

    @Test
    fun `malformed bytes are deterministic`() {
        BarcodeTestFixtures.malformedImageBytes shouldContentEqual "not-an-image".toUtf8Bytes()
        BarcodeTestFixtures.GENERATED_SOURCE_NOTE.shouldNotBeBlank()
    }
}
