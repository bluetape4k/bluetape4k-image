package io.bluetape4k.images.spring.autoconfigure

import io.bluetape4k.aws.spring.s3.S3Operations
import io.bluetape4k.images.spring.storage.ImageStorage
import io.bluetape4k.images.spring.storage.LocalImageStorage
import io.bluetape4k.images.spring.storage.s3.S3ImageStorage
import io.bluetape4k.support.requireNotBlank
import jakarta.annotation.PostConstruct
import org.springframework.boot.autoconfigure.AutoConfiguration
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
 *   `backend=s3`. The bucket is validated via `@PostConstruct` (no-arg, JSR-250 compliant).
 * - Nested [LocalStorageConfiguration] is gated by `@ConditionalOnMissingBean(ImageStorage)` only —
 *   it acts as a guaranteed fallback even when `backend=s3` but [S3Operations] is absent.
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
     * `bluetape4k.images.storage.backend=s3`. The bucket is validated post-construction.
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
    class S3StorageConfiguration(
        private val properties: ImageStorageProperties,
    ) {

        @PostConstruct
        fun validateBucket() {
            properties.bucket.requireNotBlank("bluetape4k.images.storage.bucket")
        }

        @Bean
        @ConditionalOnMissingBean(ImageStorage::class)
        fun s3ImageStorage(operations: S3Operations): ImageStorage =
            S3ImageStorage(operations, properties)
    }

    /**
     * Local filesystem fallback. Always registered when no other [ImageStorage] bean is present.
     *
     * No `backend=local` predicate here — if `backend=s3` is configured but [S3Operations] is not
     * on the classpath, the S3 nested configuration is skipped and this fallback still provides a
     * working storage bean.
     */
    @Configuration(proxyBeanMethods = false)
    class LocalStorageConfiguration {

        @Bean
        @ConditionalOnMissingBean(ImageStorage::class)
        fun localImageStorage(properties: ImageStorageProperties): ImageStorage =
            LocalImageStorage(
                rootDir = Path.of(properties.local.rootDir),
                maxSizeBytes = properties.maxSizeBytes,
            )
    }
}
