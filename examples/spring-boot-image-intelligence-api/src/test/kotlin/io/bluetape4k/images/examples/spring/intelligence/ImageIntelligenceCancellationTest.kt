package io.bluetape4k.images.examples.spring.intelligence

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.images.barcode.BarcodeResult
import io.bluetape4k.images.detection.DetectionResult
import io.bluetape4k.images.examples.spring.intelligence.config.ImageIntelligenceProperties
import io.bluetape4k.images.examples.spring.intelligence.model.AnalysisResult
import io.bluetape4k.images.examples.spring.intelligence.service.BarcodeAnalysisProvider
import io.bluetape4k.images.examples.spring.intelligence.service.DetectionAnalysisProvider
import io.bluetape4k.images.examples.spring.intelligence.service.FixtureOcrAnalysisProvider
import io.bluetape4k.images.examples.spring.intelligence.service.GuardedAnalysisRunner
import io.bluetape4k.images.examples.spring.intelligence.service.ImageIntelligenceWorkflow
import io.bluetape4k.images.examples.spring.intelligence.service.OcrAnalysisProvider
import io.bluetape4k.images.ocr.OcrStructuredResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class ImageIntelligenceCancellationTest {

    private val image = ImmutableImage.create(80, 60)

    @Test
    fun `cancelled request releases every lane for a subsequent request`() = runTest {
        val started = List(3) { CompletableDeferred<Unit>() }
        val cancelled = List(3) { CompletableDeferred<Unit>() }
        val ocr = RecoveringOcrProvider(started[0], cancelled[0])
        val detection = RecoveringDetectionProvider(started[1], cancelled[1])
        val barcode = RecoveringBarcodeProvider(started[2], cancelled[2])
        val workflow = workflow(ocr, detection, barcode)
        val first = launch { workflow.analyze(image) }
        started.forEach { it.await() }

        first.cancel()
        first.cancelAndJoin()
        cancelled.forEach { it.await() }

        val recovered = workflow.analyze(image)
        first.isCancelled.shouldBeTrue()
        recovered.ocr shouldBeInstanceOf AnalysisResult.Completed::class
        recovered.detection shouldBeInstanceOf AnalysisResult.Empty::class
        recovered.barcode shouldBeInstanceOf AnalysisResult.Empty::class
    }

    @Test
    fun `lane timeout remains data and does not cancel siblings`() = runTest {
        val workflow = workflow(
            ocr = object : OcrAnalysisProvider {
                override val id: String = "slow-ocr"
                override suspend fun analyze(image: ImmutableImage): OcrStructuredResult {
                    delay(200)
                    return FixtureOcrAnalysisProvider().analyze(image)
                }
            },
            detection = object : DetectionAnalysisProvider {
                override val id: String = "empty-detection"
                override suspend fun analyze(image: ImmutableImage): List<DetectionResult> = emptyList()
            },
            barcode = object : BarcodeAnalysisProvider {
                override val id: String = "empty-barcode"
                override suspend fun analyze(image: ImmutableImage): List<BarcodeResult> = emptyList()
            },
            timeout = Duration.ofMillis(100),
        )

        val results = workflow.analyze(image)

        results.ocr shouldBeInstanceOf AnalysisResult.Failed::class
        results.detection shouldBeInstanceOf AnalysisResult.Empty::class
        results.barcode shouldBeInstanceOf AnalysisResult.Empty::class
    }

    private fun workflow(
        ocr: OcrAnalysisProvider,
        detection: DetectionAnalysisProvider,
        barcode: BarcodeAnalysisProvider,
        timeout: Duration = Duration.ofSeconds(5),
    ): ImageIntelligenceWorkflow =
        ImageIntelligenceWorkflow(
            ocrProvider = ocr,
            detectionProvider = detection,
            barcodeProvider = barcode,
            runner = GuardedAnalysisRunner(),
            properties = ImageIntelligenceProperties(
                ocrTimeout = timeout,
                detectionTimeout = timeout,
                barcodeTimeout = timeout,
                ocrConcurrency = 1,
                detectionConcurrency = 1,
                barcodeConcurrency = 1,
            ),
        )

    private class RecoveringOcrProvider(
        private val started: CompletableDeferred<Unit>,
        private val cancelled: CompletableDeferred<Unit>,
    ) : OcrAnalysisProvider {
        private val attempts = AtomicInteger()
        override val id: String = "recovering-ocr"

        override suspend fun analyze(image: ImmutableImage): OcrStructuredResult {
            if (attempts.incrementAndGet() == 1) {
                suspendFirstAttempt(started, cancelled)
            }
            return FixtureOcrAnalysisProvider().analyze(image)
        }
    }

    private class RecoveringDetectionProvider(
        private val started: CompletableDeferred<Unit>,
        private val cancelled: CompletableDeferred<Unit>,
    ) : DetectionAnalysisProvider {
        private val attempts = AtomicInteger()
        override val id: String = "recovering-detection"

        override suspend fun analyze(image: ImmutableImage): List<DetectionResult> {
            if (attempts.incrementAndGet() == 1) {
                suspendFirstAttempt(started, cancelled)
            }
            return emptyList()
        }
    }

    private class RecoveringBarcodeProvider(
        private val started: CompletableDeferred<Unit>,
        private val cancelled: CompletableDeferred<Unit>,
    ) : BarcodeAnalysisProvider {
        private val attempts = AtomicInteger()
        override val id: String = "recovering-barcode"

        override suspend fun analyze(image: ImmutableImage): List<BarcodeResult> {
            if (attempts.incrementAndGet() == 1) {
                suspendFirstAttempt(started, cancelled)
            }
            return emptyList()
        }
    }

    private companion object {
        suspend fun suspendFirstAttempt(
            started: CompletableDeferred<Unit>,
            cancelled: CompletableDeferred<Unit>,
        ): Nothing {
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }
    }
}
