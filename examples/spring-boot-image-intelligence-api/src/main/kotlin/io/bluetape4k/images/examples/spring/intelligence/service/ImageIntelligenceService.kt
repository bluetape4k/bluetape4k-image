package io.bluetape4k.images.examples.spring.intelligence.service

import io.bluetape4k.images.examples.spring.intelligence.model.AnalysisResult
import io.bluetape4k.images.examples.spring.intelligence.model.AnalysisStatus
import io.bluetape4k.images.examples.spring.intelligence.model.BarcodeAnalysisResponse
import io.bluetape4k.images.examples.spring.intelligence.model.BarcodeResponse
import io.bluetape4k.images.examples.spring.intelligence.model.DetectionAnalysisResponse
import io.bluetape4k.images.examples.spring.intelligence.model.DetectionResponse
import io.bluetape4k.images.examples.spring.intelligence.model.ImageIntelligenceResponse
import io.bluetape4k.images.examples.spring.intelligence.model.OcrAnalysisResponse
import io.bluetape4k.images.examples.spring.intelligence.model.OcrResponse
import io.bluetape4k.images.examples.spring.intelligence.model.QualifiedImageResponse
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.info
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

internal fun interface ImageIntelligenceOperations {
    suspend fun analyze(file: MultipartFile): ImageIntelligenceResponse
}

internal class ImageIntelligenceService(
    private val qualifier: ImageUploadQualifier,
    private val workflow: ImageIntelligenceWorkflow,
    private val aggregator: ImageIntelligenceAggregator,
    private val policy: VisitorPassPolicy,
    private val requestIdProvider: () -> String = { UUID.randomUUID().toString() },
) : ImageIntelligenceOperations {

    override suspend fun analyze(file: MultipartFile): ImageIntelligenceResponse {
        val requestId = requestIdProvider()
        log.info { "Image intelligence request started. requestId=$requestId" }
        val qualified = qualifier.qualify(file)
        val results = workflow.analyze(qualified.image)
        val aggregateStatus = aggregator.status(results)
        val decision = policy.decide(results)
        val response = ImageIntelligenceResponse(
            requestId = requestId,
            status = aggregateStatus,
            decision = decision.action,
            reasons = decision.reasons,
            image = QualifiedImageResponse(
                mediaType = qualified.mediaType,
                width = qualified.dimensions.width,
                height = qualified.dimensions.height,
            ),
            ocr = results.ocr.toOcrResponse(),
            detection = results.detection.toDetectionResponse(),
            barcodes = results.barcode.toBarcodeResponse(),
        )
        log.info {
            "Image intelligence request completed. requestId=$requestId status=$aggregateStatus " +
                "ocr=${results.ocr.provider}:${results.ocr.statusName()}:${results.ocr.elapsedMillis}ms " +
                "detection=${results.detection.provider}:${results.detection.statusName()}:" +
                "${results.detection.elapsedMillis}ms " +
                "barcode=${results.barcode.provider}:${results.barcode.statusName()}:" +
                "${results.barcode.elapsedMillis}ms"
        }
        return response
    }

    private fun AnalysisResult<*>.statusName(): AnalysisStatus =
        when (this) {
            is AnalysisResult.Completed -> AnalysisStatus.COMPLETED
            is AnalysisResult.Empty -> AnalysisStatus.EMPTY
            is AnalysisResult.Unavailable -> AnalysisStatus.UNAVAILABLE
            is AnalysisResult.Failed -> AnalysisStatus.FAILED
        }

    private fun AnalysisResult<io.bluetape4k.images.ocr.OcrStructuredResult>.toOcrResponse(): OcrAnalysisResponse =
        when (this) {
            is AnalysisResult.Completed -> OcrAnalysisResponse(
                status = AnalysisStatus.COMPLETED,
                provider = provider,
                elapsedMillis = elapsedMillis,
                result = OcrResponse(value.text, value.pages.size),
            )
            is AnalysisResult.Empty -> OcrAnalysisResponse(AnalysisStatus.EMPTY, provider, elapsedMillis)
            is AnalysisResult.Unavailable ->
                OcrAnalysisResponse(AnalysisStatus.UNAVAILABLE, provider, elapsedMillis, reasonCode = reasonCode)
            is AnalysisResult.Failed ->
                OcrAnalysisResponse(AnalysisStatus.FAILED, provider, elapsedMillis, reasonCode = reasonCode)
        }

    private fun AnalysisResult<List<io.bluetape4k.images.detection.DetectionResult>>.toDetectionResponse():
        DetectionAnalysisResponse =
        when (this) {
            is AnalysisResult.Completed -> DetectionAnalysisResponse(
                status = AnalysisStatus.COMPLETED,
                provider = provider,
                elapsedMillis = elapsedMillis,
                regions = value.map {
                    DetectionResponse(
                        label = it.label,
                        category = it.category,
                        confidence = it.confidence,
                        detector = it.detector.name,
                    )
                },
            )
            is AnalysisResult.Empty -> DetectionAnalysisResponse(AnalysisStatus.EMPTY, provider, elapsedMillis)
            is AnalysisResult.Unavailable ->
                DetectionAnalysisResponse(AnalysisStatus.UNAVAILABLE, provider, elapsedMillis, reasonCode = reasonCode)
            is AnalysisResult.Failed ->
                DetectionAnalysisResponse(AnalysisStatus.FAILED, provider, elapsedMillis, reasonCode = reasonCode)
        }

    private fun AnalysisResult<List<io.bluetape4k.images.barcode.BarcodeResult>>.toBarcodeResponse():
        BarcodeAnalysisResponse =
        when (this) {
            is AnalysisResult.Completed -> BarcodeAnalysisResponse(
                status = AnalysisStatus.COMPLETED,
                provider = provider,
                elapsedMillis = elapsedMillis,
                items = value.map {
                    BarcodeResponse(
                        text = it.text,
                        format = it.format,
                        provider = it.provider.name,
                    )
                },
            )
            is AnalysisResult.Empty -> BarcodeAnalysisResponse(AnalysisStatus.EMPTY, provider, elapsedMillis)
            is AnalysisResult.Unavailable ->
                BarcodeAnalysisResponse(AnalysisStatus.UNAVAILABLE, provider, elapsedMillis, reasonCode = reasonCode)
            is AnalysisResult.Failed ->
                BarcodeAnalysisResponse(AnalysisStatus.FAILED, provider, elapsedMillis, reasonCode = reasonCode)
        }

    private companion object : KLogging()
}
