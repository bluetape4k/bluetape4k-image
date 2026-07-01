package io.bluetape4k.images.spring.autoconfigure

import io.bluetape4k.aws.spring.s3.S3Operations
import io.bluetape4k.images.spring.storage.ImageStorage
import io.bluetape4k.images.spring.storage.LocalImageStorage
import io.bluetape4k.images.spring.storage.s3.S3ImageStorage
import io.bluetape4k.support.requireNotBlank
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Path

/**
 * Phase 2 — image storage auto-configuration.
 *
 * ## Behavior / Contract
 * - Binds [ImageStorageProperties] under the `bluetape4k.images.storage` prefix.
 * - Ordered after `io.bluetape4k.aws.spring.s3.S3AutoConfiguration` and
 *   [ImagesProcessingAutoConfiguration] via `afterName` (string FQCN) — never `after`, because
 *   `S3AutoConfiguration` is an optional/compileOnly dependency.
 * - Toggled by `bluetape4k.images.storage.enabled` (default `true`).
 * - Nested [S3StorageConfiguration] is activated only when [S3Operations] is on the classpath and
 *   `backend=s3`. The S3 storage bean is created only when an [S3Operations] bean exists.
 * - Nested [LocalStorageConfiguration] handles the default/local backend.
 * - Nested [S3MissingOperationsConfiguration] fails startup when `backend=s3` but no
 *   [S3Operations] bean is available.
 *
 * ### Maintainer note
 * Do not change `afterName` to `after`. `S3AutoConfiguration` is `compileOnly` here; the `KClass`
 * form (`after = [...]`) would trigger `NoClassDefFoundError` when the consumer omits
 * `bluetape4k-aws-spring-boot`.
 */
@AutoConfiguration(
    afterName = [
        "io.bluetape4k.aws.spring.s3.S3AutoConfiguration",
        "io.bluetape4k.images.spring.autoconfigure.ImagesProcessingAutoConfiguration",
    ],
)
@ConditionalOnProperty(
    prefix = "bluetape4k.images.storage",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
@EnableConfigurationProperties(ImageStorageProperties::class)
class ImagesStorageAutoConfiguration {

    /**
     * S3-backed storage. Activated only when [S3Operations] is on the classpath and
     * `bluetape4k.images.storage.backend=s3`.
     *
     * Reference to [S3Operations] is confined to this nested class so that the outer
     * `@AutoConfiguration` class never directly references the `compileOnly` SDK type at
     * class-load time.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["io.bluetape4k.aws.spring.s3.S3Operations"])
    @ConditionalOnProperty(
        prefix = "bluetape4k.images.storage",
        name = ["backend"],
        havingValue = "s3",
    )
    class S3StorageConfiguration {

        @Bean
        @ConditionalOnBean(type = ["io.bluetape4k.aws.spring.s3.S3Operations"])
        @ConditionalOnMissingBean(ImageStorage::class)
        fun s3ImageStorage(
            operations: S3Operations,
            properties: ImageStorageProperties,
        ): ImageStorage {
            properties.bucket.requireNotBlank("bluetape4k.images.storage.bucket")
            return S3ImageStorage(operations, properties)
        }
    }

    /**
     * Local filesystem storage for the default/local backend.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
        prefix = "bluetape4k.images.storage",
        name = ["backend"],
        havingValue = "local",
        matchIfMissing = true,
    )
    class LocalStorageConfiguration {

        @Bean
        @ConditionalOnMissingBean(ImageStorage::class)
        fun localImageStorage(properties: ImageStorageProperties): ImageStorage =
            localImageStorageOf(properties)
    }

    /**
     * Fail-fast guard for `backend=s3` when the optional S3 integration did not provide an
     * [S3Operations] bean.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
        prefix = "bluetape4k.images.storage",
        name = ["backend"],
        havingValue = "s3",
    )
    @ConditionalOnMissingBean(type = ["io.bluetape4k.aws.spring.s3.S3Operations"])
    class S3MissingOperationsConfiguration {

        @Bean
        @ConditionalOnMissingBean(ImageStorage::class)
        fun missingS3OperationsImageStorage(): ImageStorage =
            throw IllegalStateException(
                "bluetape4k.images.storage.backend=s3 requires an " +
                    "io.bluetape4k.aws.spring.s3.S3Operations bean. " +
                    "Add bluetape4k-aws-spring-boot S3 auto-configuration or provide an ImageStorage bean.",
            )
    }

    companion object {
        private fun localImageStorageOf(properties: ImageStorageProperties): ImageStorage =
            LocalImageStorage(
                rootDir = Path.of(properties.local.rootDir),
                maxSizeBytes = properties.maxSizeBytes,
            )
    }
}
