package io.bluetape4k.images.vips

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import org.junit.jupiter.api.Test

@OptIn(VipsIncubatingApi::class)
class VipsCodecCapabilityReportTest {

    @Test
    fun `report keeps stable formats available unconditionally`() {
        val report = VipsCodecCapabilityReport(
            backendName = "test-backend",
            codecs = listOf(
                VipsCodecCapability.heifFamily(
                    format = VipsImageFormat.AVIF,
                    decode = VipsCodecOperationCapability.available(
                        direction = VipsCodecDirection.DECODE,
                        operationName = "heifload_buffer",
                    ),
                    encode = VipsCodecOperationCapability.available(
                        direction = VipsCodecDirection.ENCODE,
                        operationName = "heifsave_buffer",
                    ),
                    nativeDependencies = listOf("libheif", "libaom"),
                ),
            ),
            inspectedOperations = setOf("heifload_buffer", "heifsave_buffer"),
        )

        report.stableFormats shouldBeEqualTo setOf(
            VipsImageFormat.JPEG,
            VipsImageFormat.PNG,
            VipsImageFormat.WEBP,
        )
        report.isStableFormat(VipsImageFormat.JPEG).shouldBeTrue()
        report.codec(VipsImageFormat.AVIF).decode.support shouldBeEqualTo VipsCodecSupport.AVAILABLE
    }

    @Test
    fun `unknown capability preserves safe diagnostic detail`() {
        val capability = VipsCodecOperationCapability.unknown(
            direction = VipsCodecDirection.DECODE,
            operationName = "heifload_buffer",
            reason = "Backend cannot inspect libvips operations; run smokeTestCodec with caller samples.",
        )

        capability.support shouldBeEqualTo VipsCodecSupport.UNKNOWN
        capability.operationName shouldBeEqualTo "heifload_buffer"
        capability.reason shouldBeEqualTo
            "Backend cannot inspect libvips operations; run smokeTestCodec with caller samples."
    }

    @Test
    fun `smoke result reports sanitized failure without native exception text`() {
        val result = VipsCodecSmokeResult.failure(
            backendName = "test-backend",
            format = VipsImageFormat.HEIC,
            stage = VipsCodecDirection.ENCODE,
            reason = "HEIC encode failed on test-backend; verify native codec support.",
        )

        result.succeeded shouldBeEqualTo false
        result.failureStage shouldBeEqualTo VipsCodecDirection.ENCODE
        result.failureReason shouldBeEqualTo "HEIC encode failed on test-backend; verify native codec support."
    }

    @Test
    fun `partial smoke result requires failure stage and reason`() {
        assertFailsWith<IllegalArgumentException> {
            VipsCodecSmokeResult(
                backendName = "test-backend",
                format = VipsImageFormat.AVIF,
                decoded = true,
                encoded = false,
            )
        }
    }
}
