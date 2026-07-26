package io.bluetape4k.images.examples.spring.intelligence.service

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.images.barcode.BarcodeFormat
import io.bluetape4k.images.barcode.BarcodeResult
import io.bluetape4k.images.barcode.zxing.ZxingBarcodeReader
import io.bluetape4k.images.detection.DetectionCategory
import io.bluetape4k.images.examples.spring.intelligence.model.AnalysisResult
import io.bluetape4k.images.examples.spring.intelligence.support.VISITOR_PASS_PAYLOAD
import io.bluetape4k.images.examples.spring.intelligence.support.qrImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration

class ImageAnalysisProvidersTest {

    private val runner = GuardedAnalysisRunner()
    private val image = ImmutableImage.create(120, 80)

    @Test
    fun `disabled providers become unavailable outcomes`() = runTest {
        val ocr = DisabledOcrAnalysisProvider()
        val detector = DisabledDetectionAnalysisProvider()

        val ocrResult = runner.run<io.bluetape4k.images.ocr.OcrStructuredResult>(
            provider = ocr.id,
            timeout = Duration.ofSeconds(1),
            semaphore = Semaphore(1),
        ) {
            ocr.analyze(image)
        }
        val detectorResult = runner.run<List<io.bluetape4k.images.detection.DetectionResult>>(
            provider = detector.id,
            timeout = Duration.ofSeconds(1),
            semaphore = Semaphore(1),
        ) {
            detector.analyze(image)
        }

        (ocrResult as AnalysisResult.Unavailable).reasonCode shouldBeEqualTo "provider_not_configured"
        (detectorResult as AnalysisResult.Unavailable).reasonCode shouldBeEqualTo "provider_not_configured"
    }

    @Test
    fun `fixture providers return structured OCR and one face fact`() = runTest {
        val ocr = FixtureOcrAnalysisProvider()
        val detector = FixtureDetectionAnalysisProvider()

        val ocrResult = ocr.analyze(image)
        val detections = detector.analyze(image)

        ocrResult.text.isNotBlank().shouldBeTrue()
        ocrResult.pages.size shouldBeEqualTo 1
        detections.size shouldBeEqualTo 1
        detections.single().category shouldBeEqualTo DetectionCategory.FACE
    }

    @Test
    fun `blank image has no barcode`() = runTest {
        val provider = ZxingBarcodeAnalysisProvider(
            reader = ZxingBarcodeReader(),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = runner.run(
            provider = provider.id,
            timeout = Duration.ofSeconds(1),
            semaphore = Semaphore(1),
            isEmpty = List<*>::isEmpty,
        ) {
            provider.analyze(image)
        }

        result shouldBeInstanceOf AnalysisResult.Empty::class
    }

    @Test
    fun `generated visitor QR is extracted by the real ZXing provider`() = runTest {
        val provider = ZxingBarcodeAnalysisProvider(
            reader = ZxingBarcodeReader(),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = provider.analyze(qrImage()).single()

        result.text shouldBeEqualTo VISITOR_PASS_PAYLOAD
        result.format shouldBeEqualTo BarcodeFormat.QR_CODE
    }

    @Test
    fun `provider exception is sanitized while cancellation propagates`() = runTest {
        val failing = object : BarcodeAnalysisProvider {
            override val id: String = "failing"

            override suspend fun analyze(image: ImmutableImage) =
                error("secret-native-path=/opt/private")
        }
        val failed = runner.run(
            provider = failing.id,
            timeout = Duration.ofSeconds(1),
            semaphore = Semaphore(1),
        ) {
            failing.analyze(image)
        }

        (failed as AnalysisResult.Failed).reasonCode shouldBeEqualTo "provider_failure"

        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val cancelling = object : BarcodeAnalysisProvider {
            override val id: String = "cancelling"

            override suspend fun analyze(image: ImmutableImage): List<BarcodeResult> {
                started.complete(Unit)
                delay(Long.MAX_VALUE)
                return emptyList()
            }
        }
        val job = launch {
            runner.run(
                provider = cancelling.id,
                timeout = Duration.ofSeconds(10),
                semaphore = Semaphore(1),
            ) {
                cancelling.analyze(image)
            }
        }
        started.await()
        job.cancel(CancellationException("client disconnected"))
        job.cancelAndJoin()

        job.isCancelled.shouldBeTrue()
    }
}
