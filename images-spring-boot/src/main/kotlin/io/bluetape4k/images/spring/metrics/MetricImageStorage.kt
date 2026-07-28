package io.bluetape4k.images.spring.metrics

import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.ImageUploadResult
import io.bluetape4k.images.spring.UploadOptions
import io.bluetape4k.images.spring.storage.ImageStorage
import io.bluetape4k.logging.KLogging
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.CancellationException
import java.nio.file.Path

/**
 * [ImageStorage]에 Micrometer metric을 입히는 decorator입니다.
 *
 * ## 동작/계약
 * - `upload(bytes)`, `upload(Path)`, `download(key)` operation을 [Timer]와
 *   error [io.micrometer.core.instrument.Counter]로 감쌉니다.
 * - 나머지 [ImageStorage] method는 Kotlin class delegation(`by delegate`)으로 wrapped instance에 위임합니다.
 * - transitive dependency가 아닌 `micrometer-core-kotlin`의 `recordSuspend` extension 대신
 *   `Timer.start(registry)` + `Sample.stop(registry.timer(...))`를 사용합니다.
 * - structured concurrency(CLAUDE.md)를 지키기 위해 `CancellationException`은 즉시 다시 던집니다.
 *   propagation 전에 timer sample을 stop하므로 취소된 upload도 duration을 emit합니다.
 * - error는 error counter를 증가시키고 timer를 stop한 뒤 원래 exception을 다시 던집니다.
 *
 * metric name:
 * - `images.storage.upload.duration` — 성공/실패 upload의 Timer입니다.
 * - `images.storage.upload.errors`   — upload failure마다 증가하는 Counter입니다.
 * - `images.storage.download.duration` — 성공/실패 download의 Timer입니다.
 * - `images.storage.download.errors`  — download failure마다 증가하는 Counter입니다.
 */
class MetricImageStorage(
    private val delegate: ImageStorage,
    private val registry: MeterRegistry,
) : ImageStorage by delegate {

    companion object : KLogging() {

        private const val UPLOAD_TIMER: String = "images.storage.upload.duration"
        private const val UPLOAD_ERRORS: String = "images.storage.upload.errors"
        private const val DOWNLOAD_TIMER: String = "images.storage.download.duration"
        private const val DOWNLOAD_ERRORS: String = "images.storage.download.errors"
    }

    override suspend fun upload(
        key: ImageObjectKey,
        bytes: ByteArray,
        options: UploadOptions,
    ): ImageUploadResult {
        val sample = Timer.start(registry)
        try {
            val result = delegate.upload(key, bytes, options)
            sample.stop(registry.timer(UPLOAD_TIMER))
            return result
        } catch (e: CancellationException) {
            sample.stop(registry.timer(UPLOAD_TIMER))
            throw e
        } catch (e: Throwable) {
            sample.stop(registry.timer(UPLOAD_TIMER))
            registry.counter(UPLOAD_ERRORS).increment()
            throw e
        }
    }

    override suspend fun upload(
        key: ImageObjectKey,
        source: Path,
        options: UploadOptions,
    ): ImageUploadResult {
        val sample = Timer.start(registry)
        try {
            val result = delegate.upload(key, source, options)
            sample.stop(registry.timer(UPLOAD_TIMER))
            return result
        } catch (e: CancellationException) {
            sample.stop(registry.timer(UPLOAD_TIMER))
            throw e
        } catch (e: Throwable) {
            sample.stop(registry.timer(UPLOAD_TIMER))
            registry.counter(UPLOAD_ERRORS).increment()
            throw e
        }
    }

    override suspend fun download(key: ImageObjectKey): ByteArray {
        val sample = Timer.start(registry)
        try {
            val result = delegate.download(key)
            sample.stop(registry.timer(DOWNLOAD_TIMER))
            return result
        } catch (e: CancellationException) {
            sample.stop(registry.timer(DOWNLOAD_TIMER))
            throw e
        } catch (e: Throwable) {
            sample.stop(registry.timer(DOWNLOAD_TIMER))
            registry.counter(DOWNLOAD_ERRORS).increment()
            throw e
        }
    }

    override suspend fun download(key: ImageObjectKey, destination: Path) {
        val sample = Timer.start(registry)
        try {
            delegate.download(key, destination)
            sample.stop(registry.timer(DOWNLOAD_TIMER))
        } catch (e: CancellationException) {
            sample.stop(registry.timer(DOWNLOAD_TIMER))
            throw e
        } catch (e: Throwable) {
            sample.stop(registry.timer(DOWNLOAD_TIMER))
            registry.counter(DOWNLOAD_ERRORS).increment()
            throw e
        }
    }
}
