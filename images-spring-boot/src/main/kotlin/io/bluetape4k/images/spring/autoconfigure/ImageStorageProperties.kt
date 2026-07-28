package io.bluetape4k.images.spring.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties
import java.io.Serializable
import java.time.Duration

/**
 * image storage용 configuration properties입니다.
 *
 * ## 동작
 * - `bluetape4k.images.storage` prefix에 bind됩니다.
 * - [backend]가 [Backend.S3]이면 [bucket]은 non-null/non-blank여야 하며,
 *   `ImagesStorageAutoConfiguration.S3StorageConfiguration`이 validation을 적용합니다.
 * - [backend]가 [Backend.S3]이면 application이 자체 [io.bluetape4k.images.spring.storage.ImageStorage] bean을
 *   제공하지 않는 한 `io.bluetape4k.aws.spring.s3.S3Operations` bean이 필요합니다.
 * - [maxSizeBytes]는 upload와 download 모두에 적용됩니다. 초과 size는
 *   [io.bluetape4k.images.spring.ImageStorageException.ValidationException]을 발생시킵니다.
 * - [healthProbeKey]는 health indicator가 storage availability를 probe할 때 사용하는 object name입니다.
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

    /** storage backend 선택 값입니다. */
    enum class Backend { LOCAL, S3 }

    /**
     * local filesystem backend configuration입니다.
     *
     * ## 동작
     * - [rootDir] 기본값은 JVM temporary directory입니다. persistent storage에는 명시적인 path를 사용해야 합니다.
     */
    data class Local(
        val rootDir: String = System.getProperty("java.io.tmpdir") + "/bluetape4k-images",
    ) : Serializable {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    /**
     * S3-compatible backend configuration입니다.
     *
     * ## 동작
     * - [callTimeout]은 SDK call 하나의 end-to-end timeout입니다.
     * - [attemptTimeout]은 retry를 제외한 attempt별 timeout입니다.
     * - [maxRetries]는 SDK retry count를 제어합니다.
     * - [maxInFlight]는 concurrent S3 operation 수를 제한합니다.
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
