package io.bluetape4k.images.spring.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties
import java.io.Serializable
import java.time.Duration

/**
 * CDN URL signing용 configuration properties입니다.
 *
 * ## 동작
 * - `bluetape4k.images.cdn` prefix에 bind됩니다.
 * - CDN signing은 기본적으로 비활성화됩니다([enabled] = false).
 * - [provider]는 S3 presigned URL과 CloudFront signed URL 중 하나를 선택합니다.
 * - [cloudfront] 하위 property는 [provider]가 `cloudfront`일 때만 의미가 있습니다.
 */
@ConfigurationProperties(prefix = "bluetape4k.images.cdn")
data class CdnProperties(
    val enabled: Boolean = false,
    val provider: String = "s3_presign",
    val cloudfront: CloudFront = CloudFront(),
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }

    /**
     * CloudFront URL signing configuration입니다.
     *
     * ## 동작
     * - CloudFront로 CDN을 활성화하면 [privateKeyPem] 또는 [privateKeyPath] 중 정확히 하나가 설정되어야 합니다.
     * - 둘 다 지정하면 bean creation 시점에 [IllegalArgumentException]이 발생합니다.
     * - inline [privateKeyPem]은 권장하지 않습니다. 값이 JVM heap에 남고 zero-fill할 수 없기 때문입니다.
     *   file-system secret 또는 mounted Kubernetes secret을 가리키는 [privateKeyPath]를 선호합니다.
     * - [toString]은 log와 Actuator endpoint에서 accidental exposure를 막기 위해 [privateKeyPem]을 redaction합니다.
     *   `ImagesCdnAutoConfiguration`이 등록하는 `SanitizingFunction` bean은 `/actuator/configprops`와
     *   `/actuator/env` level에서 추가 보호를 제공합니다.
     */
    data class CloudFront(
        val distributionDomain: String? = null,
        val keyPairId: String? = null,
        val privateKeyPem: String? = null,
        val privateKeyPath: String? = null,
        val defaultExpiry: Duration = Duration.ofMinutes(10),
        val maxExpiry: Duration = Duration.ofHours(1),
    ) : Serializable {

        companion object {
            private const val serialVersionUID: Long = 1L
        }

        /**
         * [privateKeyPem]이 log와 toString() 호출로 유출되지 않도록 막습니다.
         *
         * `SanitizingFunction` bean(T7.7)은 Actuator level에서 더 깊은 보호를 제공합니다.
         */
        override fun toString(): String =
            "CloudFront(domain=$distributionDomain, keyPairId=$keyPairId, " +
                "privateKeyPem=[REDACTED], privateKeyPath=$privateKeyPath, " +
                "defaultExpiry=$defaultExpiry, maxExpiry=$maxExpiry)"
    }
}
