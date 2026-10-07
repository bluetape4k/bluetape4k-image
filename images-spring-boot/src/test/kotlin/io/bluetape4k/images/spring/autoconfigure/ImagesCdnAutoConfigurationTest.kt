package io.bluetape4k.images.spring.autoconfigure

import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeSameInstanceAs
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.aws.spring.s3.S3Operations
import io.bluetape4k.images.spring.cdn.CdnReadSigner
import io.bluetape4k.images.spring.cdn.CdnWriteSigner
import io.bluetape4k.images.spring.storage.ImageStorage
import io.bluetape4k.logging.KLogging
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.getBean
import org.springframework.beans.factory.getBeanNamesForType
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class ImagesCdnAutoConfigurationTest {

    companion object: KLogging()

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                ImagesProcessingAutoConfiguration::class.java,
                ImagesStorageAutoConfiguration::class.java,
                ImagesCdnAutoConfiguration::class.java,
            )
        )

    @Test
    fun `no CDN signer registered by default (cdn disabled by default)`() {
        contextRunner.run { ctx ->
            ctx.getBeanNamesForType<CdnReadSigner>().shouldBeEmpty()
        }
    }

    @Test
    fun `CDN configuration inactive when cdn enabled=false`() {
        contextRunner
            .withPropertyValues("bluetape4k.images.cdn.enabled=false")
            .run { ctx ->
                ctx.getBeanNamesForType<CdnReadSigner>().shouldBeEmpty()
            }
    }

    @Test
    fun `CdnProperties bean not registered when cdn disabled`() {
        contextRunner
            .withPropertyValues("bluetape4k.images.cdn.enabled=false")
            .run { ctx ->
                ctx.getBeanNamesForType<CdnProperties>().shouldBeEmpty()
            }
    }

    @Test
    fun `s3 presign cdn backs off when S3Operations bean is absent`() {
        contextRunner
            .withPropertyValues(
                "bluetape4k.images.cdn.enabled=true",
                "bluetape4k.images.storage.bucket=images",
            )
            .run { ctx ->
                ctx.getBeanNamesForType<CdnReadSigner>().shouldBeEmpty()
                ctx.getBeanNamesForType<CdnWriteSigner>().shouldBeEmpty()
                ctx.getBeanNamesForType<CdnProperties>() shouldHaveSize 1
            }
    }

    @Test
    fun `s3 presign signer remains available when storage is disabled`() {
        val operations = mockk<S3Operations>(relaxed = true)

        contextRunner
            .withBean(S3Operations::class.java, { operations })
            .withPropertyValues(
                "bluetape4k.images.cdn.enabled=true",
                "bluetape4k.images.cdn.provider=s3_presign",
                "bluetape4k.images.storage.enabled=false",
                "bluetape4k.images.storage.bucket=images",
                "bluetape4k.images.storage.key-prefix=cdn",
            )
            .run { ctx ->
                ctx.startupFailure.shouldBeNull()

                ctx.getBeanNamesForType<CdnReadSigner>() shouldHaveSize 1
                ctx.getBeanNamesForType<CdnWriteSigner>() shouldHaveSize 1

                ctx.getBean<ImageStorageProperties>().bucket shouldBeEqualTo "images"
                ctx.getBean<ImageStorageProperties>().keyPrefix shouldBeEqualTo "cdn"

                ctx.getBeanNamesForType<ImageStorage>().shouldBeEmpty()
            }
    }

    @Test
    fun `user-provided CdnReadSigner backs off s3 presign signer`() {
        val operations = mockk<S3Operations>(relaxed = true)
        val signer = mockk<CdnReadSigner>(relaxed = true)

        contextRunner
            .withBean(S3Operations::class.java, { operations })
            .withBean(CdnReadSigner::class.java, { signer })
            .withPropertyValues(
                "bluetape4k.images.cdn.enabled=true",
                "bluetape4k.images.storage.bucket=images",
            )
            .run { ctx ->
                ctx.getBeanNamesForType<CdnReadSigner>() shouldHaveSize 1
                ctx.getBean<CdnReadSigner>() shouldBeSameInstanceAs signer
                ctx.getBeanNamesForType<CdnWriteSigner>().shouldBeEmpty()
            }
    }

    @Test
    fun `user-provided CdnReadSigner backs off cloudfront signer with missing credentials`() {
        val signer = mockk<CdnReadSigner>(relaxed = true)

        contextRunner
            .withBean(CdnReadSigner::class.java, { signer })
            .withPropertyValues(
                "bluetape4k.images.cdn.enabled=true",
                "bluetape4k.images.cdn.provider=cloudfront",
            )
            .run { ctx ->
                ctx.getBeanNamesForType<CdnReadSigner>() shouldHaveSize 1
                ctx.getBean<CdnReadSigner>() shouldBeSameInstanceAs signer
            }
    }
}
