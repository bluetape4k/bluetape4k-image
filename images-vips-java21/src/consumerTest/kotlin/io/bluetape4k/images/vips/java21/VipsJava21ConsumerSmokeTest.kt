package io.bluetape4k.images.vips.java21

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.images.vips.VipsImage
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class VipsJava21ConsumerSmokeTest {

    @Test
    fun `consumer decodes embedded PNG through production API`() {
        assumeTrue(
            System.getProperty("vips.consumer.enabled") == "true",
            "consumer smoke requires -Pvips.consumer.enabled=true",
        )

        JVipsRuntime.init()
        val image: VipsImage = vipsImageOf(EMBEDDED_PNG)
        image.use {
            it.width shouldBeEqualTo 1
            it.height shouldBeEqualTo 1
        }
    }

    private companion object {
        val EMBEDDED_PNG: ByteArray = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        )
    }
}
