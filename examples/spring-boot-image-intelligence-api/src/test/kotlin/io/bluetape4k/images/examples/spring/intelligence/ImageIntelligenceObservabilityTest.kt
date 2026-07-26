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
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile

@ExtendWith(OutputCaptureExtension::class)
class ImageIntelligenceObservabilityTest {

    @Test
    fun `lifecycle logs retain operational facts and redact payloads`(output: CapturedOutput) = runTest {
        val properties = ImageIntelligenceProperties()
        val service = ImageIntelligenceService(
            qualifier = ImageUploadQualifier(properties),
            workflow = ImageIntelligenceWorkflow(
                ocrProvider = object : OcrAnalysisProvider {
                    override val id: String = "broken-ocr"
                    override suspend fun analyze(image: ImmutableImage): OcrStructuredResult =
                        error("native-path=/private/secret-provider")
                },
                detectionProvider = FixtureDetectionAnalysisProvider(),
                barcodeProvider = object : BarcodeAnalysisProvider {
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

        val logs = output.all
        logs.shouldContain("requestId=request-observability")
        logs.shouldContain("broken-ocr:FAILED:")
        logs.shouldContain("fixture-detector:COMPLETED:")
        logs.shouldContain("empty-barcode:EMPTY:")
        logs.shouldContain("ms")
        logs.shouldNotContain(VISITOR_PASS_PAYLOAD)
        logs.shouldNotContain("VISITOR PASS-001")
        logs.shouldNotContain("/private/secret-provider")
        logs.shouldNotContain("native-path")
        logs.shouldNotContain("stackTrace")
    }
}
