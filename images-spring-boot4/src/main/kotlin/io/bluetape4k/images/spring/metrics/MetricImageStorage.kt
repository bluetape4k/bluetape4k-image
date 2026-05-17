package io.bluetape4k.images.spring.metrics

import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.ImageUploadResult
import io.bluetape4k.images.spring.UploadOptions
import io.bluetape4k.images.spring.storage.ImageStorage
import io.bluetape4k.logging.KLogging
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.CancellationException
import java.io.Serializable
import java.nio.file.Path

/**
 * Decorates an [ImageStorage] with Micrometer metrics.
 *
 * ## Behavior / Contract
 * - Wraps `upload(bytes)`, `upload(Path)` and `download(key)` operations with a [Timer] and an
 *   error [io.micrometer.core.instrument.Counter].
 * - Delegates every other [ImageStorage] method to the wrapped instance via Kotlin's class
 *   delegation (`by delegate`).
 * - Uses `Timer.start(registry)` + `Sample.stop(registry.timer(...))` (instead of the
 *   `recordSuspend` extension shipped in `micrometer-core-kotlin`, which is not a transitive
 *   dependency).
 * - `CancellationException` is rethrown immediately to honour structured concurrency
 *   (CLAUDE.md). The timer sample is stopped before propagation so a cancelled upload still
 *   emits a duration.
 * - Errors increment the error counter and stop the timer; the original exception is rethrown.
 *
 * Metric names:
 * - `images.storage.upload.duration` — Timer for successful and failed uploads.
 * - `images.storage.upload.errors`   — Counter incremented on any upload failure.
 * - `images.storage.download.duration` — Timer for successful and failed downloads.
 * - `images.storage.download.errors`  — Counter incremented on any download failure.
 */
class MetricImageStorage(
    private val delegate: ImageStorage,
    private val registry: MeterRegistry,
) : ImageStorage by delegate, Serializable {

    companion object : KLogging() {
        private const val serialVersionUID: Long = 1L

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
