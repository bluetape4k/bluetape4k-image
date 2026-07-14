package io.bluetape4k.images.examples.spring.barcode

import com.sksamuel.scrimage.nio.JpegWriter
import com.sksamuel.scrimage.webp.WebpWriter
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeSameInstanceAs
import io.bluetape4k.images.ImageDimensions
import io.bluetape4k.images.barcode.BarcodeException
import io.bluetape4k.images.barcode.BarcodeFailureReason
import io.bluetape4k.images.barcode.BarcodeFormat
import io.bluetape4k.images.barcode.BarcodeReader
import io.bluetape4k.images.barcode.zxing.ZxingBarcodeReader
import io.bluetape4k.images.immutableImageOf
import io.bluetape4k.images.probeImageDimensions
import io.bluetape4k.images.analysis.ImageMetadataReadOptions
import io.bluetape4k.images.analysis.readImageMetadataReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BarcodeExtractionServiceTest {

    private val fixtures = BarcodeExampleFixtures()

    @Test
    fun `extracts the QR fixture into a bounded provider neutral response`() = runTest {
        val response = service().extract(fixtures.bytes(BarcodeExampleFixture.SAMPLE))

        response.count shouldBeEqualTo 1
        response.results.single() shouldBeEqualTo BarcodeResultResponse(
            text = "bluetape4k-barcode-quickstart",
            format = BarcodeFormat.QR_CODE,
            provider = "ZXing",
        )
    }

    @Test
    fun `returns an empty response when a valid image has no barcode`() = runTest {
        val response = service().extract(fixtures.bytes(BarcodeExampleFixture.NO_RESULT))

        response.count shouldBeEqualTo 0
        response.results shouldBeEqualTo emptyList()
    }

    @Test
    fun `accepts JPEG and WebP uploads`() = runTest {
        val image = immutableImageOf(fixtures.bytes(BarcodeExampleFixture.SAMPLE))
        val uploads = listOf(
            "image/jpeg" to image.forWriter(JpegWriter(90, false)).bytes(),
            "image/webp" to image.forWriter(WebpWriter.DEFAULT).bytes(),
        )

        uploads.forEach { (contentType, bytes) ->
            val response = service().extract(multipart(contentType, bytes))
            response.count shouldBeEqualTo 1
            response.results.single().format shouldBeEqualTo BarcodeFormat.QR_CODE
        }
    }

    @Test
    fun `uses bounded metadata dimensions when primary WebP probe is unavailable`() = runTest {
        val webp = immutableImageOf(fixtures.bytes(BarcodeExampleFixture.SAMPLE))
            .forWriter(WebpWriter.DEFAULT)
            .bytes()
        val response = service(dimensionProbe = { null }).extract(multipart("image/webp", webp))

        response.count shouldBeEqualTo 1
    }

    @Test
    fun `rejects empty unsupported and missing content type uploads`() = runTest {
        val empty = assertFailsWith<BarcodeRequestException> {
            service().extract(multipart("image/png", ByteArray(0)))
        }
        empty.status shouldBeEqualTo HttpStatus.BAD_REQUEST
        empty.error shouldBeEqualTo "empty_input"

        listOf("text/plain", null).forEach { contentType ->
            val error = assertFailsWith<BarcodeRequestException> {
                service().extract(multipart(contentType, byteArrayOf(1)))
            }
            error.status shouldBeEqualTo HttpStatus.UNSUPPORTED_MEDIA_TYPE
            error.error shouldBeEqualTo "unsupported_media_type"
        }
    }

    @Test
    fun `rejects reported and actual encoded byte overflow before decode`() = runTest {
        val readerCalls = AtomicInteger()
        val reader = BarcodeReader { _, _ ->
            readerCalls.incrementAndGet()
            emptyList()
        }
        val properties = BarcodeExampleProperties(maxInputBytes = 8)

        val reported = assertFailsWith<BarcodeRequestException> {
            service(reader, properties).extract(
                TrackingMultipartFile(
                    contentType = "image/png",
                    content = byteArrayOf(1),
                    reportedSize = 9,
                )
            )
        }
        reported.status shouldBeEqualTo HttpStatus.CONTENT_TOO_LARGE

        val actual = assertFailsWith<BarcodeRequestException> {
            service(reader, properties).extract(
                TrackingMultipartFile(
                    contentType = "image/png",
                    content = ByteArray(9),
                    reportedSize = 1,
                )
            )
        }
        actual.status shouldBeEqualTo HttpStatus.CONTENT_TOO_LARGE
        readerCalls.get() shouldBeEqualTo 0
    }

    @Test
    fun `rejects decoded side and pixel overflow before provider invocation`() = runTest {
        val readerCalls = AtomicInteger()
        val reader = BarcodeReader { _, _ ->
            readerCalls.incrementAndGet()
            emptyList()
        }
        val bytes = fixtures.bytes(BarcodeExampleFixture.SAMPLE)

        val sideError = assertFailsWith<BarcodeRequestException> {
            service(
                reader = reader,
                properties = BarcodeExampleProperties(maxInputSide = 100),
            ).extract(bytes)
        }
        sideError.status shouldBeEqualTo HttpStatus.CONTENT_TOO_LARGE

        val pixelError = assertFailsWith<BarcodeRequestException> {
            service(
                reader = reader,
                properties = BarcodeExampleProperties(maxInputPixels = 40_000),
            ).extract(bytes)
        }
        pixelError.status shouldBeEqualTo HttpStatus.CONTENT_TOO_LARGE
        readerCalls.get() shouldBeEqualTo 0
    }

    @Test
    fun `normalizes malformed bytes and missing dimensions`() = runTest {
        val malformed = assertFailsWith<BarcodeException> {
            service().extract(fixtures.bytes(BarcodeExampleFixture.MALFORMED))
        }
        malformed.reason shouldBeEqualTo BarcodeFailureReason.MALFORMED_INPUT

        val missingDimensions = assertFailsWith<BarcodeException> {
            service(
                dimensionProbe = { null },
                metadataDimensionProbe = { _, _ -> null },
            ).extract(byteArrayOf(1, 2, 3))
        }
        missingDimensions.reason shouldBeEqualTo BarcodeFailureReason.MALFORMED_INPUT
    }

    @Test
    fun `preserves provider neutral failures`() = runTest {
        listOf(
            BarcodeFailureReason.UNSUPPORTED_FORMAT,
            BarcodeFailureReason.PROVIDER_UNAVAILABLE,
            BarcodeFailureReason.DECODE_FAILED,
        ).forEach { reason ->
            val expected = BarcodeException(reason, "provider detail")
            val reader = BarcodeReader { _, _ -> throw expected }

            val actual = assertFailsWith<BarcodeException> {
                service(reader).extract(fixtures.bytes(BarcodeExampleFixture.SAMPLE))
            }
            actual shouldBeSameInstanceAs expected
        }
    }

    @Test
    fun `rethrows cancellation unchanged`() = runTest {
        val expected = CancellationException("cancel extraction")
        val reader = BarcodeReader { _, _ -> throw expected }

        val actual = assertFailsWith<CancellationException> {
            service(reader).extract(fixtures.bytes(BarcodeExampleFixture.SAMPLE))
        }
        actual.message shouldBeEqualTo expected.message
    }

    @Test
    fun `uses IO for multipart bytes and CPU for probe and provider`() = runTest {
        val readThread = AtomicReference<String>()
        val probeThread = AtomicReference<String>()
        val readerThread = AtomicReference<String>()
        val bytes = fixtures.bytes(BarcodeExampleFixture.SAMPLE)
        val file = TrackingMultipartFile(
            contentType = "image/png",
            content = bytes,
            onRead = { readThread.set(Thread.currentThread().name) },
        )
        val reader = BarcodeReader { _, _ ->
            readerThread.set(Thread.currentThread().name)
            emptyList()
        }

        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "barcode-io") }
            .asCoroutineDispatcher().use { ioDispatcher ->
                Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "barcode-cpu") }
                    .asCoroutineDispatcher().use { cpuDispatcher ->
                        BarcodeExtractionService(
                            reader = reader,
                            properties = BarcodeExampleProperties(),
                            ioDispatcher = ioDispatcher,
                            cpuDispatcher = cpuDispatcher,
                            dimensionProbe = { input ->
                                probeThread.set(Thread.currentThread().name)
                                ImageDimensions(220, 220).takeIf { input.isNotEmpty() }
                            },
                        ).extract(file)
                    }
            }

        readThread.get() shouldBeEqualTo "barcode-io"
        probeThread.get() shouldBeEqualTo "barcode-cpu"
        readerThread.get() shouldBeEqualTo "barcode-cpu"
    }

    private fun service(
        reader: BarcodeReader = ZxingBarcodeReader(),
        properties: BarcodeExampleProperties = BarcodeExampleProperties(),
        dimensionProbe: (ByteArray) -> ImageDimensions? = ::probeImageDimensions,
        metadataDimensionProbe: (ByteArray, Int) -> ImageDimensions? = { bytes, maxBytes ->
            readImageMetadataReport(
                bytes,
                ImageMetadataReadOptions(maxBytes = maxBytes),
            ).dimensions
        },
    ): BarcodeExtractionService =
        BarcodeExtractionService(
            reader = reader,
            properties = properties,
            dimensionProbe = dimensionProbe,
            metadataDimensionProbe = metadataDimensionProbe,
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
