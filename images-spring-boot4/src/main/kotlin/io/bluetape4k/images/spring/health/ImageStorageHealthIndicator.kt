package io.bluetape4k.images.spring.health

import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.storage.ImageStorage
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.warn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.reactor.mono
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.ReactiveHealthIndicator
import reactor.core.publisher.Mono
import java.io.Serializable

/**
 * Reactive health indicator that probes the configured [ImageStorage].
 *
 * ## Behavior / Contract
 * - Calls `storage.exists(ImageObjectKey.of("_health", probeKey))` to verify the storage backend
 *   is reachable.
 * - Returns `Health.up()` on success; `Health.down(e)` when the probe raises an exception other
 *   than [CancellationException].
 * - [CancellationException] is always rethrown to honour structured concurrency (CLAUDE.md).
 * - Suspend bridge: implemented as a [ReactiveHealthIndicator] returning `Mono<Health>` via
 *   `kotlinx-coroutines-reactor`'s `mono { }` builder. `runBlocking` is never used.
 *
 * Constructor parameters are accepted in named form so wiring via auto-configuration is explicit.
 */
class ImageStorageHealthIndicator(
    private val storage: ImageStorage,
    private val probeKey: String,
) : ReactiveHealthIndicator, Serializable {

    companion object : KLogging() {
        private const val serialVersionUID: Long = 1L

        /** Prefix segment used for the synthetic health-probe key. */
        private const val HEALTH_PREFIX: String = "_health"
    }

    override fun health(): Mono<Health> = mono {
        try {
            storage.exists(ImageObjectKey.of(HEALTH_PREFIX, probeKey))
            Health.up().build()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn(e) { "Image storage health probe failed for key=$probeKey" }
            Health.down(e).build()
        }
    }
}
