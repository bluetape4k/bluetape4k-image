package io.bluetape4k.images.vips.java21

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.images.vips.VipsCodecDirection
import io.bluetape4k.images.vips.VipsCodecSupport
import io.bluetape4k.images.vips.VipsImageFormat.AVIF
import io.bluetape4k.images.vips.VipsImageFormat.HEIC
import io.bluetape4k.images.vips.VipsImageFormat.JPEG
import io.bluetape4k.images.vips.VipsImageFormat.PNG
import io.bluetape4k.images.vips.VipsImageFormat.WEBP
import io.bluetape4k.images.vips.VipsIncubatingApi
import io.bluetape4k.logging.KLogging
import org.junit.jupiter.api.Test

@OptIn(VipsIncubatingApi::class)
class JVipsCodecCapabilityTest {

    companion object: KLogging()

    @Test
    fun `codecCapabilityReport marks stable formats and JVips HEIC encode limitation`() {
        val report = JVipsRuntime.codecCapabilityReport()

        report.backendName shouldBeEqualTo "JVips/JNI"
        report.stableFormats shouldBeEqualTo setOf(JPEG, PNG, WEBP)

        report.codec(AVIF).decode.support shouldBeEqualTo VipsCodecSupport.UNKNOWN
        report.codec(AVIF).encode.support shouldBeEqualTo VipsCodecSupport.UNKNOWN
        report.codec(HEIC).decode.support shouldBeEqualTo VipsCodecSupport.UNKNOWN
        report.codec(HEIC).encode.support shouldBeEqualTo VipsCodecSupport.UNAVAILABLE
        report.codec(HEIC).encode.reason.orEmpty() shouldContain "JVips does not expose HEIC encoding"
    }

    @Test
    fun `smokeTestCodec returns sanitized decode failure for malformed bytes`() {
        val result = JVipsRuntime.smokeTestCodec(
            sampleBytes = byteArrayOf(1, 2, 3, 4),
            outputFormat = AVIF,
        )

        result.backendName shouldBeEqualTo "JVips/JNI"
        result.format shouldBeEqualTo AVIF
        result.succeeded.shouldBeFalse()
        result.failureStage shouldBeEqualTo VipsCodecDirection.DECODE
        result.failureReason shouldContain "AVIF decode failed on JVips/JNI"
    }
}
