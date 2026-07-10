package io.bluetape4k.images.vips.java21

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.images.vips.VipsIncubatingApi
import io.bluetape4k.images.vips.VipsCodecDirection
import io.bluetape4k.images.vips.VipsCodecSupport
import io.bluetape4k.images.vips.VipsImageFormat
import org.junit.jupiter.api.Test

@OptIn(VipsIncubatingApi::class)
class JVipsCodecCapabilityTest {

    @Test
    fun `codecCapabilityReport marks stable formats and JVips HEIC encode limitation`() {
        val report = JVipsRuntime.codecCapabilityReport()

        report.backendName shouldBeEqualTo "JVips/JNI"
        report.stableFormats shouldBeEqualTo setOf(
            VipsImageFormat.JPEG,
            VipsImageFormat.PNG,
            VipsImageFormat.WEBP,
        )
        report.codec(VipsImageFormat.AVIF).decode.support shouldBeEqualTo VipsCodecSupport.UNKNOWN
        report.codec(VipsImageFormat.AVIF).encode.support shouldBeEqualTo VipsCodecSupport.UNKNOWN
        report.codec(VipsImageFormat.HEIC).decode.support shouldBeEqualTo VipsCodecSupport.UNKNOWN
        report.codec(VipsImageFormat.HEIC).encode.support shouldBeEqualTo VipsCodecSupport.UNAVAILABLE
        report.codec(VipsImageFormat.HEIC).encode.reason.orEmpty() shouldContain "JVips does not expose HEIC encoding"
    }

    @Test
    fun `smokeTestCodec returns sanitized decode failure for malformed bytes`() {
        val result = JVipsRuntime.smokeTestCodec(
            sampleBytes = byteArrayOf(1, 2, 3, 4),
            outputFormat = VipsImageFormat.AVIF,
        )

        result.backendName shouldBeEqualTo "JVips/JNI"
        result.format shouldBeEqualTo VipsImageFormat.AVIF
        result.succeeded shouldBeEqualTo false
        result.failureStage shouldBeEqualTo VipsCodecDirection.DECODE
        result.failureReason.orEmpty() shouldContain "AVIF decode failed on JVips/JNI"
    }
}
