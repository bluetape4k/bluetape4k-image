package io.bluetape4k.images.vips

import io.bluetape4k.assertions.shouldBeTrue
import org.junit.jupiter.api.Test

class VipsStableCodecCapabilityReportTest {

    @Test
    fun `stable report inspection needs no Vips opt in`() {
        val report = VipsCodecCapabilityReport(
            backendName = "test-backend",
            codecs = emptyList(),
        )

        report.isStableFormat(VipsImageFormat.JPEG).shouldBeTrue()
    }
}
