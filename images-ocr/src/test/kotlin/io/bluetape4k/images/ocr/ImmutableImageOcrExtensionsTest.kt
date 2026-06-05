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
}
