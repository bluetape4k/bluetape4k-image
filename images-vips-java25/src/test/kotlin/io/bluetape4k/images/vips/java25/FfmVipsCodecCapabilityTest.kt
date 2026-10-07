package io.bluetape4k.images.vips.java25

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldMatch
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.assertions.shouldNotContain
import io.bluetape4k.images.vips.VipsCodecDirection
import io.bluetape4k.images.vips.VipsCodecSupport
import io.bluetape4k.images.vips.VipsImageFormat.AVIF
import io.bluetape4k.images.vips.VipsImageFormat.HEIC
import io.bluetape4k.images.vips.VipsIncubatingApi
import io.bluetape4k.images.vips.java25.internal.DefaultFfmVipsCodecProbe
import io.bluetape4k.images.vips.java25.internal.FfmVipsCodecProbe
import io.bluetape4k.images.vips.java25.internal.FfmVipsCodecProbeResult
import io.bluetape4k.images.vips.java25.internal.classifyFfmVipsCodecProbe
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(VipsIncubatingApi::class)
class FfmVipsCodecCapabilityTest {

    companion object: KLogging()

    private val testProbe = object: FfmVipsCodecProbe {
        override fun inspectOperation(name: String): FfmVipsCodecProbeResult =
            if (name == "heifload_buffer") {
                FfmVipsCodecProbeResult.Available
            } else {
                FfmVipsCodecProbeResult.Unavailable
            }

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

        log.debug { "report=$report" }

        report.backendName shouldBeEqualTo "vips-ffm"
        report.libvipsVersion shouldBeEqualTo "8.17.0-test"
        report.inspectedOperations shouldBeEqualTo setOf("heifload_buffer", "heifsave_buffer")

        report.codec(AVIF).decode.support shouldBeEqualTo VipsCodecSupport.AVAILABLE
        report.codec(AVIF).encode.support shouldBeEqualTo VipsCodecSupport.UNAVAILABLE
        report.codec(HEIC).decode.support shouldBeEqualTo VipsCodecSupport.AVAILABLE
        report.codec(HEIC).encode.support shouldBeEqualTo VipsCodecSupport.UNAVAILABLE
    }

    @Test
    fun `codec probe keeps unavailable and failed operation inspection distinct`() {
        val probe = object: FfmVipsCodecProbe {
            override fun inspectOperation(name: String): FfmVipsCodecProbeResult =
                when (name) {
                    "available" -> FfmVipsCodecProbeResult.Available
                    "unavailable" -> FfmVipsCodecProbeResult.Unavailable
                    else -> FfmVipsCodecProbeResult.Failed("secret=/run/secrets/libvips-path")
                }
        }

        probe.supportsOperation("available").shouldBeTrue()
        probe.supportsOperation("unavailable").shouldBeFalse()
        probe.supportsOperation("failed").shouldBeFalse()

        FfmVipsRuntime.codecProbe = object: FfmVipsCodecProbe {
            override fun inspectOperation(name: String): FfmVipsCodecProbeResult =
                if (name == "heifload_buffer") {
                    FfmVipsCodecProbeResult.Failed("secret=/run/secrets/libvips-path")
                } else {
                    FfmVipsCodecProbeResult.Unavailable
                }
        }

        val report = FfmVipsRuntime.codecCapabilityReport()
        val decode = report.codec(AVIF).decode

        log.debug { "decode=$decode" }
        decode.support shouldBeEqualTo VipsCodecSupport.UNKNOWN
        decode.reason shouldContain "Codec operation probe failed"
        decode.reason shouldNotContain "/run/secrets/libvips-path"

        report.codec(AVIF).encode.support shouldBeEqualTo VipsCodecSupport.UNAVAILABLE
    }

    @Test
    fun `default probe classifier separates true false exception and fatal error`() {
        classifyFfmVipsCodecProbe { true } shouldBeEqualTo FfmVipsCodecProbeResult.Available
        classifyFfmVipsCodecProbe { false } shouldBeEqualTo FfmVipsCodecProbeResult.Unavailable
        classifyFfmVipsCodecProbe { error("secret native path") } shouldBeEqualTo
                FfmVipsCodecProbeResult.Failed(FfmVipsCodecProbeResult.SAFE_FAILURE_REASON)

        assertFailsWith<AssertionError> {
            classifyFfmVipsCodecProbe {
                throw AssertionError("fatal native linkage")
            }
        }
    }

    @Test
    fun `fatal probe errors are not converted to codec absence`() {
        FfmVipsRuntime.codecProbe = object: FfmVipsCodecProbe {
            override fun inspectOperation(name: String): FfmVipsCodecProbeResult =
                throw AssertionError("fatal native linkage")
        }

        assertFailsWith<AssertionError> {
            FfmVipsRuntime.codecCapabilityReport()
        }
    }

    @Test
    fun `default probe exposes the native libvips version`() {
        val version = DefaultFfmVipsCodecProbe.libvipsVersion().shouldNotBeNull()
        log.debug { "version=$version" }

        version shouldMatch "\\d+\\.\\d+\\.\\d+"
    }

    @Test
    fun `smokeTestCodec returns sanitized decode failure for malformed bytes`() {
        val result = FfmVipsRuntime.smokeTestCodec(
            sampleBytes = byteArrayOf(1, 2, 3, 4),
            outputFormat = HEIC,
        )

        log.debug { "result=$result" }
        result.backendName shouldBeEqualTo "vips-ffm"
        result.format shouldBeEqualTo HEIC
        result.succeeded.shouldBeFalse()
        result.failureStage shouldBeEqualTo VipsCodecDirection.DECODE
        result.failureReason shouldContain "HEIC decode failed on vips-ffm"
    }
}
