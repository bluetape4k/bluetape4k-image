package io.bluetape4k.images.barcode

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.nio.PngWriter
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BarcodeReaderExtensionsTest {

    private lateinit var calls: AtomicInteger
    private lateinit var recordedOptions: MutableList<BarcodeOptions>
    private lateinit var recordedSizes: MutableList<Pair<Int, Int>>

    @BeforeEach
    fun beforeEach() {
        calls = AtomicInteger()
        recordedOptions = mutableListOf()
        recordedSizes = mutableListOf()
    }

    @Test
    fun `extractBarcodes delegates to supplied reader`() {
        val options = BarcodeOptions(formats = setOf(BarcodeFormat.QR_CODE))
        val reader = recordingReader("hello qr")

        val results = sampleImage().extractBarcodes(reader = reader, options = options)

        results.single().text shouldBeEqualTo "hello qr"
        recordedOptions.single() shouldBeEqualTo options
        recordedSizes.single() shouldBeEqualTo (64 to 32)
        calls.get() shouldBeEqualTo 1
    }

    @Test
    fun `suspendExtractBarcodes delegates on supplied dispatcher`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val reader = recordingReader("suspend qr")

        val deferred = async {
            sampleImage().suspendExtractBarcodes(reader = reader, dispatcher = dispatcher)
        }

        calls.get() shouldBeEqualTo 0
        testScheduler.advanceUntilIdle()
        deferred.await().single().text shouldBeEqualTo "suspend qr"
        calls.get() shouldBeEqualTo 1
    }

    @Test
    fun `suspendExtractBarcodes honors cancellation before reader starts`() = runTest {
        val cancelledJob = Job().apply { cancel() }
        val reader = recordingReader("should not run")

        assertFailsWith<CancellationException> {
            withContext(cancelledJob) {
                sampleImage().suspendExtractBarcodes(reader = reader)
            }
        }
        calls.get() shouldBeEqualTo 0
    }

    @Test
    fun `suspendExtractBarcodes propagates provider cancellation`() = runTest {
        val reader = BarcodeReader { _, _ ->
            calls.incrementAndGet()
            throw CancellationException("provider cancelled")
        }

        assertFailsWith<CancellationException> {
            sampleImage().suspendExtractBarcodes(reader = reader)
        }
        calls.get() shouldBeEqualTo 1
    }

    @Test
    fun `reader input helpers decode and delegate`() {
        val bytes = samplePngBytes()
        val reader = recordingReader("from input")
        val path = Files.createTempFile("barcode-api-", ".png")
        Files.write(path, bytes)

        try {
            reader.readBarcodes(bytes).single().text shouldBeEqualTo "from input"
            reader.readBarcodes(path).single().text shouldBeEqualTo "from input"
            bytes.inputStream().use { input ->
                reader.readBarcodes(input).single().text shouldBeEqualTo "from input"
            }
            bytes.inputStream().source().buffer().use { source ->
                reader.readBarcodes(source).single().text shouldBeEqualTo "from input"
            }
        } finally {
            Files.deleteIfExists(path)
        }

        calls.get() shouldBeEqualTo 4
        recordedSizes shouldBeEqualTo listOf(64 to 32, 64 to 32, 64 to 32, 64 to 32)
    }

    private fun recordingReader(text: String): BarcodeReader =
        BarcodeReader { image, options ->
            calls.incrementAndGet()
            recordedOptions.add(options)
            recordedSizes.add(image.width to image.height)
            listOf(
                BarcodeResult(
                    text = text,
                    format = BarcodeFormat.QR_CODE,
                    provider = BarcodeProviderIdentity(name = "fake-reader"),
                ),
            )
        }

    private fun sampleImage(): ImmutableImage =
        ImmutableImage.create(64, 32)

    private fun samplePngBytes(): ByteArray =
        sampleImage().bytes(PngWriter.NoCompression)
}
