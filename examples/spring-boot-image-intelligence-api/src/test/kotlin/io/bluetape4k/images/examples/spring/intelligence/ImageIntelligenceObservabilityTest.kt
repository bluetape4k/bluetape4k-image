package io.bluetape4k.images.examples.spring.intelligence

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.nio.PngWriter
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotContain
import io.bluetape4k.images.barcode.BarcodeResult
import io.bluetape4k.images.examples.spring.intelligence.config.ImageIntelligenceProperties
import io.bluetape4k.images.examples.spring.intelligence.service.BarcodeAnalysisProvider
import io.bluetape4k.images.examples.spring.intelligence.service.FixtureDetectionAnalysisProvider
import io.bluetape4k.images.examples.spring.intelligence.service.GuardedAnalysisRunner
import io.bluetape4k.images.examples.spring.intelligence.service.ImageIntelligenceAggregator
import io.bluetape4k.images.examples.spring.intelligence.service.ImageIntelligenceService
import io.bluetape4k.images.examples.spring.intelligence.service.ImageIntelligenceWorkflow
import io.bluetape4k.images.examples.spring.intelligence.service.ImageUploadQualifier
import io.bluetape4k.images.examples.spring.intelligence.service.OcrAnalysisProvider
import io.bluetape4k.images.examples.spring.intelligence.service.VisitorPassPolicy
import io.bluetape4k.images.examples.spring.intelligence.support.VISITOR_PASS_PAYLOAD
import io.bluetape4k.images.examples.spring.intelligence.support.qrImage
import io.bluetape4k.images.ocr.OcrStructuredResult
import io.bluetape4k.junit5.output.OutputCapture
import io.bluetape4k.junit5.output.OutputCapturer
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile

@OutputCapture
class ImageIntelligenceObservabilityTest {

    companion object: KLoggingChannel()

    @Test
    fun `lifecycle logs retain operational facts and redact payloads`(output: OutputCapturer) = runTest {
        val properties = ImageIntelligenceProperties()
        log.debug { "properties=$properties" }

        val service = ImageIntelligenceService(
            qualifier = ImageUploadQualifier(properties),
            workflow = ImageIntelligenceWorkflow(
                ocrProvider = object: OcrAnalysisProvider {
                    override val id: String = "broken-ocr"
                    override suspend fun analyze(image: ImmutableImage): OcrStructuredResult =
                        error("native-path=/private/secret-provider")
                },
                detectionProvider = FixtureDetectionAnalysisProvider(),
                barcodeProvider = object: BarcodeAnalysisProvider {
                    override val id: String = "empty-barcode"
                    override suspend fun analyze(image: ImmutableImage): List<BarcodeResult> = emptyList()
                },
                runner = GuardedAnalysisRunner(),
                properties = properties,
            ),
            aggregator = ImageIntelligenceAggregator(),
            policy = VisitorPassPolicy(),
            requestIdProvider = { "request-observability" },
        )

        service.analyze(
            MockMultipartFile(
                "file",
                "visitor.png",
                MediaType.IMAGE_PNG_VALUE,
                qrImage().forWriter(PngWriter.MaxCompression).bytes(),
            ),
        )

        val logs = output.toString()

        logs shouldContain "requestId=request-observability"
        logs shouldContain "broken-ocr:FAILED:"
        logs shouldContain "fixture-detector:COMPLETED:"
        logs shouldContain "empty-barcode:EMPTY:"
        logs shouldContain "ms"
        logs shouldNotContain VISITOR_PASS_PAYLOAD
        logs shouldNotContain "VISITOR PASS-001"

        // Error 관련
        logs shouldContain "native-path"
        logs shouldContain "/private/secret-provider"
        logs shouldNotContain "stackTrace"
    }
}
