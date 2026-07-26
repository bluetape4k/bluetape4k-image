package io.bluetape4k.images.examples.spring.intelligence.model

import java.io.Serializable

internal sealed interface AnalysisResult<out T> : Serializable {
    val provider: String
    val elapsedMillis: Long

    data class Completed<T>(
        override val provider: String,
        override val elapsedMillis: Long,
        val value: T,
    ) : AnalysisResult<T> {
        private companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    data class Empty(
        override val provider: String,
        override val elapsedMillis: Long,
    ) : AnalysisResult<Nothing> {
        private companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    data class Unavailable(
        override val provider: String,
        override val elapsedMillis: Long,
        val reasonCode: String,
    ) : AnalysisResult<Nothing> {
        private companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    data class Failed(
        override val provider: String,
        override val elapsedMillis: Long,
        val reasonCode: String,
    ) : AnalysisResult<Nothing> {
        private companion object {
            private const val serialVersionUID: Long = 1L
        }
    }
}
