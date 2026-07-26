package io.bluetape4k.images.examples.spring.intelligence.service

import io.bluetape4k.images.examples.spring.intelligence.model.AnalysisResult
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.warn
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import java.time.Duration
import kotlin.time.TimeSource

internal class ProviderUnavailableException(
    val reasonCode: String,
) : RuntimeException(reasonCode)

internal class GuardedAnalysisRunner {

    suspend fun <T : Any> run(
        provider: String,
        timeout: Duration,
        semaphore: Semaphore,
        isEmpty: (T) -> Boolean = { false },
        block: suspend () -> T,
    ): AnalysisResult<T> {
        val validProvider = provider.requireNotBlank("provider")
        require(timeout.toMillis() > 0L) { "timeout must be at least 1 ms" }
        val started = TimeSource.Monotonic.markNow()

        return try {
            semaphore.withPermit {
                withTimeout(timeout.toMillis()) {
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
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: ProviderUnavailableException) {
            AnalysisResult.Unavailable(
                provider = validProvider,
                elapsedMillis = started.elapsedMillis(),
                reasonCode = exception.reasonCode,
            )
        } catch (exception: Exception) {
            log.warn {
                "Image analysis provider failed. provider=$validProvider reason=provider_failure"
            }
            AnalysisResult.Failed(
                provider = validProvider,
                elapsedMillis = started.elapsedMillis(),
                reasonCode = "provider_failure",
            )
        }
    }

    private fun TimeSource.Monotonic.ValueTimeMark.elapsedMillis(): Long =
        elapsedNow().inWholeMilliseconds.coerceAtLeast(0L)

    private companion object: KLogging()
}
