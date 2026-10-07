package io.bluetape4k.images.vips

import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test

class VipsStableCodecCapabilityReportTest {

    companion object: KLogging()

    @OptIn(VipsIncubatingApi::class)
    @Test
    fun `stable report inspection needs no Vips opt in`() {
        val report = VipsCodecCapabilityReport(
            backendName = "test-backend",
            codecs = emptyList(),
        )

        log.debug { "report=$report" }
        report.isStableFormat(VipsImageFormat.JPEG).shouldBeTrue()
        report.isStableFormat(VipsImageFormat.PNG).shouldBeTrue()
        report.isStableFormat(VipsImageFormat.WEBP).shouldBeTrue()

        report.isStableFormat(VipsImageFormat.AVIF).shouldBeFalse()
        report.isStableFormat(VipsImageFormat.HEIC).shouldBeFalse()
    }
}
