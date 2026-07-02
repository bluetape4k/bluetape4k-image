package io.bluetape4k.images.ocr

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test

class ImmutableImageOcrExtensionsTest {

    @Test
    fun `extractText delegates to supplied engine`() {
        val calls = AtomicInteger()
        val options = OcrOptions(languages = listOf("eng", "kor"))
        val engine = OcrEngine { image, actualOptions ->
            calls.incrementAndGet()
            image.width shouldBeEqualTo 640
            actualOptions shouldBeEqualTo options
            OcrResult(text = "hello ocr", options = actualOptions)
        }

        val text = textImage().extractText(options, engine)

        text shouldBeEqualTo "hello ocr"
        calls.get() shouldBeEqualTo 1
    }

    @Test
    fun `extractOcr delegates to supplied structured engine`() {
        val calls = AtomicInteger()
        val options = OcrOptions(structuredDetail = OcrStructuredDetail.LINE)
        val engine = RecordingStructuredOcrEngine(
            calls = calls,
            result = OcrStructuredResult(
                text = "structured ocr",
                options = options,
                pages = listOf(OcrPage(pageIndex = 0, text = "structured ocr")),
                lines = listOf(OcrTextLine(pageIndex = 0, text = "structured ocr")),
            ),
        )

        val result = textImage().extractOcr(options, engine)

        result.text shouldBeEqualTo "structured ocr"
        result.lines.size shouldBeEqualTo 1
        calls.get() shouldBeEqualTo 1
    }

    @Test
    fun `suspendExtractText delegates on the supplied dispatcher`() = runTest {
        val calls = AtomicInteger()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = OcrEngine { _, options ->
            calls.incrementAndGet()
            OcrResult(text = "suspend ocr", options = options)
        }

        val deferred = this.async {
            textImage().suspendExtractText(engine = engine, dispatcher = dispatcher)
        }

        calls.get() shouldBeEqualTo 0
        testScheduler.advanceUntilIdle()
        deferred.await() shouldBeEqualTo "suspend ocr"
        calls.get() shouldBeEqualTo 1
    }

    @Test
    fun `suspendExtractText honors cancellation before engine starts`() = runTest {
        val calls = AtomicInteger()
        val cancelledJob = Job().apply { cancel() }
        val engine = OcrEngine { _, options ->
            calls.incrementAndGet()
            OcrResult(text = "should not run", options = options)
        }

        assertFailsWith<CancellationException> {
            withContext(cancelledJob) {
                textImage().suspendExtractText(engine = engine)
            }
        }
        calls.get() shouldBeEqualTo 0
    }

    @Test
    fun `suspendExtractOcr delegates on the supplied dispatcher`() = runTest {
        val calls = AtomicInteger()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val options = OcrOptions(structuredDetail = OcrStructuredDetail.WORD)
        val engine = RecordingStructuredOcrEngine(
            calls = calls,
            result = OcrStructuredResult(
                text = "suspend structured ocr",
                options = options,
                pages = listOf(OcrPage(pageIndex = 0, text = "suspend structured ocr")),
                words = listOf(OcrWord(pageIndex = 0, text = "ocr")),
            ),
        )

        val deferred = this.async {
            textImage().suspendExtractOcr(options = options, engine = engine, dispatcher = dispatcher)
        }

        calls.get() shouldBeEqualTo 0
        testScheduler.advanceUntilIdle()
        deferred.await().words.size shouldBeEqualTo 1
        calls.get() shouldBeEqualTo 1
    }

    private class RecordingStructuredOcrEngine(
        private val calls: AtomicInteger,
        private val result: OcrStructuredResult,
    ): StructuredOcrEngine {

        override fun recognize(image: com.sksamuel.scrimage.ImmutableImage, options: OcrOptions): OcrResult =
            OcrResult(text = result.text, options = options)

        override fun recognizeStructured(
            image: com.sksamuel.scrimage.ImmutableImage,
            options: OcrOptions,
        ): OcrStructuredResult {
            calls.incrementAndGet()
            image.width shouldBeEqualTo 640
            options shouldBeEqualTo result.options
            return result
        }
    }
}
