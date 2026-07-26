package io.bluetape4k.images.examples.spring.intelligence.service

import io.bluetape4k.images.barcode.BarcodeFormat
import io.bluetape4k.images.barcode.BarcodeResult
import io.bluetape4k.images.detection.DetectionCategory
import io.bluetape4k.images.detection.DetectionResult
import io.bluetape4k.images.examples.spring.intelligence.model.AnalysisResult
import java.io.Serializable

internal enum class VisitorPassAction {
    ALLOW,
    MANUAL_REVIEW,
    REJECT,
    QUARANTINE,
}

internal data class VisitorPassDecision(
    val action: VisitorPassAction,
    val reasons: List<String>,
) : Serializable {
    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}

internal class VisitorPassPolicy {

    fun decide(results: ImageAnalysisResults): VisitorPassDecision {
        val detections = results.detection.completedValue().orEmpty()
        if (detections.any { it.category == DetectionCategory.SENSITIVE_REGION }) {
            return decision(VisitorPassAction.QUARANTINE, "SENSITIVE_REGION_DETECTED")
        }

        val barcodes = results.barcode.completedValue().orEmpty()
        if (results.barcode is AnalysisResult.Completed && barcodes.any { !it.isVisitorQr() }) {
            return decision(VisitorPassAction.REJECT, "INVALID_VISITOR_QR")
        }

        val degradedReasons = buildList {
            addDegradedReason(results.ocr, "OCR")
            addDegradedReason(results.detection, "DETECTION")
            addDegradedReason(results.barcode, "BARCODE")
        }
        if (degradedReasons.isNotEmpty()) {
            return VisitorPassDecision(VisitorPassAction.MANUAL_REVIEW, degradedReasons)
        }

        val reviewReasons = buildList {
            if (detections.count { it.category == DetectionCategory.FACE } != 1) {
                add("FACE_COUNT_REQUIRES_REVIEW")
            }
            if (barcodes.count { it.isVisitorQr() } != 1) {
                add("QR_COUNT_REQUIRES_REVIEW")
            }
            if (results.ocr !is AnalysisResult.Completed || results.ocr.value.text.isBlank()) {
                add("OCR_CONTENT_REQUIRES_REVIEW")
            }
        }
        return if (reviewReasons.isEmpty()) {
            VisitorPassDecision(VisitorPassAction.ALLOW, emptyList())
        } else {
            VisitorPassDecision(VisitorPassAction.MANUAL_REVIEW, reviewReasons)
        }
    }

    private fun MutableList<String>.addDegradedReason(result: AnalysisResult<*>, lane: String) {
        when (result) {
            is AnalysisResult.Failed -> add("${lane}_FAILED")
            is AnalysisResult.Unavailable -> add("${lane}_UNAVAILABLE")
            else -> Unit
        }
    }

    private fun BarcodeResult.isVisitorQr(): Boolean =
        format == BarcodeFormat.QR_CODE && text.startsWith(VISITOR_QR_PREFIX)

    private fun <T : Any> AnalysisResult<T>.completedValue(): T? =
        (this as? AnalysisResult.Completed<T>)?.value

    private fun decision(action: VisitorPassAction, reason: String): VisitorPassDecision =
        VisitorPassDecision(action, listOf(reason))

    private companion object {
        private const val VISITOR_QR_PREFIX: String = "visitor:"
    }
}
