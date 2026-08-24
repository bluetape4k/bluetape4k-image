package io.bluetape4k.images.spring.autoconfigure

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeSameInstanceAs
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.aws.spring.s3.S3Operations
import io.bluetape4k.images.spring.cdn.CdnReadSigner
import io.bluetape4k.images.spring.cdn.CdnWriteSigner
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class ImagesCdnAutoConfigurationTest {

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
            ctx.getBeanNamesForType(CdnReadSigner::class.java).isEmpty().shouldBeTrue()
        }
    }

    @Test
    fun `CDN configuration inactive when cdn enabled=false`() {
        contextRunner
            .withPropertyValues("bluetape4k.images.cdn.enabled=false")
            .run { ctx ->
                ctx.getBeanNamesForType(CdnReadSigner::class.java).isEmpty().shouldBeTrue()
            }
    }

    @Test
    fun `CdnProperties bean not registered when cdn disabled`() {
        contextRunner
            .withPropertyValues("bluetape4k.images.cdn.enabled=false")
            .run { ctx ->
                ctx.getBeanNamesForType(CdnProperties::class.java).isEmpty().shouldBeTrue()
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
                ctx.getBeanNamesForType(CdnReadSigner::class.java).isEmpty().shouldBeTrue()
                ctx.getBeanNamesForType(CdnWriteSigner::class.java).isEmpty().shouldBeTrue()
                ctx.getBeanNamesForType(CdnProperties::class.java).size shouldBeEqualTo 1
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
                ctx.getBeanNamesForType(CdnReadSigner::class.java).size shouldBeEqualTo 1
                ctx.getBeanNamesForType(CdnWriteSigner::class.java).size shouldBeEqualTo 1
                ctx.getBean(ImageStorageProperties::class.java).bucket shouldBeEqualTo "images"
                ctx.getBean(ImageStorageProperties::class.java).keyPrefix shouldBeEqualTo "cdn"
                ctx.getBeanNamesForType(io.bluetape4k.images.spring.storage.ImageStorage::class.java)
                    .isEmpty()
                    .shouldBeTrue()
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
                ctx.getBeanNamesForType(CdnReadSigner::class.java).size shouldBeEqualTo 1
                ctx.getBean(CdnReadSigner::class.java) shouldBeSameInstanceAs signer
                ctx.getBeanNamesForType(CdnWriteSigner::class.java).isEmpty().shouldBeTrue()
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
                ctx.getBeanNamesForType(CdnReadSigner::class.java).size shouldBeEqualTo 1
                ctx.getBean(CdnReadSigner::class.java) shouldBeSameInstanceAs signer
            }
    }
}
