package io.bluetape4k.images.spring.autoconfigure

import io.bluetape4k.aws.spring.s3.S3Operations
import io.bluetape4k.images.spring.cdn.CdnReadSigner
import io.bluetape4k.images.spring.cdn.CdnWriteSigner
import io.bluetape4k.images.spring.cdn.CloudFrontUrlSigner
import io.bluetape4k.images.spring.cdn.S3PreSignedUrlSigner
import io.bluetape4k.support.requireNotBlank
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Phase 3 — CDN URL signing auto-configuration.
 *
 * ## Behavior / Contract
 * - Binds [CdnProperties] under the `bluetape4k.images.cdn` prefix.
 * - Disabled by default — must be explicitly turned on via `bluetape4k.images.cdn.enabled=true`.
 * - Ordered after [ImagesStorageAutoConfiguration] via `afterName` (string FQCN).
 * - Nested [S3PresignCdnConfiguration] activates when [S3Operations] is on the classpath and the
 *   provider is `s3_presign` (default); its signer bean is created only when an [S3Operations] bean
 *   exists.
 * - Nested [CloudFrontCdnConfiguration] activates when `software.amazon.awssdk.services.cloudfront.CloudFrontUtilities`
 *   is on the classpath and the provider is `cloudfront`.
 * - Nested [CdnSanitizingConfiguration] registers a [CdnPropertySanitizingFunction] bean to redact
 *   private-key material in `/actuator/configprops` and `/actuator/env`. It activates only when
 *   `org.springframework.boot.actuate.endpoint.SanitizingFunction` is on the classpath.
 *
 * ### Maintainer note
 * Do not change `afterName` to `after` — it would cause `NoClassDefFoundError` when the consumer
 * omits `bluetape4k-aws-spring-boot`.
 */
@AutoConfiguration(
    afterName = [
        "io.bluetape4k.images.spring.autoconfigure.ImagesStorageAutoConfiguration",
    ],
)
@ConditionalOnProperty(
    prefix = "bluetape4k.images.cdn",
    name = ["enabled"],
    havingValue = "true",
)
@EnableConfigurationProperties(CdnProperties::class)
class ImagesCdnAutoConfiguration {

    /**
     * S3 presigned-URL CDN signer. Activated when [S3Operations] is on the classpath and the
     * provider is `s3_presign` (default).
     *
     * Reference to [S3Operations] is confined to this nested class so the outer
     * `@AutoConfiguration` class never directly references the `compileOnly` SDK type.
     *
     * The bean is registered with the concrete [S3PreSignedUrlSigner] return type — it satisfies
     * dependency injection for both [CdnReadSigner] and `CdnWriteSigner` interfaces automatically.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["io.bluetape4k.aws.spring.s3.S3Operations"])
    @ConditionalOnProperty(
        prefix = "bluetape4k.images.cdn",
        name = ["provider"],
        havingValue = "s3_presign",
        matchIfMissing = true,
        )
    class S3PresignCdnConfiguration {

        @Bean
        @ConditionalOnBean(type = ["io.bluetape4k.aws.spring.s3.S3Operations"])
        @ConditionalOnMissingBean(value = [CdnReadSigner::class, CdnWriteSigner::class])
        fun s3PreSignedUrlSigner(
            operations: S3Operations,
            storageProperties: ImageStorageProperties,
        ): S3PreSignedUrlSigner {
            val bucket = storageProperties.bucket.requireNotBlank("bluetape4k.images.storage.bucket")
            return S3PreSignedUrlSigner(
                operations = operations,
                bucket = bucket,
                keyPrefix = storageProperties.keyPrefix,
            )
        }
    }

    /**
     * CloudFront signed-URL signer. Activated when [software.amazon.awssdk.services.cloudfront.CloudFrontUtilities]
     * is on the classpath and the provider is `cloudfront`.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["software.amazon.awssdk.services.cloudfront.CloudFrontUtilities"])
    @ConditionalOnProperty(
        prefix = "bluetape4k.images.cdn",
        name = ["provider"],
        havingValue = "cloudfront",
    )
    class CloudFrontCdnConfiguration {

        @Bean
        @ConditionalOnMissingBean(CdnReadSigner::class)
        fun cloudFrontUrlSigner(properties: CdnProperties): CloudFrontUrlSigner =
            CloudFrontUrlSigner(properties.cloudfront)
    }

    /**
     * Registers a [CdnPropertySanitizingFunction] bean to redact private-key material from
     * Actuator endpoint payloads (T7.7). Active only when `spring-boot-actuator` is on the
     * classpath.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["org.springframework.boot.actuate.endpoint.SanitizingFunction"])
    class CdnSanitizingConfiguration {

        @Bean
        @ConditionalOnMissingBean(CdnPropertySanitizingFunction::class)
        fun cdnPropertySanitizingFunction(): CdnPropertySanitizingFunction =
            CdnPropertySanitizingFunction()
    }
}
