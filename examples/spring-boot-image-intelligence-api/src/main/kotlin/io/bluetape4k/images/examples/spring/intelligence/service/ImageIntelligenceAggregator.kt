package io.bluetape4k.images.examples.spring.intelligence.service

import io.bluetape4k.images.examples.spring.intelligence.model.AnalysisResult

internal enum class AggregateStatus {
    COMPLETED,
    PARTIAL,
    FAILED,
}

internal class ImageIntelligenceAggregator {

    fun status(results: ImageAnalysisResults): AggregateStatus {
        val outcomes = listOf(results.ocr, results.detection, results.barcode)
        val available = outcomes.count { it.isAvailable() }
        return when {
            available == outcomes.size -> AggregateStatus.COMPLETED
            available > 0 -> AggregateStatus.PARTIAL
            else -> AggregateStatus.FAILED
        }
    }

    private fun AnalysisResult<*>.isAvailable(): Boolean =
        this is AnalysisResult.Completed || this is AnalysisResult.Empty
}
