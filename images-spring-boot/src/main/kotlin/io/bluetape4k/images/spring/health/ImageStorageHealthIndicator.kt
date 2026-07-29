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

/**
 * 설정된 [ImageStorage]를 probe하는 reactive health indicator입니다.
 *
 * ## 동작/계약
 * - storage backend reachability를 확인하기 위해 `storage.exists(ImageObjectKey.of("_health", probeKey))`를 호출합니다.
 * - 성공하면 `Health.up()`을 반환하고, probe가 [CancellationException]이 아닌 exception을 던지면
 *   `Health.down(e)`를 반환합니다.
 * - structured concurrency(CLAUDE.md)를 지키기 위해 [CancellationException]은 항상 다시 던집니다.
 * - suspend bridge는 `kotlinx-coroutines-reactor`의 `mono { }` builder를 통해
 *   `Mono<Health>`를 반환하는 [ReactiveHealthIndicator]로 구현합니다. `runBlocking`은 절대 사용하지 않습니다.
 *
 * auto-configuration wiring이 명시적이도록 constructor parameter는 named form으로 받습니다.
 */
class ImageStorageHealthIndicator(
    private val storage: ImageStorage,
    private val probeKey: String,
) : ReactiveHealthIndicator {

    companion object : KLogging() {

        /** synthetic health-probe key에 사용하는 prefix segment입니다. */
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
