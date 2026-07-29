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
 * Phase 3 CDN URL signing auto-configuration입니다.
 *
 * ## 동작 / 계약
 * - `bluetape4k.images.cdn` prefix 아래 [CdnProperties]를 bind합니다.
 * - 기본값은 비활성화입니다. `bluetape4k.images.cdn.enabled=true`로 명시적으로 켜야 합니다.
 * - [ImagesStorageAutoConfiguration] 뒤에 오도록 `afterName`(string FQCN)으로 ordering합니다.
 * - nested [S3PresignCdnConfiguration]은 [S3Operations]가 classpath에 있고 provider가 `s3_presign`(default)일 때
 *   활성화됩니다. signer bean은 [S3Operations] bean이 있을 때만 생성됩니다.
 * - nested [CloudFrontCdnConfiguration]은 `software.amazon.awssdk.services.cloudfront.CloudFrontUtilities`가
 *   classpath에 있고 provider가 `cloudfront`일 때 활성화됩니다.
 * - nested [CdnSanitizingConfiguration]은 `/actuator/configprops`와 `/actuator/env`의 private-key material을
 *   redaction하는 [CdnPropertySanitizingFunction] bean을 등록합니다.
 *   `org.springframework.boot.actuate.endpoint.SanitizingFunction`이 classpath에 있을 때만 활성화됩니다.
 *
 * ### Maintainer note
 * `afterName`을 `after`로 바꾸면 안 됩니다. consumer가 `bluetape4k-aws-spring-boot`를 빼면
 * `NoClassDefFoundError`가 발생할 수 있습니다.
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
     * S3 presigned-URL CDN signer입니다. [S3Operations]가 classpath에 있고 provider가 `s3_presign`(default)일 때
     * 활성화됩니다.
     *
     * [S3Operations] 참조는 이 nested class 안에만 둡니다. 외부 `@AutoConfiguration` class가 `compileOnly` SDK type을
     * 직접 참조하지 않게 하기 위해서입니다.
     *
     * bean은 concrete [S3PreSignedUrlSigner] return type으로 등록됩니다. 이 type은 [CdnReadSigner]와
     * `CdnWriteSigner` interface에 대한 dependency injection을 자동으로 충족합니다.
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
     * CloudFront signed-URL signer입니다. [software.amazon.awssdk.services.cloudfront.CloudFrontUtilities]가
     * classpath에 있고 provider가 `cloudfront`일 때 활성화됩니다.
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
     * Actuator endpoint payload의 private-key material을 redaction하기 위해 [CdnPropertySanitizingFunction]
     * bean을 등록합니다(T7.7). `spring-boot-actuator`가 classpath에 있을 때만 활성화됩니다.
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
