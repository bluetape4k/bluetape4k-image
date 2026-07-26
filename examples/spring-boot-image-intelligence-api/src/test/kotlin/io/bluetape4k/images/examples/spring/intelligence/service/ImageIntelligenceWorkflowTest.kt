package io.bluetape4k.images.examples.spring.intelligence.service

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.images.barcode.BarcodeResult
import io.bluetape4k.images.detection.DetectionResult
import io.bluetape4k.images.examples.spring.intelligence.config.ImageIntelligenceProperties
import io.bluetape4k.images.examples.spring.intelligence.model.AnalysisResult
import io.bluetape4k.images.ocr.OcrStructuredResult
import io.bluetape4k.workflow.api.WorkContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class ImageIntelligenceWorkflowTest {

    private val image = ImmutableImage.create(80, 60)

    @Test
    fun `three analysis lanes overlap and preserve unique outcomes`() = runTest {
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
        val ocr = controlledOcr(active, maxActive)
        val detection = controlledDetection(active, maxActive)
        val barcode = controlledBarcode(active, maxActive)

        val results = workflow(ocr, detection, barcode).analyze(image)

        maxActive.get() shouldBeEqualTo 3
        results.ocr shouldBeInstanceOf AnalysisResult.Completed::class
        results.detection shouldBeInstanceOf AnalysisResult.Empty::class
        results.barcode shouldBeInstanceOf AnalysisResult.Empty::class
    }

    @Test
    fun `provider failure remains data and preserves sibling results`() = runTest {
        val failingOcr = object : OcrAnalysisProvider {
            override val id: String = "broken-ocr"
            override suspend fun analyze(image: ImmutableImage): OcrStructuredResult =
                error("private-provider-detail")
        }

        val results = workflow(
            ocr = failingOcr,
            detection = FixtureDetectionAnalysisProvider(),
            barcode = controlledBarcode(),
        ).analyze(image)

        results.ocr.shouldBeInstanceOf<AnalysisResult.Failed>()
            .reasonCode shouldBeEqualTo "provider_failure"
        results.detection shouldBeInstanceOf AnalysisResult.Completed::class
        results.barcode shouldBeInstanceOf AnalysisResult.Empty::class
    }

    @Test
    fun `missing workflow key becomes a stable orchestration defect`() {
        val exception = assertFailsWith<ImageWorkflowException> {
            ImageIntelligenceWorkflow.resultsFrom(WorkContext())
        }

        exception.reasonCode shouldBeEqualTo "missing_workflow_result"
        exception.message?.contains("analysis.ocr").shouldBeTrue()
    }

    @Test
    fun `external cancellation reaches every active provider`() = runTest {
        val started = List(3) { CompletableDeferred<Unit>() }
        val cancelled = List(3) { CompletableDeferred<Unit>() }
        val workflow = workflow(
            ocr = suspendingOcr(started[0], cancelled[0]),
            detection = suspendingDetection(started[1], cancelled[1]),
            barcode = suspendingBarcode(started[2], cancelled[2]),
        )
        val job = launch { workflow.analyze(image) }
        started.forEach { it.await() }

        job.cancel()
        job.cancelAndJoin()

        cancelled.forEach { it.await() }
        job.isCancelled.shouldBeTrue()
    }

    private fun workflow(
        ocr: OcrAnalysisProvider,
        detection: DetectionAnalysisProvider,
        barcode: BarcodeAnalysisProvider,
    ): ImageIntelligenceWorkflow =
        ImageIntelligenceWorkflow(
            ocrProvider = ocr,
            detectionProvider = detection,
            barcodeProvider = barcode,
            runner = GuardedAnalysisRunner(),
            properties = ImageIntelligenceProperties(
                ocrTimeout = Duration.ofSeconds(5),
                detectionTimeout = Duration.ofSeconds(5),
                barcodeTimeout = Duration.ofSeconds(5),
            ),
        )

    private fun controlledOcr(
        active: AtomicInteger = AtomicInteger(),
        maxActive: AtomicInteger = AtomicInteger(),
    ) = object : OcrAnalysisProvider {
        override val id: String = "controlled-ocr"
        override suspend fun analyze(image: ImmutableImage): OcrStructuredResult {
            track(active, maxActive)
            return FixtureOcrAnalysisProvider().analyze(image)
        }
    }

    private fun controlledDetection(
        active: AtomicInteger = AtomicInteger(),
        maxActive: AtomicInteger = AtomicInteger(),
    ) = object : DetectionAnalysisProvider {
        override val id: String = "controlled-detection"
        override suspend fun analyze(image: ImmutableImage): List<DetectionResult> {
            track(active, maxActive)
            return emptyList()
        }
    }

    private fun controlledBarcode(
        active: AtomicInteger = AtomicInteger(),
        maxActive: AtomicInteger = AtomicInteger(),
    ) = object : BarcodeAnalysisProvider {
        override val id: String = "controlled-barcode"
        override suspend fun analyze(image: ImmutableImage): List<BarcodeResult> {
            track(active, maxActive)
            return emptyList()
        }
    }

    private suspend fun track(active: AtomicInteger, maxActive: AtomicInteger) {
        val current = active.incrementAndGet()
        maxActive.accumulateAndGet(current, ::maxOf)
        try {
            delay(100)
        } finally {
            active.decrementAndGet()
        }
    }

    private fun suspendingOcr(started: CompletableDeferred<Unit>, cancelled: CompletableDeferred<Unit>) =
        object : OcrAnalysisProvider {
            override val id: String = "suspending-ocr"
            override suspend fun analyze(image: ImmutableImage): OcrStructuredResult {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
        }

    private fun suspendingDetection(started: CompletableDeferred<Unit>, cancelled: CompletableDeferred<Unit>) =
        object : DetectionAnalysisProvider {
            override val id: String = "suspending-detection"
            override suspend fun analyze(image: ImmutableImage): List<DetectionResult> {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
        }

    private fun suspendingBarcode(started: CompletableDeferred<Unit>, cancelled: CompletableDeferred<Unit>) =
        object : BarcodeAnalysisProvider {
            override val id: String = "suspending-barcode"
            override suspend fun analyze(image: ImmutableImage): List<BarcodeResult> {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
        }
}
