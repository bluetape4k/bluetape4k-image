package io.bluetape4k.images.examples.spring.intelligence.service

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.images.barcode.BarcodeResult
import io.bluetape4k.images.detection.DetectionResult
import io.bluetape4k.images.examples.spring.intelligence.config.ImageIntelligenceProperties
import io.bluetape4k.images.examples.spring.intelligence.model.AnalysisResult
import io.bluetape4k.images.ocr.OcrStructuredResult
import io.bluetape4k.workflow.api.WorkContext
import io.bluetape4k.workflow.api.WorkReport
import io.bluetape4k.workflow.coroutines.suspendParallelFlow
import kotlinx.coroutines.sync.Semaphore
import java.io.Serializable

internal data class ImageAnalysisResults(
    val ocr: AnalysisResult<OcrStructuredResult>,
    val detection: AnalysisResult<List<DetectionResult>>,
    val barcode: AnalysisResult<List<BarcodeResult>>,
) : Serializable {
    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}

internal class ImageWorkflowException(
    val reasonCode: String,
    message: String,
) : RuntimeException(message) {
    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}

internal class ImageIntelligenceWorkflow(
    private val ocrProvider: OcrAnalysisProvider,
    private val detectionProvider: DetectionAnalysisProvider,
    private val barcodeProvider: BarcodeAnalysisProvider,
    private val runner: GuardedAnalysisRunner,
    properties: ImageIntelligenceProperties,
) {
    private val ocrTimeout = properties.ocrTimeout
    private val detectionTimeout = properties.detectionTimeout
    private val barcodeTimeout = properties.barcodeTimeout
    private val ocrSemaphore = Semaphore(properties.ocrConcurrency)
    private val detectionSemaphore = Semaphore(properties.detectionConcurrency)
    private val barcodeSemaphore = Semaphore(properties.barcodeConcurrency)

    suspend fun analyze(image: ImmutableImage): ImageAnalysisResults {
        val context = WorkContext()
        val flow = suspendParallelFlow("image-intelligence-analysis") {
            execute("ocr") { workContext ->
                workContext[OCR_RESULT] = runner.run(
                    provider = ocrProvider.id,
                    timeout = ocrTimeout,
                    semaphore = ocrSemaphore,
                    isEmpty = { it.text.isBlank() },
                ) {
                    ocrProvider.analyze(image)
                }
                WorkReport.success(workContext)
            }
            execute("detection") { workContext ->
                workContext[DETECTION_RESULT] = runner.run(
                    provider = detectionProvider.id,
                    timeout = detectionTimeout,
                    semaphore = detectionSemaphore,
                    isEmpty = List<DetectionResult>::isEmpty,
                ) {
                    detectionProvider.analyze(image)
                }
                WorkReport.success(workContext)
            }
            execute("barcode") { workContext ->
                workContext[BARCODE_RESULT] = runner.run(
                    provider = barcodeProvider.id,
                    timeout = barcodeTimeout,
                    semaphore = barcodeSemaphore,
                    isEmpty = List<BarcodeResult>::isEmpty,
                ) {
                    barcodeProvider.analyze(image)
                }
                WorkReport.success(workContext)
            }
        }

        val report = flow.execute(context)
        if (report !is WorkReport.Success) {
            throw ImageWorkflowException(
                reasonCode = "workflow_failed",
                message = "Image analysis workflow did not complete successfully.",
            )
        }
        return resultsFrom(report.context)
    }

    companion object {
        internal const val OCR_RESULT: String = "analysis.ocr"
        internal const val DETECTION_RESULT: String = "analysis.detection"
        internal const val BARCODE_RESULT: String = "analysis.barcode"

        internal fun resultsFrom(context: WorkContext): ImageAnalysisResults =
            ImageAnalysisResults(
                ocr = context.requireResult(OCR_RESULT),
                detection = context.requireResult(DETECTION_RESULT),
                barcode = context.requireResult(BARCODE_RESULT),
            )

        private inline fun <reified T : Any> WorkContext.requireResult(key: String): T =
            this[key]
                ?: throw ImageWorkflowException(
                    reasonCode = "missing_workflow_result",
                    message = "Workflow result is missing for key=$key.",
                )
    }
}
