package io.bluetape4k.images.examples.spring.intelligence.service

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.images.ImageDimensions
import io.bluetape4k.images.analysis.ImageMetadataReadOptions
import io.bluetape4k.images.analysis.readImageMetadataReport
import io.bluetape4k.images.examples.spring.intelligence.config.ImageIntelligenceProperties
import io.bluetape4k.images.examples.spring.intelligence.support.jpegBytes
import io.bluetape4k.images.examples.spring.intelligence.support.pngBytes
import io.bluetape4k.images.examples.spring.intelligence.support.webpBytes
import io.bluetape4k.images.immutableImageOf
import io.bluetape4k.images.probeImageDimensions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImageUploadQualifierTest {

    @Test
    fun `qualifies PNG and decodes it exactly once`() = runTest {
        val decodeCalls = AtomicInteger()
        val qualifier = ImageUploadQualifier(
            properties = ImageIntelligenceProperties(),
            imageDecoder = { bytes ->
                decodeCalls.incrementAndGet()
                immutableImageOf(bytes)
            },
        )

        val qualified = qualifier.qualify(multipart("image/png", pngBytes(40, 30)))

        qualified.mediaType shouldBeEqualTo "image/png"
        qualified.dimensions shouldBeEqualTo ImageDimensions(40, 30)
        qualified.image.width shouldBeEqualTo 40
        qualified.image.height shouldBeEqualTo 30
        decodeCalls.get() shouldBeEqualTo 1
    }

    @Test
    fun `accepts matching JPEG and WebP magic bytes`() = runTest {
        listOf(
            "image/jpeg" to jpegBytes(),
            "image/webp" to webpBytes(),
        ).forEach { (contentType, bytes) ->
            val qualified = qualifier().qualify(multipart(contentType, bytes))
            qualified.mediaType shouldBeEqualTo contentType
        }
    }

    @Test
    fun `rejects missing unsupported and mismatched media types before decode`() = runTest {
        val decodeCalls = AtomicInteger()
        val qualifier = qualifier(decodeCalls = decodeCalls)

        listOf(
            multipart(null, pngBytes()),
            multipart("image/gif", byteArrayOf(0x47, 0x49, 0x46)),
            multipart("image/png", jpegBytes()),
        ).forEach { file ->
            assertFailsWith<InvalidImageUploadException> {
                qualifier.qualify(file)
            }
        }

        decodeCalls.get() shouldBeEqualTo 0
    }

    @Test
    fun `rejects empty reported and actual byte overflow before decode`() = runTest {
        val decodeCalls = AtomicInteger()
        val properties = ImageIntelligenceProperties(maxInputBytes = 8)
        val qualifier = qualifier(properties, decodeCalls)

        assertFailsWith<InvalidImageUploadException> {
            qualifier.qualify(multipart("image/png", ByteArray(0)))
        }
        assertFailsWith<ImagePayloadTooLargeException> {
            qualifier.qualify(
                TrackingMultipartFile(
                    contentType = "image/png",
                    content = byteArrayOf(1),
                    reportedSize = 9,
                ),
            )
        }
        assertFailsWith<ImagePayloadTooLargeException> {
            qualifier.qualify(
                TrackingMultipartFile(
                    contentType = "image/png",
                    content = ByteArray(9),
                    reportedSize = 1,
                ),
            )
        }

        decodeCalls.get() shouldBeEqualTo 0
    }

    @Test
    fun `rejects side and pixel overflow before decode`() = runTest {
        val decodeCalls = AtomicInteger()

        assertFailsWith<ImagePayloadTooLargeException> {
            qualifier(
                properties = ImageIntelligenceProperties(maxInputSide = 39),
                decodeCalls = decodeCalls,
            ).qualify(multipart("image/png", pngBytes(40, 30)))
        }
        assertFailsWith<ImagePayloadTooLargeException> {
            qualifier(
                properties = ImageIntelligenceProperties(maxInputPixels = 1_199),
                decodeCalls = decodeCalls,
            ).qualify(multipart("image/png", pngBytes(40, 30)))
        }

        decodeCalls.get() shouldBeEqualTo 0
    }

    @Test
    fun `normalizes malformed image input without exposing decoder detail`() = runTest {
        val error = assertFailsWith<InvalidImageUploadException> {
            qualifier(
                dimensionProbe = { ImageDimensions(1, 1) },
                metadataDimensionProbe = { _, _ -> ImageDimensions(1, 1) },
                imageDecoder = { error("decoder-secret") },
            ).qualify(multipart("image/png", pngBytes()))
        }

        error.reasonCode shouldBeEqualTo "image_not_decodable"
        error.message shouldBeEqualTo "The uploaded file is not a decodable image."
    }

    @Test
    fun `rejects payload when both dimension probes fail`() = runTest {
        val error = assertFailsWith<InvalidImageUploadException> {
            qualifier(
                dimensionProbe = { null },
                metadataDimensionProbe = { _, _ -> null },
            ).qualify(multipart("image/png", pngBytes()))
        }

        error.reasonCode shouldBeEqualTo "image_not_decodable"
    }

    @Test
    fun `rethrows cancellation while reading upload bytes`() = runTest {
        val expected = CancellationException("cancel-upload")
        val file = TrackingMultipartFile(
            contentType = "image/png",
            content = pngBytes(),
            onRead = { throw expected },
        )

        val actual = assertFailsWith<CancellationException> {
            qualifier().qualify(file)
        }

        actual.message shouldBeEqualTo expected.message
    }

    private fun qualifier(
        properties: ImageIntelligenceProperties = ImageIntelligenceProperties(),
        decodeCalls: AtomicInteger = AtomicInteger(),
        dimensionProbe: (ByteArray) -> ImageDimensions? = ::probeImageDimensions,
        metadataDimensionProbe: (ByteArray, Int) -> ImageDimensions? = { bytes, maxBytes ->
            readImageMetadataReport(
                bytes,
                ImageMetadataReadOptions(maxBytes = maxBytes),
            ).dimensions
        },
        imageDecoder: (ByteArray) -> com.sksamuel.scrimage.ImmutableImage = { bytes ->
            decodeCalls.incrementAndGet()
            immutableImageOf(bytes)
        },
    ): ImageUploadQualifier =
        ImageUploadQualifier(
            properties = properties,
            dimensionProbe = dimensionProbe,
            metadataDimensionProbe = metadataDimensionProbe,
            imageDecoder = imageDecoder,
        )

    private fun multipart(contentType: String?, bytes: ByteArray): MockMultipartFile =
        MockMultipartFile("file", "upload", contentType, bytes)

    private class TrackingMultipartFile(
        private val contentType: String?,
        private val content: ByteArray,
        private val reportedSize: Long = content.size.toLong(),
        private val onRead: () -> Unit = {},
    ) : MultipartFile {
        override fun getName(): String = "file"
        override fun getOriginalFilename(): String = "upload"
        override fun getContentType(): String? = contentType
        override fun isEmpty(): Boolean = content.isEmpty()
        override fun getSize(): Long = reportedSize

        override fun getBytes(): ByteArray {
            onRead()
            return content.copyOf()
        }

        override fun getInputStream(): InputStream = ByteArrayInputStream(content)

        override fun transferTo(dest: File) {
            dest.writeBytes(content)
        }
    }
}
