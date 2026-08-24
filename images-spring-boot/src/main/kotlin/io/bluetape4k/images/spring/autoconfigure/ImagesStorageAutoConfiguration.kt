package io.bluetape4k.images.spring.autoconfigure

import io.bluetape4k.aws.spring.s3.S3Operations
import io.bluetape4k.images.spring.storage.ImageStorage
import io.bluetape4k.images.spring.storage.LocalImageStorage
import io.bluetape4k.images.spring.storage.s3.S3ImageStorage
import io.bluetape4k.images.spring.storage.s3.S3PathTransferOperations
import io.bluetape4k.images.spring.storage.s3.S3TransferOperationsAdapter
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
 * - nested [S3TransferCapabilityConfiguration]은 `S3TransferOperations` class와 bean이 있을 때만
 *   path-upload adapter를 등록합니다. transfer capability는 byte/object CRUD의 필수 조건이 아닙니다.
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
     * S3-backed storage입니다. [S3Operations]가 classpath에 있고 `bluetape4k.images.storage.backend=s3`일
     * 때만 활성화됩니다. transfer bean은 선택 사항이며,
     * 없을 때 [S3ImageStorage]의 [java.nio.file.Path] upload는 fail closed합니다.
     *
     * [S3Operations] 참조는 이 nested class 안에만 둡니다. 외부 `@AutoConfiguration` class가 class-load 시점에
     * `compileOnly` SDK type을 직접 참조하지 않게 하기 위해서입니다.
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
            transferOperations: ObjectProvider<S3PathTransferOperations>,
        ): ImageStorage {
            properties.bucket.requireNotBlank("bluetape4k.images.storage.bucket")
            requireHeadObjectSupport(operations)
            return S3ImageStorage(operations, properties, transferOperations.getIfAvailable())
        }

        /**
         * `S3Operations.headObject`는 compileOnly upstream 계약입니다. 구 runtime 구현체가
         * interface default만 물고 있는 경우 S3ImageStorage를 만들면 첫 호출에서
         * `UnsupportedOperationException`이 발생하므로, bean 생성 시점에 fail closed합니다.
         */
        private fun requireHeadObjectSupport(operations: S3Operations) {
            val implementationMethod = operations.javaClass.methods.firstOrNull { method ->
                method.name == "headObject" &&
                    method.parameterCount == 3 &&
                    method.parameterTypes[0] == String::class.java &&
                    method.parameterTypes[1] == String::class.java &&
                    method.declaringClass != S3Operations::class.java
            }
            check(implementationMethod != null) {
                "bluetape4k.images.storage.backend=s3 requires an S3Operations implementation " +
                    "with headObject support. Upgrade bluetape4k-aws-spring-boot before creating S3ImageStorage."
            }
        }
    }

    /**
     * AWS Transfer Manager가 제공하는 선택적 path-upload capability를 adapter로 연결합니다.
     *
     * `S3TransferOperations`를 method signature에 직접 노출하는 이 configuration은 classpath 조건을
     * 통과한 경우에만 평가됩니다. storage phase와 같은 property 조건을 적용해 storage가 비활성화된
     * consumer에는 불필요한 capability bean을 만들지 않습니다.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(
        name = [
            "io.bluetape4k.aws.spring.s3.S3TransferOperations",
            "software.amazon.awssdk.transfer.s3.model.CompletedFileUpload",
        ],
    )
    @ConditionalOnProperty(
        prefix = "bluetape4k.images.storage",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    class S3TransferCapabilityConfiguration {

        @Bean
        @ConditionalOnBean(type = ["io.bluetape4k.aws.spring.s3.S3TransferOperations"])
        @ConditionalOnMissingBean(S3PathTransferOperations::class)
        fun s3PathTransfer(
            transferOperations: io.bluetape4k.aws.spring.s3.S3TransferOperations,
        ): S3PathTransferOperations =
            S3TransferOperationsAdapter(transferOperations)
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
        private fun localImageStorageOf(properties: ImageStorageProperties): ImageStorage {
            val root = LocalImageStorage.provisionRoot(
                rootDir = Path.of(properties.local.rootDir),
                prefixes = properties.local.bootstrapPrefixes,
            )
            return LocalImageStorage(
                rootDir = root,
                maxSizeBytes = properties.maxSizeBytes,
            )
        }
    }
}
