package io.bluetape4k.images.examples.spring.intelligence.config

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Duration

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImageIntelligencePropertiesTest {

    @Test
    fun `provides bounded defaults`() {
        val properties = ImageIntelligenceProperties()

        properties.maxInputBytes shouldBeEqualTo 5L * 1024L * 1024L
        properties.maxInputPixels shouldBeEqualTo 16_777_216L
        properties.maxInputSide shouldBeEqualTo 8_192
        properties.ocrTimeout shouldBeEqualTo Duration.ofSeconds(3)
        properties.detectionTimeout shouldBeEqualTo Duration.ofSeconds(2)
        properties.barcodeTimeout shouldBeEqualTo Duration.ofSeconds(2)
        properties.ocrConcurrency shouldBeEqualTo 1
        properties.detectionConcurrency shouldBeEqualTo 2
        properties.barcodeConcurrency shouldBeEqualTo 4
    }

    @Test
    fun `rejects non-positive upload limits`() {
        assertFailsWith<IllegalArgumentException> {
            ImageIntelligenceProperties(maxInputBytes = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            ImageIntelligenceProperties(maxInputPixels = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            ImageIntelligenceProperties(maxInputSide = 0)
        }
    }

    @Test
    fun `rejects timeouts shorter than one millisecond`() {
        assertFailsWith<IllegalArgumentException> {
            ImageIntelligenceProperties(ocrTimeout = Duration.ZERO)
        }
        assertFailsWith<IllegalArgumentException> {
            ImageIntelligenceProperties(detectionTimeout = Duration.ofNanos(1))
        }
        assertFailsWith<IllegalArgumentException> {
            ImageIntelligenceProperties(barcodeTimeout = Duration.ofMillis(-1))
        }
    }

    @Test
    fun `rejects non-positive provider concurrency`() {
        assertFailsWith<IllegalArgumentException> {
            ImageIntelligenceProperties(ocrConcurrency = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            ImageIntelligenceProperties(detectionConcurrency = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            ImageIntelligenceProperties(barcodeConcurrency = 0)
        }
    }

    @Test
    fun `rejects byte limits larger than a ByteArray`() {
        assertFailsWith<IllegalArgumentException> {
            ImageIntelligenceProperties(maxInputBytes = Int.MAX_VALUE.toLong() + 1L)
        }
    }
}
