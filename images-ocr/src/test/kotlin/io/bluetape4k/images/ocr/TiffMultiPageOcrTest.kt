package io.bluetape4k.images.ocr

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.images.IIORegistryUtils
import io.bluetape4k.images.coroutines.SuspendTiffMultiPageWriter
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.imageio.ImageReadParam
import javax.imageio.ImageTypeSpecifier
import javax.imageio.ImageReader
import javax.imageio.metadata.IIOMetadata
import javax.imageio.stream.ImageInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class TiffMultiPageOcrTest {

    @Test
    fun `recognize aggregates pages in TIFF order and remaps structured indices`() {
        val engine = RecordingStructuredEngine()
        val options = OcrOptions(structuredDetail = OcrStructuredDetail.LINE)
        val result = TiffMultiPageOcr(engine).recognize(threePageTiff(), options)

        result.text shouldBeEqualTo "page-0\n\npage-1\n\npage-2"
        result.options shouldBeEqualTo options
        result.pages.map(OcrPage::pageIndex) shouldBeEqualTo listOf(0, 1, 2)
        result.blocks.map(OcrTextBlock::pageIndex) shouldBeEqualTo listOf(0, 1, 2)
        result.blocks.forEach {
            it.confidence.shouldBeNull()
            it.boundingBox.shouldBeNull()
        }
        engine.calls.map { it.first } shouldBeEqualTo listOf(640, 640, 640)
        engine.calls.forEach { it.second shouldBeEqualTo options }
    }

    @Test
    fun `preflight rejects late total pixel overflow before engine`() {
        val engine = RecordingStructuredEngine()
        val pagePixels = 640L * 160L

        val error = assertFailsWith<TiffMultiPageOcrValidationException> {
            TiffMultiPageOcr(engine).recognize(
                threePageTiff(),
                limits = TiffMultiPageOcrLimits(maxTotalPixels = pagePixels * 2),
            )
        }

        error.reason shouldBeEqualTo TiffMultiPageOcrFailureReason.TOTAL_PIXELS_LIMIT_EXCEEDED
        error.pageIndex shouldBeEqualTo 2
        engine.calls.size shouldBeEqualTo 0
    }

    @Test
    fun `result budget rejects cumulative text and entries before returning aggregate`() {
        val textEngine = RecordingStructuredEngine(textAt = { "x" })
        val textError = assertFailsWith<TiffMultiPageOcrValidationException> {
            TiffMultiPageOcr(textEngine).recognize(
                threePageTiff(),
                limits = TiffMultiPageOcrLimits(maxResultTextChars = 4),
            )
        }
        textError.reason shouldBeEqualTo TiffMultiPageOcrFailureReason.RESULT_LIMIT_EXCEEDED
        textError.pageIndex shouldBeEqualTo 2
        textError.message.orEmpty() shouldContain "phase=result"

        val entryEngine = RecordingStructuredEngine(textAt = { "x" })
        val entryError = assertFailsWith<TiffMultiPageOcrValidationException> {
            TiffMultiPageOcr(entryEngine).recognize(
                threePageTiff(),
                limits = TiffMultiPageOcrLimits(maxResultEntries = 4),
            )
        }
        entryError.reason shouldBeEqualTo TiffMultiPageOcrFailureReason.RESULT_LIMIT_EXCEEDED
        entryError.pageIndex shouldBeEqualTo 2
    }

    @Test
    fun `real TwelveMonkeys reader enforces metadata budget before engine`() {
        val engine = RecordingStructuredEngine()
        val error = assertFailsWith<TiffMultiPageOcrValidationException> {
            TiffMultiPageOcr(engine).recognize(
                threePageTiff(),
                limits = TiffMultiPageOcrLimits(maxMetadataBytes = 8),
            )
        }

        error.reason shouldBeEqualTo TiffMultiPageOcrFailureReason.METADATA_LIMIT_EXCEEDED
        engine.calls.size shouldBeEqualTo 0
    }

    @Test
    fun `reader and metadata reason matrix preserves page index`() {
        val engine = RecordingStructuredEngine()

        val noReader = assertFailsWith<TiffMultiPageOcrValidationException> {
            TiffMultiPageOcr.withFactories(
                engine,
                fixedInputFactory(),
                object : TiffImageReaderFactory {
                    override fun open(stream: ImageInputStream): ImageReader =
                        throw TiffMultiPageOcrValidationException(
                            TiffMultiPageOcrFailureReason.READER_UNAVAILABLE,
                            null,
                            "reader unavailable",
                        )
                },
            ).recognize(byteArrayOf(1))
        }
        noReader.reason shouldBeEqualTo TiffMultiPageOcrFailureReason.READER_UNAVAILABLE
        noReader.pageIndex shouldBeEqualTo null

        val unsupported = assertFailsWith<TiffMultiPageOcrValidationException> {
            TiffMultiPageOcr.withFactories(
                engine,
                fixedInputFactory(),
                object : TiffImageReaderFactory {
                    override fun open(stream: ImageInputStream): ImageReader =
                        throw TiffMultiPageOcrValidationException(
                            TiffMultiPageOcrFailureReason.UNSUPPORTED_FORMAT,
                            null,
                            "unsupported format",
                        )
                },
            ).recognize(byteArrayOf(1))
        }
        unsupported.reason shouldBeEqualTo TiffMultiPageOcrFailureReason.UNSUPPORTED_FORMAT
        unsupported.pageIndex shouldBeEqualTo null

        val unknownPages = assertFailsWith<TiffMultiPageOcrValidationException> {
            stubOcr(StubImageReader(pageCount = -1)).recognize(byteArrayOf(1))
        }
        unknownPages.reason shouldBeEqualTo TiffMultiPageOcrFailureReason.PAGE_COUNT_UNKNOWN
        unknownPages.pageIndex shouldBeEqualTo null

        val unknownPageCountCause = IllegalStateException("/secret/metadata")
        val unknownPageCount = assertFailsWith<TiffMultiPageOcrValidationException> {
            stubOcr(
                StubImageReader(
                    pageCount = 1,
                    pageCountFailure = unknownPageCountCause,
                ),
            ).recognize(byteArrayOf(1))
        }
        unknownPageCount.reason shouldBeEqualTo TiffMultiPageOcrFailureReason.PAGE_COUNT_UNKNOWN
        unknownPageCount.pageIndex shouldBeEqualTo null
        unknownPageCount.cause shouldBeEqualTo unknownPageCountCause
        unknownPageCount.message.orEmpty() shouldContain "phase=metadata"
        unknownPageCount.message.orEmpty().contains("/secret") shouldBeEqualTo false

        val metadataCancellation = CancellationException("metadata cancelled")
        assertFailsWith<CancellationException> {
            stubOcr(
                StubImageReader(
                    pageCount = 1,
                    pageCountFailure = metadataCancellation,
                ),
            ).recognize(byteArrayOf(1))
        }

        val unknownReaderCause = IllegalStateException("/secret/reader")
        val unknownReader = assertFailsWith<TiffMultiPageOcrValidationException> {
            TiffMultiPageOcr.withFactories(
                engine,
                fixedInputFactory(),
                object : TiffImageReaderFactory {
                    override fun open(stream: ImageInputStream): ImageReader = throw unknownReaderCause
                },
            ).recognize(byteArrayOf(1))
        }
        unknownReader.reason shouldBeEqualTo TiffMultiPageOcrFailureReason.READER_UNAVAILABLE
        unknownReader.pageIndex shouldBeEqualTo null
        unknownReader.cause shouldBeEqualTo unknownReaderCause
        unknownReader.message.orEmpty() shouldContain "phase=reader"
        unknownReader.message.orEmpty().contains("/secret") shouldBeEqualTo false

        val readerCancellation = CancellationException("reader cancelled")
        assertFailsWith<CancellationException> {
            TiffMultiPageOcr.withFactories(
                engine,
                fixedInputFactory(),
                object : TiffImageReaderFactory {
                    override fun open(stream: ImageInputStream): ImageReader = throw readerCancellation
                },
            ).recognize(byteArrayOf(1))
        }

        val setInputCause = IllegalStateException("/secret/set-input")
        val setInputReader = StubImageReader(pageCount = 1, setInputFailure = setInputCause)
        val setInputError = assertFailsWith<TiffMultiPageOcrValidationException> {
            TiffMultiPageOcr.withFactories(
                engine,
                fixedInputFactory(),
                object : TiffImageReaderFactory {
                    override fun open(stream: ImageInputStream): ImageReader = setInputReader
                },
            ).recognize(byteArrayOf(1))
        }
        setInputError.reason shouldBeEqualTo TiffMultiPageOcrFailureReason.READER_UNAVAILABLE
        setInputError.cause shouldBeEqualTo setInputCause
        setInputReader.disposed shouldBeEqualTo true

        val invalidDimensions = assertFailsWith<TiffMultiPageOcrValidationException> {
            stubOcr(StubImageReader(pageCount = 1, width = 0)).recognize(byteArrayOf(1))
        }
        invalidDimensions.reason shouldBeEqualTo TiffMultiPageOcrFailureReason.DIMENSIONS_UNAVAILABLE
        invalidDimensions.pageIndex shouldBeEqualTo 0

        val decodeCause = IllegalStateException("/secret/decode")
        val decodeError = assertFailsWith<TiffMultiPageOcrException> {
            stubOcr(StubImageReader(pageCount = 1, readFailure = decodeCause)).recognize(byteArrayOf(1))
        }
        decodeError.reason shouldBeEqualTo TiffMultiPageOcrFailureReason.DECODE_FAILED
        decodeError.pageIndex shouldBeEqualTo 0
        decodeError.cause shouldBeEqualTo decodeCause
        decodeError.message.orEmpty() shouldContain "phase=decode"
        decodeError.message.orEmpty().contains("/secret") shouldBeEqualTo false
    }

    @Test
    fun `unexpected setup failure uses unknown operational reason`() {
        val cause = IllegalStateException("/secret/unexpected")
        val error = assertFailsWith<TiffMultiPageOcrException> {
            TiffMultiPageOcr.withFactories(
                RecordingStructuredEngine(),
                fixedInputFactory(allowPayloadFailure = cause),
                object : TiffImageReaderFactory {
                    override fun open(stream: ImageInputStream): ImageReader = StubImageReader(pageCount = 1)
                },
            ).recognize(byteArrayOf(1))
        }

        error.reason shouldBeEqualTo TiffMultiPageOcrFailureReason.UNKNOWN
        error.pageIndex shouldBeEqualTo null
        error.cause shouldBeEqualTo cause
        error.message.orEmpty() shouldContain "phase=unknown"
        error.message.orEmpty().contains("/secret") shouldBeEqualTo false
    }

    @Test
    fun `limits and encoded budget fail before engine`() {
        val cases = listOf(
            TiffMultiPageOcrLimits(maxEncodedBytes = 1L) to TiffMultiPageOcrFailureReason.INPUT_TOO_LARGE,
            TiffMultiPageOcrLimits(maxPages = 2) to TiffMultiPageOcrFailureReason.PAGE_LIMIT_EXCEEDED,
            TiffMultiPageOcrLimits(maxDecodedSide = 100) to TiffMultiPageOcrFailureReason.SIDE_LIMIT_EXCEEDED,
            TiffMultiPageOcrLimits(maxPixelsPerPage = 100L) to TiffMultiPageOcrFailureReason.PIXELS_PER_PAGE_LIMIT_EXCEEDED,
        )

        cases.forEach { (limits, reason) ->
            val engine = RecordingStructuredEngine()
            val payload = threePageTiff()
            val error = assertFailsWith<TiffMultiPageOcrValidationException> {
                TiffMultiPageOcr(engine).recognize(payload, limits = limits)
            }
            error.reason shouldBeEqualTo reason
            engine.calls.size shouldBeEqualTo 0
        }
    }

    @Test
    fun `malformed truncated and GIF inputs map to stable validation reasons`() {
        val tiff = threePageTiff()
        val truncated = tiff.copyOf(64)
        val truncatedError = assertFailsWith<TiffMultiPageOcrValidationException> {
            TiffMultiPageOcr(RecordingStructuredEngine()).recognize(truncated)
        }
        truncatedError.pageIndex shouldBeEqualTo null

        val gif = ByteArrayOutputStream().also {
            ImageIO.write(textImage().awt(), "gif", it)
        }.toByteArray()
        val gifError = assertFailsWith<TiffMultiPageOcrValidationException> {
            TiffMultiPageOcr(RecordingStructuredEngine()).recognize(gif)
        }
        gifError.reason shouldBeEqualTo TiffMultiPageOcrFailureReason.UNSUPPORTED_FORMAT
    }

    @Test
    fun `engine failure is fail fast and never returns partial result`() {
        val engine = RecordingStructuredEngine(
            failAt = 1,
            failureMessage = "/secret/tessdata/private.traineddata payload=classified",
        )

        val error = assertFailsWith<TiffMultiPageOcrException> {
            TiffMultiPageOcr(engine).recognize(threePageTiff())
        }

        error.reason shouldBeEqualTo TiffMultiPageOcrFailureReason.ENGINE_FAILED
        error.pageIndex shouldBeEqualTo 1
        engine.calls.size shouldBeEqualTo 2
        error.message shouldBeEqualTo "TIFF OCR engine failed (phase=engine, pageIndex=1)."
        error.cause shouldBeInstanceOf OcrException::class
    }

    @Test
    fun `cleanup failure is sanitized and suppressed without replacing engine failure`() {
        val inputFactory = object : TiffImageInputFactory {
            override fun open(bytes: ByteArray, maxMetadataBytes: Long): TiffImageInput {
                val source = ByteArrayInputStream(bytes)
                val stream = requireNotNull(ImageIO.createImageInputStream(source))
                return object : TiffImageInput {
                    override val stream = stream

                    override fun allowPayloadReads() = Unit

                    override fun close() {
                        stream.close()
                        throw IOException("/secret/storage/private-input.tiff")
                    }
                }
            }
        }
        val readerFactory = object : TiffImageReaderFactory {
            override fun open(stream: javax.imageio.stream.ImageInputStream): javax.imageio.ImageReader {
                IIORegistryUtils.registerApplicationClasspathSpis()
                stream.seek(0)
                val reader = ImageIO.getImageReaders(stream).asSequence().first {
                    it.formatName.equals("tiff", ignoreCase = true)
                }
                stream.seek(0)
                return reader
            }
        }
        val engine = RecordingStructuredEngine(
            failAt = 0,
            failureMessage = "/secret/tessdata/private.traineddata",
        )

        val error = assertFailsWith<TiffMultiPageOcrException> {
            TiffMultiPageOcr.withFactories(engine, inputFactory, readerFactory).recognize(threePageTiff())
        }

        error.reason shouldBeEqualTo TiffMultiPageOcrFailureReason.ENGINE_FAILED
        error.message shouldBeEqualTo "TIFF OCR engine failed (phase=engine, pageIndex=0)."
        error.cause shouldBeInstanceOf OcrException::class
        error.suppressed.size shouldBeEqualTo 1
        error.suppressed.single().message shouldBeEqualTo "TIFF OCR resource cleanup failed"
    }

    @Test
    fun `suspend cancellation between pages propagates`() = runTest {
        lateinit var deferred: kotlinx.coroutines.Deferred<OcrStructuredResult>
        val engine = RecordingStructuredEngine(onCall = { index ->
            if (index == 0) deferred.cancel()
        })
        val ocr = TiffMultiPageOcr(engine)

        deferred = async { ocr.suspendRecognize(threePageTiff()) }

        assertFailsWith<CancellationException> { deferred.await() }
        engine.calls.size shouldBeEqualTo 1
    }

    private fun threePageTiff(): ByteArray = runBlocking {
        val output = ByteArrayOutputStream()
        SuspendTiffMultiPageWriter().suspendWrite(
            listOf(textImage("PAGE ONE"), textImage("PAGE TWO"), textImage("PAGE THREE")),
            output,
        )
        output.toByteArray()
    }

    private fun stubOcr(reader: ImageReader): TiffMultiPageOcr =
        TiffMultiPageOcr.withFactories(
            RecordingStructuredEngine(),
            fixedInputFactory(),
            object : TiffImageReaderFactory {
                override fun open(stream: ImageInputStream): ImageReader = reader
            },
        )

    private fun fixedInputFactory(allowPayloadFailure: RuntimeException? = null): TiffImageInputFactory = object : TiffImageInputFactory {
        override fun open(bytes: ByteArray, maxMetadataBytes: Long): TiffImageInput {
            val source = ByteArrayInputStream(byteArrayOf(0))
            val imageInput = requireNotNull(ImageIO.createImageInputStream(source))
            return object : TiffImageInput {
                override val stream: ImageInputStream = imageInput

                override fun allowPayloadReads() {
                    allowPayloadFailure?.let { throw it }
                }

                override fun close() = imageInput.close()
            }
        }
    }

    private class StubImageReader(
        private val pageCount: Int,
        private val width: Int = 1,
        private val height: Int = 1,
        private val pageCountFailure: RuntimeException? = null,
        private val readFailure: RuntimeException? = null,
        private val setInputFailure: RuntimeException? = null,
    ) : ImageReader(null) {
        var disposed: Boolean = false

        override fun getNumImages(allowSearch: Boolean): Int = pageCountFailure?.let { throw it } ?: pageCount

        override fun setInput(input: Any?, seekForwardOnly: Boolean, ignoreMetadata: Boolean) {
            setInputFailure?.let { throw it }
            super.setInput(input, seekForwardOnly, ignoreMetadata)
        }

        override fun dispose() {
            disposed = true
            super.dispose()
        }

        override fun getWidth(imageIndex: Int): Int = width

        override fun getHeight(imageIndex: Int): Int = height

        override fun getImageTypes(imageIndex: Int): MutableIterator<ImageTypeSpecifier> =
            mutableListOf(ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_RGB)).iterator()

        override fun getStreamMetadata(): IIOMetadata? = null

        override fun getImageMetadata(imageIndex: Int): IIOMetadata? = null

        override fun read(imageIndex: Int, param: ImageReadParam?): BufferedImage {
            readFailure?.let { throw it }
            return BufferedImage(width.coerceAtLeast(1), height.coerceAtLeast(1), BufferedImage.TYPE_INT_RGB)
        }
    }

    private class RecordingStructuredEngine(
        private val failAt: Int? = null,
        private val onCall: ((Int) -> Unit)? = null,
        private val textAt: (Int) -> String = { index -> "page-$index" },
        private val failureMessage: String = "fake engine failure",
    ) : StructuredOcrEngine {
        val calls = mutableListOf<Pair<Int, OcrOptions>>()

        override fun recognize(image: ImmutableImage, options: OcrOptions): OcrResult =
            error("plain OCR is not used by the multipage contract")

        override fun recognizeStructured(image: ImmutableImage, options: OcrOptions): OcrStructuredResult {
            val index = calls.size
            calls += image.width to options
            onCall?.invoke(index)
            if (failAt == index) {
                throw OcrException(failureMessage)
            }
            val text = textAt(index)
            return OcrStructuredResult(
                text = text,
                options = options,
                pages = listOf(OcrPage(pageIndex = 0, text = text)),
                blocks = listOf(OcrTextBlock(pageIndex = 0, text = text)),
            )
        }
    }
}
