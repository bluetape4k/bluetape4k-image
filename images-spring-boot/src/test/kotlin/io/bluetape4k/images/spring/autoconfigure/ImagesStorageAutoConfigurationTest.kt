package io.bluetape4k.images.spring.autoconfigure

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldBeSameInstanceAs
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.aws.spring.s3.S3Operations
import io.bluetape4k.aws.spring.s3.S3TransferOperations
import io.bluetape4k.images.spring.storage.ImageStorage
import io.bluetape4k.images.spring.storage.LocalImageStorage
import io.bluetape4k.images.spring.storage.s3.S3ImageStorage
import io.bluetape4k.images.spring.storage.s3.S3PathTransferOperations
import io.bluetape4k.images.spring.storage.s3.S3TransferOperationsAdapter
import io.mockk.clearMocks
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

class ImagesStorageAutoConfigurationTest {

    private val customStorage = mockk<ImageStorage>(relaxed = true)
    private val operations = mockk<S3Operations>(relaxed = true)
    private val transferOperations = mockk<S3TransferOperations>(relaxed = true)

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                ImagesProcessingAutoConfiguration::class.java,
                ImagesStorageAutoConfiguration::class.java,
            )
        )

    @BeforeEach
    fun setUp() {
        clearMocks(customStorage, operations, transferOperations)
    }

    @Test
    fun `registers LocalImageStorage by default`() {
        contextRunner.run { ctx ->
            ctx.getBeanNamesForType(ImageStorage::class.java).size shouldBeEqualTo 1
            ctx.getBean(ImageStorage::class.java) shouldBeInstanceOf LocalImageStorage::class
        }
    }

    @Test
    fun `registers ImageStorageProperties with defaults`() {
        contextRunner.run { ctx ->
            ctx.getBeanNamesForType(ImageStorageProperties::class.java).size shouldBeEqualTo 1
            val props = ctx.getBean(ImageStorageProperties::class.java)
            props.enabled.shouldBeTrue()
            props.backend shouldBeEqualTo ImageStorageProperties.Backend.LOCAL
            props.maxSizeBytes shouldBeEqualTo 50 * 1024 * 1024L
        }
    }

    @Test
    fun `disabled when storage enabled=false`() {
        contextRunner
            .withPropertyValues("bluetape4k.images.storage.enabled=false")
            .run { ctx ->
                ctx.getBeanNamesForType(ImageStorage::class.java).isEmpty().shouldBeTrue()
                ctx.getBeanNamesForType(ImageStorageProperties::class.java).isEmpty().shouldBeTrue()
            }
    }

    @Test
    fun `user-provided ImageStorage bean takes precedence`() {
        contextRunner
            .withBean(ImageStorage::class.java, { customStorage })
            .run { ctx ->
                ctx.getBeanNamesForType(ImageStorage::class.java).size shouldBeEqualTo 1
                ctx.getBean(ImageStorage::class.java) shouldBeSameInstanceAs customStorage
            }
    }

    @Test
    fun `s3 backend fails fast when S3Operations bean is absent`() {
        contextRunner
            .withPropertyValues(
                "bluetape4k.images.storage.backend=s3",
                "bluetape4k.images.storage.bucket=images",
            )
            .run { ctx ->
                val failure = ctx.startupFailure.shouldNotBeNull()
                rootCauseMessage(failure) shouldContain "S3Operations"
                rootCauseMessage(failure) shouldContain "bluetape4k.images.storage.backend=s3"
            }
    }

    @Test
    fun `s3 backend creates S3 storage when S3Operations bean and bucket are present`() {
        contextRunner
            .withBean(S3Operations::class.java, { operations })
            .withPropertyValues(
                "bluetape4k.images.storage.backend=s3",
                "bluetape4k.images.storage.bucket=images",
            )
            .run { ctx ->
                ctx.getBeanNamesForType(ImageStorage::class.java).size shouldBeEqualTo 1
                ctx.getBean(ImageStorage::class.java) shouldBeInstanceOf S3ImageStorage::class
            }
    }

    @Test
    fun `s3 byte CRUD remains available when transfer class is absent`() {
        contextRunner
            .withClassLoader(FilteredClassLoader("io.bluetape4k.aws.spring.s3.S3TransferOperations"))
            .withBean(S3Operations::class.java, { operations })
            .withPropertyValues(
                "bluetape4k.images.storage.backend=s3",
                "bluetape4k.images.storage.bucket=images",
            )
            .run { ctx ->
                ctx.startupFailure.shouldBeNull()
                ctx.getBeanNamesForType(ImageStorage::class.java).size shouldBeEqualTo 1
                ctx.getBean(ImageStorage::class.java) shouldBeInstanceOf S3ImageStorage::class
                ctx.getBeanNamesForType(S3PathTransferOperations::class.java).isEmpty().shouldBeTrue()
            }
    }

    @Test
    fun `s3 storage remains available when transfer bean is absent`() {
        contextRunner
            .withBean(S3Operations::class.java, { operations })
            .withPropertyValues(
                "bluetape4k.images.storage.backend=s3",
                "bluetape4k.images.storage.bucket=images",
            )
            .run { ctx ->
                ctx.startupFailure.shouldBeNull()
                ctx.getBean(ImageStorage::class.java) shouldBeInstanceOf S3ImageStorage::class
                ctx.getBeanNamesForType(S3PathTransferOperations::class.java).isEmpty().shouldBeTrue()
            }
    }

    @Test
    fun `s3 transfer bean is adapted as an optional path capability`() {
        contextRunner
            .withBean(S3Operations::class.java, { operations })
            .withBean(S3TransferOperations::class.java, { transferOperations })
            .withPropertyValues(
                "bluetape4k.images.storage.backend=s3",
                "bluetape4k.images.storage.bucket=images",
            )
            .run { ctx ->
                ctx.startupFailure.shouldBeNull()
                ctx.getBean(S3PathTransferOperations::class.java) shouldBeInstanceOf S3TransferOperationsAdapter::class
                ctx.getBean(ImageStorage::class.java) shouldBeInstanceOf S3ImageStorage::class
            }
    }

    @Test
    fun `s3 backend reports a clear diagnostic when operations class is absent`() {
        contextRunner
            .withClassLoader(FilteredClassLoader("io.bluetape4k.aws.spring.s3.S3Operations"))
            .withPropertyValues(
                "bluetape4k.images.storage.backend=s3",
                "bluetape4k.images.storage.bucket=images",
            )
            .run { ctx ->
                val failure = ctx.startupFailure.shouldNotBeNull()
                rootCauseMessage(failure) shouldContain "S3Operations"
                rootCauseMessage(failure) shouldContain "bluetape4k.images.storage.backend=s3"
            }
    }

    @Test
    fun `s3 backend fails closed when implementation only has the default HEAD method`() {
        contextRunner
            .withBean(S3Operations::class.java, { legacyJavaS3Operations() })
            .withPropertyValues(
                "bluetape4k.images.storage.backend=s3",
                "bluetape4k.images.storage.bucket=images",
            )
            .run { ctx ->
                val failure = ctx.startupFailure.shouldNotBeNull()
                rootCauseMessage(failure) shouldContain "headObject support"
                rootCauseMessage(failure) shouldContain "Upgrade bluetape4k-aws-spring-boot"
            }
    }

    @Test
    fun `user-provided ImageStorage backs off s3 storage even when bucket is absent`() {
        contextRunner
            .withBean(ImageStorage::class.java, { customStorage })
            .withPropertyValues("bluetape4k.images.storage.backend=s3")
            .run { ctx ->
                ctx.getBeanNamesForType(ImageStorage::class.java).size shouldBeEqualTo 1
                ctx.getBean(ImageStorage::class.java) shouldBeSameInstanceAs customStorage
            }
    }

    @Test
    fun `accepts custom local rootDir property`() {
        contextRunner
            .withPropertyValues("bluetape4k.images.storage.local.root-dir=/tmp/custom-images")
            .run { ctx ->
                val props = ctx.getBean(ImageStorageProperties::class.java)
                props.local.rootDir shouldBeEqualTo "/tmp/custom-images"
            }
    }

    @Test
    fun `provisions only explicitly configured local bootstrap prefixes`() {
        val root = Files.createTempDirectory("images-storage-bootstrap-test")
        try {
            contextRunner
                .withPropertyValues(
                    "bluetape4k.images.storage.local.root-dir=$root",
                    "bluetape4k.images.storage.local.bootstrap-prefixes=originals,thumbnails/nested",
                )
                .run {
                    Files.isDirectory(root.resolve("originals")).shouldBeTrue()
                    Files.isDirectory(root.resolve("thumbnails/nested")).shouldBeTrue()
                    Files.exists(root.resolve("unconfigured")).shouldBeFalse()
                }
        } finally {
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    @Test
    fun `accepts custom maxSizeBytes property`() {
        contextRunner
            .withPropertyValues("bluetape4k.images.storage.max-size-bytes=1048576")
            .run { ctx ->
                val props = ctx.getBean(ImageStorageProperties::class.java)
                props.maxSizeBytes shouldBeEqualTo 1_048_576L
            }
    }

    private fun rootCauseMessage(error: Throwable): String {
        var current = error
        while (true) {
            current = current.cause ?: return current.message.orEmpty()
        }
    }

    /** Java fixture는 atomicfu test output에 포함되지 않으므로 해당 output에서 old ABI를 직접 로드합니다. */
    private fun legacyJavaS3Operations(): S3Operations {
        val testClasses = Path.of("build/classes/java/test").toAbsolutePath().toUri().toURL()
        val loader = URLClassLoader(arrayOf(testClasses), S3Operations::class.java.classLoader)
        return loader
            .loadClass("io.bluetape4k.images.spring.autoconfigure.LegacyJavaS3Operations")
            .getDeclaredConstructor()
            .newInstance() as S3Operations
    }

}
