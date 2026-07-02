package io.bluetape4k.images.vips.java25

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.images.IncubatingImageApi
import io.bluetape4k.images.vips.VipsCodecDirection
import io.bluetape4k.images.vips.VipsCodecSupport
import io.bluetape4k.images.vips.VipsImageFormat
import io.bluetape4k.images.vips.java25.internal.DefaultFfmVipsCodecProbe
import io.bluetape4k.images.vips.java25.internal.FfmVipsCodecProbe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(IncubatingImageApi::class)
class FfmVipsCodecCapabilityTest {

    private val testProbe = object : FfmVipsCodecProbe {
        override fun supportsOperation(name: String): Boolean =
            name == "heifload_buffer"

        override fun libvipsVersion(): String =
            "8.17.0-test"
    }

    @BeforeEach
    fun setup() {
        FfmVipsRuntime.codecProbe = testProbe
    }

    @AfterEach
    fun teardown() {
        FfmVipsRuntime.codecProbe = DefaultFfmVipsCodecProbe
    }

    @Test
    fun `codecCapabilityReport maps FFM operation probes`() {
        val report = FfmVipsRuntime.codecCapabilityReport()

        report.backendName shouldBeEqualTo "vips-ffm"
        report.libvipsVersion shouldBeEqualTo "8.17.0-test"
        report.inspectedOperations shouldBeEqualTo setOf("heifload_buffer", "heifsave_buffer")
        report.codec(VipsImageFormat.AVIF).decode.support shouldBeEqualTo VipsCodecSupport.AVAILABLE
        report.codec(VipsImageFormat.AVIF).encode.support shouldBeEqualTo VipsCodecSupport.UNAVAILABLE
        report.codec(VipsImageFormat.HEIC).decode.support shouldBeEqualTo VipsCodecSupport.AVAILABLE
        report.codec(VipsImageFormat.HEIC).encode.support shouldBeEqualTo VipsCodecSupport.UNAVAILABLE
    }

    @Test
    fun `smokeTestCodec returns sanitized decode failure for malformed bytes`() {
        val result = FfmVipsRuntime.smokeTestCodec(
            sampleBytes = byteArrayOf(1, 2, 3, 4),
            outputFormat = VipsImageFormat.HEIC,
        )

        result.backendName shouldBeEqualTo "vips-ffm"
        result.format shouldBeEqualTo VipsImageFormat.HEIC
        result.succeeded shouldBeEqualTo false
        result.failureStage shouldBeEqualTo VipsCodecDirection.DECODE
        result.failureReason.orEmpty() shouldContain "HEIC decode failed on vips-ffm"
    }
}
