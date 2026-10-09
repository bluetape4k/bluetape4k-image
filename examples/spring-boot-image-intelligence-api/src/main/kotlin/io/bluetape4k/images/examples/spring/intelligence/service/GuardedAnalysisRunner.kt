package io.bluetape4k.images.examples.spring.intelligence.service

import io.bluetape4k.images.examples.spring.intelligence.model.AnalysisResult
import io.bluetape4k.javatimes.inMillis
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.warn
import io.bluetape4k.support.requireGt
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import java.time.Duration
import kotlin.time.TimeSource
import kotlin.time.toKotlinDuration

internal class ProviderUnavailableException(
    val reasonCode: String,
): RuntimeException(reasonCode)

internal class GuardedAnalysisRunner {

    private companion object: KLogging()

    suspend fun <T: Any> run(
        provider: String,
        timeout: Duration,
        semaphore: Semaphore,
        isEmpty: (T) -> Boolean = { false },
        block: suspend () -> T,
    ): AnalysisResult<T> {
        val validProvider = provider.requireNotBlank("provider")
        timeout.inMillis().requireGt(0) { "timeout must be at least 1 ms" }

        val started = TimeSource.Monotonic.markNow()

        return try {
            semaphore.withPermit {
                withTimeout(timeout.toKotlinDuration()) {
                    val value = block()
                    if (isEmpty(value)) {
                        AnalysisResult.Empty(
                            provider = validProvider,
                            elapsedMillis = started.elapsedMillis(),
                        )
                    } else {
                        AnalysisResult.Completed(
                            provider = validProvider,
                            elapsedMillis = started.elapsedMillis(),
                            value = value,
                        )
                    }
                }
            }
        } catch (exception: TimeoutCancellationException) {
            AnalysisResult.Failed(
                provider = validProvider,
                elapsedMillis = started.elapsedMillis(),
                reasonCode = "timeout",
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: ProviderUnavailableException) {
            AnalysisResult.Unavailable(
                provider = validProvider,
                elapsedMillis = started.elapsedMillis(),
                reasonCode = e.reasonCode,
            )
        } catch (e: Exception) {
            log.warn(e) { "Image analysis provider failed. provider=$validProvider reason=provider_failure" }
            AnalysisResult.Failed(
                provider = validProvider,
                elapsedMillis = started.elapsedMillis(),
                reasonCode = "provider_failure",
            )
        }
    }

    private fun TimeSource.Monotonic.ValueTimeMark.elapsedMillis(): Long =
        elapsedNow().inWholeMilliseconds.coerceAtLeast(0L)
}
