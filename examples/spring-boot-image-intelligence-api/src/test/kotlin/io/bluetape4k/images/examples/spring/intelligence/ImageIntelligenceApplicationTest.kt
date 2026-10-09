package io.bluetape4k.images.examples.spring.intelligence

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.images.examples.spring.intelligence.model.AnalysisStatus
import io.bluetape4k.images.examples.spring.intelligence.service.AggregateStatus
import io.bluetape4k.images.examples.spring.intelligence.service.DetectionAnalysisProvider
import io.bluetape4k.images.examples.spring.intelligence.service.DisabledDetectionAnalysisProvider
import io.bluetape4k.images.examples.spring.intelligence.service.DisabledOcrAnalysisProvider
import io.bluetape4k.images.examples.spring.intelligence.service.ImageIntelligenceOperations
import io.bluetape4k.images.examples.spring.intelligence.service.OcrAnalysisProvider
import io.bluetape4k.images.examples.spring.intelligence.service.VisitorPassAction
import io.bluetape4k.images.examples.spring.intelligence.support.pngBytes
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.servlet.autoconfigure.MultipartProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile

@SpringBootTest
class ImageIntelligenceApplicationTest {

    companion object: KLogging()

    @Autowired
    private lateinit var operations: ImageIntelligenceOperations

    @Autowired
    private lateinit var ocrProvider: OcrAnalysisProvider

    @Autowired
    private lateinit var detectionProvider: DetectionAnalysisProvider

    @Autowired
    private lateinit var multipartProperties: MultipartProperties

    @Test
    fun `default application fails closed without native providers`(): Unit = runBlocking {
        ocrProvider.shouldBeInstanceOf<DisabledOcrAnalysisProvider>()
        detectionProvider.shouldBeInstanceOf<DisabledDetectionAnalysisProvider>()

        val response = operations.analyze(
            MockMultipartFile(
                "file",
                "blank.png",
                MediaType.IMAGE_PNG_VALUE,
                pngBytes(),
            ),
        )

        log.debug { "response=$response" }
        response.status shouldBeEqualTo AggregateStatus.PARTIAL
        response.decision shouldBeEqualTo VisitorPassAction.MANUAL_REVIEW
        response.ocr.status shouldBeEqualTo AnalysisStatus.UNAVAILABLE
        response.detection.status shouldBeEqualTo AnalysisStatus.UNAVAILABLE
        response.barcodes.status shouldBeEqualTo AnalysisStatus.EMPTY
    }

    @Test
    fun `multipart request limit leaves room for envelope overhead`() {
        multipartProperties.maxRequestSize.toBytes() shouldBeGreaterThan multipartProperties.maxFileSize.toBytes()
    }
}
