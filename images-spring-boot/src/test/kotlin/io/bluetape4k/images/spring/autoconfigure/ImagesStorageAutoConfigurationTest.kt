package io.bluetape4k.images.spring.autoconfigure

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldBeSameInstanceAs
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.aws.spring.s3.S3Operations
import io.bluetape4k.images.spring.storage.ImageStorage
import io.bluetape4k.images.spring.storage.LocalImageStorage
import io.bluetape4k.images.spring.storage.s3.S3ImageStorage
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class ImagesStorageAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                ImagesProcessingAutoConfiguration::class.java,
                ImagesStorageAutoConfiguration::class.java,
            )
        )

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
        val customStorage = mockk<ImageStorage>(relaxed = true)
        contextRunner
            .withBean(ImageStorage::class.java, { customStorage })
            .run { ctx ->
                ctx.getBeanNamesForType(ImageStorage::class.java).size shouldBeEqualTo 1
                ctx.getBean(ImageStorage::class.java) shouldBeSameInstanceAs customStorage
            }
    }

    @Test
    fun `s3 backend falls back to local storage when S3Operations bean is absent`() {
        contextRunner
            .withPropertyValues(
                "bluetape4k.images.storage.backend=s3",
                "bluetape4k.images.storage.bucket=images",
            )
            .run { ctx ->
                ctx.getBeanNamesForType(ImageStorage::class.java).size shouldBeEqualTo 1
                ctx.getBean(ImageStorage::class.java) shouldBeInstanceOf LocalImageStorage::class
            }
    }

    @Test
    fun `s3 backend creates S3 storage when S3Operations bean and bucket are present`() {
        val operations = mockk<S3Operations>(relaxed = true)

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
    fun `user-provided ImageStorage backs off s3 storage even when bucket is absent`() {
        val customStorage = mockk<ImageStorage>(relaxed = true)

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
    fun `accepts custom maxSizeBytes property`() {
        contextRunner
            .withPropertyValues("bluetape4k.images.storage.max-size-bytes=1048576")
            .run { ctx ->
                val props = ctx.getBean(ImageStorageProperties::class.java)
                props.maxSizeBytes shouldBeEqualTo 1_048_576L
            }
    }
}
