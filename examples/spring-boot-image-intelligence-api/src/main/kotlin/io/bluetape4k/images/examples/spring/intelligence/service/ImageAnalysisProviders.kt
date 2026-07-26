package io.bluetape4k.images.examples.spring.intelligence.service

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.images.barcode.BarcodeOptions
import io.bluetape4k.images.barcode.BarcodeReader
import io.bluetape4k.images.barcode.BarcodeResult
import io.bluetape4k.images.barcode.suspendExtractBarcodes
import io.bluetape4k.images.detection.DetectionCategory
import io.bluetape4k.images.detection.DetectionOptions
import io.bluetape4k.images.detection.DetectionResult
import io.bluetape4k.images.detection.DetectorIdentity
import io.bluetape4k.images.detection.ImageDetector
import io.bluetape4k.images.detection.suspendDetectRegions
import io.bluetape4k.images.ocr.OcrOptions
import io.bluetape4k.images.ocr.OcrPage
import io.bluetape4k.images.ocr.OcrStructuredDetail
import io.bluetape4k.images.ocr.OcrStructuredResult
import io.bluetape4k.images.ocr.StructuredOcrEngine
import io.bluetape4k.images.ocr.suspendExtractOcr
import kotlinx.coroutines.CoroutineDispatcher
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.core.env.Environment

internal interface OcrAnalysisProvider {
    val id: String

    suspend fun analyze(image: ImmutableImage): OcrStructuredResult
}

internal interface DetectionAnalysisProvider {
    val id: String

    suspend fun analyze(image: ImmutableImage): List<DetectionResult>
}

internal interface BarcodeAnalysisProvider {
    val id: String

    suspend fun analyze(image: ImmutableImage): List<BarcodeResult>
}

internal class DisabledOcrAnalysisProvider : OcrAnalysisProvider {
    override val id: String = "disabled-ocr"

    override suspend fun analyze(image: ImmutableImage): OcrStructuredResult =
        throw ProviderUnavailableException("provider_not_configured")
}

internal class FixtureOcrAnalysisProvider : OcrAnalysisProvider {
    override val id: String = "fixture-ocr"

    override suspend fun analyze(image: ImmutableImage): OcrStructuredResult {
        val text = "VISITOR PASS-001"
        val options = OcrOptions(structuredDetail = OcrStructuredDetail.PLAIN_TEXT)
        return OcrStructuredResult(
            text = text,
            options = options,
            pages = listOf(OcrPage(pageIndex = 0, text = text)),
        )
    }
}

internal class TesseractOcrAnalysisProvider(
    private val engine: StructuredOcrEngine,
    private val options: OcrOptions,
    private val dispatcher: CoroutineDispatcher,
) : OcrAnalysisProvider {
    override val id: String = "tesseract"

    override suspend fun analyze(image: ImmutableImage): OcrStructuredResult =
        image.suspendExtractOcr(
            options = options,
            engine = engine,
            dispatcher = dispatcher,
        )
}

internal class DisabledDetectionAnalysisProvider : DetectionAnalysisProvider {
    override val id: String = "disabled-detector"

    override suspend fun analyze(image: ImmutableImage): List<DetectionResult> =
        throw ProviderUnavailableException("provider_not_configured")
}

internal class FixtureDetectionAnalysisProvider : DetectionAnalysisProvider {
    override val id: String = "fixture-detector"

    override suspend fun analyze(image: ImmutableImage): List<DetectionResult> =
        listOf(
            DetectionResult(
                label = "face",
                category = DetectionCategory.FACE,
                confidence = 0.99,
                detector = DetectorIdentity(
                    name = id,
                    version = "generated",
                    backend = "fixture",
                ),
                metadata = mapOf("source" to "generated-test-fixture"),
            ),
        )
}

internal class LocalDetectionAnalysisProvider(
    override val id: String,
    private val detector: ImageDetector,
    private val options: DetectionOptions,
    private val dispatcher: CoroutineDispatcher,
) : DetectionAnalysisProvider {
    override suspend fun analyze(image: ImmutableImage): List<DetectionResult> =
        image.suspendDetectRegions(
            detector = detector,
            options = options,
            dispatcher = dispatcher,
        )
}

internal class ZxingBarcodeAnalysisProvider(
    private val reader: BarcodeReader,
    private val options: BarcodeOptions = BarcodeOptions(),
    private val dispatcher: CoroutineDispatcher,
) : BarcodeAnalysisProvider {
    override val id: String = "zxing"

    override suspend fun analyze(image: ImmutableImage): List<BarcodeResult> =
        image.suspendExtractBarcodes(
            reader = reader,
            options = options,
            dispatcher = dispatcher,
        )
}

internal class ImageIntelligenceProfileGuard(
    private val environment: Environment,
) : SmartInitializingSingleton {
    override fun afterSingletonsInstantiated() {
        val activeProfiles = environment.activeProfiles.toSet()
        require(!activeProfiles.containsAll(setOf("demo", "native-ocr"))) {
            "Profiles 'demo' and 'native-ocr' cannot be active together."
        }
    }
}
