package io.bluetape4k.images.spring.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties
import java.io.Serializable
import java.time.Duration

/**
 * Configuration properties for image storage.
 *
 * ## Behavior
 * - Bound to the `bluetape4k.images.storage` prefix.
 * - When [backend] is [Backend.S3], [bucket] must be non-null and non-blank; validation is enforced
 *   by `ImagesStorageAutoConfiguration.S3StorageConfiguration` via `@PostConstruct`.
 * - [maxSizeBytes] applies to both upload and download; exceeded sizes raise
 *   [io.bluetape4k.images.spring.ImageStorageException.ValidationException].
 * - [healthProbeKey] is the object name used by the health indicator to probe storage availability.
 */
@ConfigurationProperties(prefix = "bluetape4k.images.storage")
data class ImageStorageProperties(
    val enabled: Boolean = true,
    val backend: Backend = Backend.LOCAL,
    val bucket: String? = null,
    val keyPrefix: String = "",
    val maxSizeBytes: Long = 50 * 1024 * 1024L,
    val healthProbeKey: String = ".health-probe",
    val local: Local = Local(),
    val s3: S3 = S3(),
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }

    /** Storage backend selector. */
    enum class Backend { LOCAL, S3 }

    /**
     * Local filesystem backend configuration.
     *
     * ## Behavior
     * - [rootDir] defaults to the JVM temporary directory; use an explicit path for persistent storage.
     */
    data class Local(
        val rootDir: String = System.getProperty("java.io.tmpdir") + "/bluetape4k-images",
    ) : Serializable {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    /**
     * S3-compatible backend configuration.
     *
     * ## Behavior
     * - [callTimeout] is the end-to-end timeout per SDK call.
     * - [attemptTimeout] is the per-attempt timeout (retries excluded).
     * - [maxRetries] controls the SDK retry count.
     * - [maxInFlight] limits concurrent S3 operations.
     */
    data class S3(
        val callTimeout: Duration = Duration.ofSeconds(30),
        val attemptTimeout: Duration = Duration.ofSeconds(10),
        val maxRetries: Int = 3,
        val maxInFlight: Int = 64,
    ) : Serializable {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }
}
