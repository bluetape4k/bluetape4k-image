package io.bluetape4k.images.spring.autoconfigure

import io.bluetape4k.aws.spring.s3.S3Operations
import io.bluetape4k.aws.spring.s3.S3TransferOperations
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
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Path

/**
 * Phase 2 image storage auto-configuration입니다.
 *
 * ## 동작 / 계약
 * - `bluetape4k.images.storage` prefix 아래 [ImageStorageProperties]를 bind합니다.
 * - `io.bluetape4k.aws.spring.s3.S3AutoConfiguration`과 [ImagesProcessingAutoConfiguration] 뒤에 오도록
 *   `afterName`(string FQCN)으로 ordering합니다. `S3AutoConfiguration`은 optional/compileOnly dependency이므로
 *   `after`를 쓰면 안 됩니다.
 * - `bluetape4k.images.storage.enabled`로 toggle됩니다(default `true`).
 * - nested [S3StorageConfiguration]은 [S3Operations]가 classpath에 있고 `backend=s3`일 때만 활성화됩니다.
 *   S3 storage bean은 [S3Operations] bean이 있을 때만 생성됩니다.
 * - nested [LocalStorageConfiguration]은 default/local backend를 처리합니다.
 * - nested [S3MissingOperationsConfiguration]은 `backend=s3`인데 [S3Operations] bean이 없을 때 startup을 실패시킵니다.
 *
 * ### Maintainer note
 * `afterName`을 `after`로 바꾸면 안 됩니다. 여기서 `S3AutoConfiguration`은 `compileOnly`입니다.
 * `KClass` form(`after = [...]`)은 consumer가 `bluetape4k-aws-spring-boot`를 생략하면
 * `NoClassDefFoundError`를 유발합니다.
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
     * S3-backed storage입니다. [S3Operations]와 [S3TransferOperations]가 classpath에 있고
     * `bluetape4k.images.storage.backend=s3`일 때만 활성화됩니다. transfer bean은 선택 사항이며,
     * 없을 때 [S3ImageStorage]의 [java.nio.file.Path] upload는 fail closed합니다.
     *
     * [S3Operations] 참조는 이 nested class 안에만 둡니다. 외부 `@AutoConfiguration` class가 class-load 시점에
     * `compileOnly` SDK type을 직접 참조하지 않게 하기 위해서입니다.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(
        name = [
            "io.bluetape4k.aws.spring.s3.S3Operations",
            "io.bluetape4k.aws.spring.s3.S3TransferOperations",
        ],
    )
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
            transferOperations: ObjectProvider<S3TransferOperations>,
        ): ImageStorage {
            properties.bucket.requireNotBlank("bluetape4k.images.storage.bucket")
            return S3ImageStorage(operations, properties, transferOperations.getIfAvailable())
        }
    }

    /**
     * default/local backend용 local filesystem storage입니다.
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
     * optional S3 integration이 [S3Operations] bean을 제공하지 않았는데 `backend=s3`인 경우를 위한 fail-fast guard입니다.
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
