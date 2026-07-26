package io.bluetape4k.images.examples.spring.intelligence.service

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.images.barcode.BarcodeFormat
import io.bluetape4k.images.barcode.BarcodeProviderIdentity
import io.bluetape4k.images.barcode.BarcodeResult
import io.bluetape4k.images.detection.DetectionCategory
import io.bluetape4k.images.detection.DetectionResult
import io.bluetape4k.images.detection.DetectorIdentity
import io.bluetape4k.images.examples.spring.intelligence.model.AnalysisResult
import io.bluetape4k.images.ocr.OcrOptions
import io.bluetape4k.images.ocr.OcrPage
import io.bluetape4k.images.ocr.OcrStructuredResult
import org.junit.jupiter.api.Test

class VisitorPassPolicyTest {

    private val aggregator = ImageIntelligenceAggregator()
    private val policy = VisitorPassPolicy()

    @Test
    fun `aggregate distinguishes completed partial and failed`() {
        aggregator.status(results()) shouldBeEqualTo AggregateStatus.COMPLETED
        aggregator.status(
            results(ocr = unavailable("ocr")),
        ) shouldBeEqualTo AggregateStatus.PARTIAL
        aggregator.status(
            results(
                ocr = unavailable("ocr"),
                detection = failed("detector"),
                barcode = unavailable("barcode"),
            ),
        ) shouldBeEqualTo AggregateStatus.FAILED
    }

    @Test
    fun `sensitive fact takes precedence and invalid visitor QR is rejected`() {
        policy.decide(
            results(detection = completed("detector", listOf(detection(DetectionCategory.SENSITIVE_REGION)))),
        ).action shouldBeEqualTo VisitorPassAction.QUARANTINE

        policy.decide(
            results(barcode = completed("barcode", listOf(barcode("product:ABC")))),
        ).action shouldBeEqualTo VisitorPassAction.REJECT
    }

    @Test
    fun `failed and unavailable lanes require manual review with stable reasons`() {
        val decision = policy.decide(
            results(
                ocr = unavailable("ocr"),
                detection = failed("detector"),
            ),
        )

        decision.action shouldBeEqualTo VisitorPassAction.MANUAL_REVIEW
        decision.reasons shouldBeEqualTo listOf("OCR_UNAVAILABLE", "DETECTION_FAILED")
    }

    @Test
    fun `empty detection and failed detection remain distinct`() {
        val empty = policy.decide(results(detection = empty("detector")))
        val failed = policy.decide(results(detection = failed("detector")))

        empty.reasons shouldBeEqualTo listOf("FACE_COUNT_REQUIRES_REVIEW")
        failed.reasons shouldBeEqualTo listOf("DETECTION_FAILED")
    }

    @Test
    fun `exactly one face and visitor QR with OCR is allowed`() {
        val decision = policy.decide(results())

        decision.action shouldBeEqualTo VisitorPassAction.ALLOW
        decision.reasons shouldBeEqualTo emptyList()
    }

    private fun results(
        ocr: AnalysisResult<OcrStructuredResult> = completed("ocr", ocr()),
        detection: AnalysisResult<List<DetectionResult>> =
            completed("detector", listOf(detection(DetectionCategory.FACE))),
        barcode: AnalysisResult<List<BarcodeResult>> =
            completed("barcode", listOf(barcode("visitor:PASS-001"))),
    ) = ImageAnalysisResults(ocr, detection, barcode)

    private fun ocr() =
        OcrStructuredResult(
            text = "VISITOR PASS-001",
            options = OcrOptions(),
            pages = listOf(OcrPage(pageIndex = 0, text = "VISITOR PASS-001")),
        )

    private fun detection(category: DetectionCategory) =
        DetectionResult(
            label = category.name.lowercase(),
            category = category,
            confidence = 0.99,
            detector = DetectorIdentity("fixture"),
        )

    private fun barcode(text: String) =
        BarcodeResult(
            text = text,
            format = BarcodeFormat.QR_CODE,
            provider = BarcodeProviderIdentity("fixture"),
        )

    private fun <T : Any> completed(provider: String, value: T): AnalysisResult<T> =
        AnalysisResult.Completed(provider, 1, value)

    private fun empty(provider: String): AnalysisResult<Nothing> =
        AnalysisResult.Empty(provider, 1)

    private fun unavailable(provider: String): AnalysisResult<Nothing> =
        AnalysisResult.Unavailable(provider, 1, "provider_not_configured")

    private fun failed(provider: String): AnalysisResult<Nothing> =
        AnalysisResult.Failed(provider, 1, "provider_failure")
}
