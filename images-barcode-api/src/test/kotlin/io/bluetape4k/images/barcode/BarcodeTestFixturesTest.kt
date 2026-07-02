package io.bluetape4k.images.barcode

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.images.ImageDimensions
import io.bluetape4k.images.barcode.testfixtures.BarcodeTestFixtures
import org.junit.jupiter.api.Test

class BarcodeTestFixturesTest {

    @Test
    fun `blank image fixture has deterministic size`() {
        val image = BarcodeTestFixtures.blankImage()

        image.width shouldBeEqualTo 180
        image.height shouldBeEqualTo 120
    }

    @Test
    fun `rotated fixture swaps dimensions`() {
        val image = BarcodeTestFixtures.blankImage(ImageDimensions(width = 64, height = 32))

        val rotated = BarcodeTestFixtures.rotateClockwise(image)

        rotated.width shouldBeEqualTo 32
        rotated.height shouldBeEqualTo 64
    }

    @Test
    fun `malformed bytes are deterministic`() {
        BarcodeTestFixtures.malformedImageBytes
            .contentEquals("not-an-image".toByteArray(Charsets.UTF_8))
            .shouldBeTrue()
        BarcodeTestFixtures.GENERATED_SOURCE_NOTE.isNotBlank().shouldBeTrue()
    }
}
