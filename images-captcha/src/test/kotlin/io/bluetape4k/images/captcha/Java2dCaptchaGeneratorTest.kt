package io.bluetape4k.images.captcha

import com.sksamuel.scrimage.nio.PngWriter
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldNotBeEmpty
import java.awt.Color
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

class Java2dCaptchaGeneratorTest {

    @Test
    fun `generate returns text image and advisory expiration`() {
        val clock = Clock.fixed(Instant.parse("2026-05-24T00:00:00Z"), ZoneOffset.UTC)
        val generator = captchaGenerator(clock) {
            length(5)
            charSet("ABCDEF")
            imageSize(160, 64)
            fontSize(30)
            noise(CaptchaNoise.Low)
            expiresAfter(2.minutes)
        }

        val challenge = generator.generate()
        val bytes = challenge.image.forWriter(PngWriter.MaxCompression).bytes()

        challenge.text.length shouldBeEqualTo 5
        challenge.text.all { it in "ABCDEF" } shouldBeEqualTo true
        challenge.image.width shouldBeEqualTo 160
        challenge.image.height shouldBeEqualTo 64
        challenge.expiresAt shouldBeEqualTo Instant.parse("2026-05-24T00:02:00Z")
        bytes.shouldNotBeEmpty()
        bytes.size shouldBeGreaterThan 0
    }

    @Test
    fun `generate validates per-call length override`() {
        val generator = captchaGenerator()

        assertFailsWith<IllegalArgumentException> {
            generator.generate(0)
        }
    }

    @Test
    fun `wave distortion still produces encodable image`() {
        val generator = captchaGenerator {
            imageSize(140, 52)
            backgroundColor(Color.WHITE)
            textColors(Color.BLACK, Color.BLUE)
            distortion(CaptchaDistortion.Wave(0.5f))
            noise(CaptchaNoise.Custom(lines = 1, dots = 3))
        }

        val challenge = generator.generate(4)
        val bytes = challenge.image.forWriter(PngWriter.MaxCompression).bytes()

        challenge.text.length shouldBeEqualTo 4
        bytes.shouldNotBeEmpty()
    }

    @Test
    fun `suspend generation honors cancellation before rendering starts`() = runTest {
        val renderStarts = AtomicInteger()
        val generator = Java2dCaptchaGenerator(
            options = CaptchaOptions(),
            random = SecureRandom(),
            onBeforeRender = { renderStarts.incrementAndGet() },
        )

        val cancelledJob = Job().apply { cancel() }

        assertFailsWith<CancellationException> {
            withContext(cancelledJob) {
                generator.generateSuspend()
            }
        }
        renderStarts.get() shouldBeEqualTo 0
    }
}
