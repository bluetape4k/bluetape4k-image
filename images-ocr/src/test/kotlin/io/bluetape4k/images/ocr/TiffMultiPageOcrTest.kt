package io.bluetape4k.images.ocr

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.images.coroutines.SuspendTiffMultiPageWriter
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
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
    fun `limits and encoded budget fail before engine`() {
        val cases = listOf(
            TiffMultiPageOcrLimits(maxEncodedBytes = 1L) to TiffMultiPageOcrFailureReason.INPUT_TOO_LARGE,
            TiffMultiPageOcrLimits(maxPages = 2) to TiffMultiPageOcrFailureReason.PAGE_LIMIT_EXCEEDED,
            TiffMultiPageOcrLimits(maxDecodedSide = 100) to TiffMultiPageOcrFailureReason.SIDE_LIMIT_EXCEEDED,
            TiffMultiPageOcrLimits(maxPixelsPerPage = 100L) to TiffMultiPageOcrFailureReason.PIXELS_PER_PAGE_LIMIT_EXCEEDED,
        )

        cases.forEach { (limits, reason) ->
            val engine = RecordingStructuredEngine()
            val error = assertFailsWith<TiffMultiPageOcrValidationException> {
                TiffMultiPageOcr(engine).recognize(threePageTiff(), limits = limits)
            }
            error.reason shouldBeEqualTo reason
            engine.calls.size shouldBeEqualTo 0
        }
    }

    @Test
    fun `malformed truncated and GIF inputs map to stable validation reasons`() {
        val tiff = threePageTiff()
        val truncated = tiff.copyOf(tiff.size / 2)
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
        val engine = RecordingStructuredEngine(failAt = 1)

        val error = assertFailsWith<TiffMultiPageOcrException> {
            TiffMultiPageOcr(engine).recognize(threePageTiff())
        }

        error.reason shouldBeEqualTo TiffMultiPageOcrFailureReason.ENGINE_FAILED
        error.pageIndex shouldBeEqualTo 1
        engine.calls.size shouldBeEqualTo 2
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

    private class RecordingStructuredEngine(
        private val failAt: Int? = null,
        private val onCall: ((Int) -> Unit)? = null,
    ) : StructuredOcrEngine {
        val calls = mutableListOf<Pair<Int, OcrOptions>>()

        override fun recognize(image: ImmutableImage, options: OcrOptions): OcrResult =
            error("plain OCR is not used by the multipage contract")

        override fun recognizeStructured(image: ImmutableImage, options: OcrOptions): OcrStructuredResult {
            val index = calls.size
            calls += image.width to options
            onCall?.invoke(index)
            if (failAt == index) {
                throw OcrException("fake engine failure")
            }
            val text = "page-$index"
            return OcrStructuredResult(
                text = text,
                options = options,
                pages = listOf(OcrPage(pageIndex = 0, text = text)),
                blocks = listOf(OcrTextBlock(pageIndex = 0, text = text)),
            )
        }
    }
}
