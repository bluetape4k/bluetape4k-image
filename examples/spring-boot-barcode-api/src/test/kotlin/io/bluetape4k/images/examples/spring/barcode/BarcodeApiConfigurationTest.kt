package io.bluetape4k.images.examples.spring.barcode

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.images.barcode.BarcodeReader
import io.bluetape4k.images.barcode.zxing.ZxingBarcodeReader
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.runner.ApplicationContextRunner

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BarcodeApiConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(BarcodeApiConfiguration::class.java)

    @Test
    fun `registers default limits fixtures and ZXing reader`() {
        contextRunner.run { context ->
            val properties = context.getBean(BarcodeExampleProperties::class.java)
            properties.maxInputBytes shouldBeEqualTo 5L * 1024L * 1024L
            properties.maxInputPixels shouldBeEqualTo 16_777_216L
            properties.maxInputSide shouldBeEqualTo 8_192

            context.getBean(BarcodeReader::class.java) shouldBeInstanceOf ZxingBarcodeReader::class
            context.getBean(BarcodeExampleFixtures::class.java)
                .bytes(BarcodeExampleFixture.SAMPLE)
                .isNotEmpty() shouldBeEqualTo true
        }
    }

    @Test
    fun `uses a fixed PNG JPEG and WebP content type allowlist`() {
        ALLOWED_BARCODE_CONTENT_TYPES shouldBeEqualTo setOf(
            "image/png",
            "image/jpeg",
            "image/webp",
        )
    }

    @Test
    fun `binds custom positive limits`() {
        contextRunner
            .withPropertyValues(
                "example.barcode.max-input-bytes=1048576",
                "example.barcode.max-input-pixels=1000000",
                "example.barcode.max-input-side=1024",
            )
            .run { context ->
                val properties = context.getBean(BarcodeExampleProperties::class.java)
                properties.maxInputBytes shouldBeEqualTo 1_048_576L
                properties.maxInputPixels shouldBeEqualTo 1_000_000L
                properties.maxInputSide shouldBeEqualTo 1_024
            }
    }

    @Test
    fun `rejects non-positive byte limit`() {
        contextRunner
            .withPropertyValues("example.barcode.max-input-bytes=0")
            .run { context ->
                context.startupFailure.shouldNotBeNull()
            }
    }

    @Test
    fun `rejects non-positive pixel and side limits`() {
        listOf(
            "example.barcode.max-input-pixels=0",
            "example.barcode.max-input-side=-1",
        ).forEach { property ->
            contextRunner.withPropertyValues(property).run { context ->
                context.startupFailure.shouldNotBeNull()
            }
        }
    }

    @Test
    fun `rejects byte limit larger than ByteArray capacity`() {
        contextRunner
            .withPropertyValues("example.barcode.max-input-bytes=2147483648")
            .run { context ->
                context.startupFailure.shouldNotBeNull()
            }
    }
}
