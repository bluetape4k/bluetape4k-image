package io.bluetape4k.images.examples.spring.intelligence.service

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.nio.PngWriter
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.images.barcode.BarcodeResult
import io.bluetape4k.images.barcode.zxing.ZxingBarcodeReader
import io.bluetape4k.images.examples.spring.intelligence.config.ImageIntelligenceProperties
import io.bluetape4k.images.examples.spring.intelligence.model.AnalysisStatus
import io.bluetape4k.images.examples.spring.intelligence.support.qrImage
import io.bluetape4k.images.ocr.OcrStructuredResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile

class ImageIntelligenceServiceTest {

    @Test
    fun `one failed lane returns a partial envelope and preserves siblings`() = runTest {
        val service = service(
            ocr = object : OcrAnalysisProvider {
                override val id: String = "broken-ocr"
                override suspend fun analyze(image: ImmutableImage): OcrStructuredResult =
                    error("native-path=/private/secret")
            },
            detection = FixtureDetectionAnalysisProvider(),
            barcode = zxing(),
        )

        val response = service.analyze(visitorUpload())

        response.requestId shouldBeEqualTo "request-test"
        response.status shouldBeEqualTo AggregateStatus.PARTIAL
        response.decision shouldBeEqualTo VisitorPassAction.MANUAL_REVIEW
        response.ocr.status shouldBeEqualTo AnalysisStatus.FAILED
        response.detection.status shouldBeEqualTo AnalysisStatus.COMPLETED
        response.barcodes.status shouldBeEqualTo AnalysisStatus.COMPLETED
    }

    @Test
    fun `no available lane returns a failed envelope`() = runTest {
        val service = service(
            ocr = DisabledOcrAnalysisProvider(),
            detection = DisabledDetectionAnalysisProvider(),
            barcode = object : BarcodeAnalysisProvider {
                override val id: String = "broken-barcode"
                override suspend fun analyze(image: ImmutableImage): List<BarcodeResult> =
                    error("decoder-secret")
            },
        )

        val response = service.analyze(visitorUpload())

        response.status shouldBeEqualTo AggregateStatus.FAILED
        response.decision shouldBeEqualTo VisitorPassAction.MANUAL_REVIEW
        response.ocr.status shouldBeEqualTo AnalysisStatus.UNAVAILABLE
        response.detection.status shouldBeEqualTo AnalysisStatus.UNAVAILABLE
        response.barcodes.status shouldBeEqualTo AnalysisStatus.FAILED
    }

    private fun service(
        ocr: OcrAnalysisProvider,
        detection: DetectionAnalysisProvider,
        barcode: BarcodeAnalysisProvider,
    ): ImageIntelligenceService {
        val properties = ImageIntelligenceProperties()
        return ImageIntelligenceService(
            qualifier = ImageUploadQualifier(properties),
            workflow = ImageIntelligenceWorkflow(
                ocrProvider = ocr,
                detectionProvider = detection,
                barcodeProvider = barcode,
                runner = GuardedAnalysisRunner(),
                properties = properties,
            ),
            aggregator = ImageIntelligenceAggregator(),
            policy = VisitorPassPolicy(),
            requestIdProvider = { "request-test" },
        )
    }

    private fun zxing(): BarcodeAnalysisProvider =
        ZxingBarcodeAnalysisProvider(
            reader = ZxingBarcodeReader(),
            dispatcher = Dispatchers.Unconfined,
        )

    private fun visitorUpload(): MockMultipartFile =
        MockMultipartFile(
            "file",
            "visitor.png",
            MediaType.IMAGE_PNG_VALUE,
            qrImage().forWriter(PngWriter.MaxCompression).bytes(),
        )
}
