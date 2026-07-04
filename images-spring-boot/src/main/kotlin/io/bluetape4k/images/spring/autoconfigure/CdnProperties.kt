package io.bluetape4k.images.spring.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties
import java.io.Serializable
import java.time.Duration

/**
 * Configuration properties for CDN URL signing.
 *
 * ## Behavior
 * - Bound to the `bluetape4k.images.cdn` prefix.
 * - CDN signing is disabled by default ([enabled] = false).
 * - [provider] selects between S3 presigned URLs and CloudFront signed URLs.
 * - [cloudfront] sub-properties are only relevant when [provider] is `cloudfront`.
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
     * CloudFront URL signing configuration.
     *
     * ## Behavior
     * - Exactly one of [privateKeyPem] or [privateKeyPath] must be set when CDN is enabled with CloudFront.
     * - Specifying both raises [IllegalArgumentException] at bean creation time.
     * - [privateKeyPem] inline is discouraged: the value resides in JVM heap and cannot be zeroed out.
     *   Prefer [privateKeyPath] pointing to a file-system secret or mounted Kubernetes secret.
     * - [toString] redacts [privateKeyPem] to prevent accidental exposure in logs and Actuator endpoints.
     *   A `SanitizingFunction` bean (registered by `ImagesCdnAutoConfiguration`) provides additional
     *   protection at the `/actuator/configprops` and `/actuator/env` level.
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
         * Prevents [privateKeyPem] from leaking in logs and toString() calls.
         *
         * The `SanitizingFunction` bean (T7.7) provides deeper Actuator-level protection.
         */
        override fun toString(): String =
            "CloudFront(domain=$distributionDomain, keyPairId=$keyPairId, " +
                "privateKeyPem=[REDACTED], privateKeyPath=$privateKeyPath, " +
                "defaultExpiry=$defaultExpiry, maxExpiry=$maxExpiry)"
    }
}
