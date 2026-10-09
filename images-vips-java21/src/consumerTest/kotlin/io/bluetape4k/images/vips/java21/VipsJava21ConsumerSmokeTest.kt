package io.bluetape4k.images.vips.java21

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.codec.decodeBase64ByteArray
import io.bluetape4k.images.vips.VipsImage
import io.bluetape4k.logging.KLogging
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class VipsJava21ConsumerSmokeTest {

    private companion object: KLogging() {
        val EMBEDDED_PNG: ByteArray =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=".decodeBase64ByteArray()
//        Base64.getDecoder().decode(
//        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
//        )
    }

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

}
