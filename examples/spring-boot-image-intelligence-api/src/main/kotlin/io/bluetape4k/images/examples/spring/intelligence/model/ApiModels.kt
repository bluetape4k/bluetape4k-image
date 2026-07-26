package io.bluetape4k.images.examples.spring.intelligence.model

import io.bluetape4k.images.barcode.BarcodeFormat
import io.bluetape4k.images.detection.DetectionCategory
import io.bluetape4k.images.examples.spring.intelligence.service.AggregateStatus
import io.bluetape4k.images.examples.spring.intelligence.service.VisitorPassAction
import java.io.Serializable

internal enum class AnalysisStatus {
    COMPLETED,
    EMPTY,
    UNAVAILABLE,
    FAILED,
}

internal data class QualifiedImageResponse(
    val mediaType: String,
    val width: Int,
    val height: Int,
) : Serializable {
    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}

internal data class OcrResponse(
    val text: String,
    val pageCount: Int,
) : Serializable {
    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}

internal data class DetectionResponse(
    val label: String,
    val category: DetectionCategory,
    val confidence: Double,
    val detector: String,
) : Serializable {
    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}

internal data class BarcodeResponse(
    val text: String,
    val format: BarcodeFormat,
    val provider: String,
) : Serializable {
    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}

internal data class OcrAnalysisResponse(
    val status: AnalysisStatus,
    val provider: String,
    val elapsedMillis: Long,
    val result: OcrResponse? = null,
    val reasonCode: String? = null,
) : Serializable {
    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}

internal data class DetectionAnalysisResponse(
    val status: AnalysisStatus,
    val provider: String,
    val elapsedMillis: Long,
    val regions: List<DetectionResponse> = emptyList(),
    val reasonCode: String? = null,
) : Serializable {
    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}

internal data class BarcodeAnalysisResponse(
    val status: AnalysisStatus,
    val provider: String,
    val elapsedMillis: Long,
    val items: List<BarcodeResponse> = emptyList(),
    val reasonCode: String? = null,
) : Serializable {
    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}

internal data class ImageIntelligenceResponse(
    val requestId: String,
    val status: AggregateStatus,
    val decision: VisitorPassAction,
    val reasons: List<String>,
    val image: QualifiedImageResponse,
    val ocr: OcrAnalysisResponse,
    val detection: DetectionAnalysisResponse,
    val barcodes: BarcodeAnalysisResponse,
) : Serializable {
    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}
