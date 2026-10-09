package io.bluetape4k.images.examples.spring.intelligence.config

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.javatimes.millis
import io.bluetape4k.javatimes.nanos
import io.bluetape4k.javatimes.seconds
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Duration

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImageIntelligencePropertiesTest {

    companion object: KLogging()

    @Test
    fun `provides bounded defaults`() {
        val properties = ImageIntelligenceProperties()

        log.debug { "properties=$properties" }
        properties.maxInputBytes shouldBeEqualTo 5L * 1024L * 1024L
        properties.maxInputPixels shouldBeEqualTo 16_777_216L
        properties.maxInputSide shouldBeEqualTo 8_192
        properties.ocrTimeout shouldBeEqualTo 3.seconds()
        properties.detectionTimeout shouldBeEqualTo 2.seconds()
        properties.barcodeTimeout shouldBeEqualTo 2.seconds()
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
            ImageIntelligenceProperties(detectionTimeout = 1.nanos())
        }
        assertFailsWith<IllegalArgumentException> {
            ImageIntelligenceProperties(barcodeTimeout = (-1).millis())
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
