package io.bluetape4k.images.spring.autoconfigure

import io.bluetape4k.aws.spring.s3.S3Operations
import io.bluetape4k.images.spring.cdn.CdnReadSigner
import io.bluetape4k.images.spring.cdn.CdnWriteSigner
import org.assertj.core.api.Assertions.assertThat
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
            assertThat(ctx).doesNotHaveBean(CdnReadSigner::class.java)
        }
    }

    @Test
    fun `CDN configuration inactive when cdn enabled=false`() {
        contextRunner
            .withPropertyValues("bluetape4k.images.cdn.enabled=false")
            .run { ctx ->
                assertThat(ctx).doesNotHaveBean(CdnReadSigner::class.java)
            }
    }

    @Test
    fun `CdnProperties bean not registered when cdn disabled`() {
        contextRunner
            .withPropertyValues("bluetape4k.images.cdn.enabled=false")
            .run { ctx ->
                assertThat(ctx).doesNotHaveBean(CdnProperties::class.java)
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
                assertThat(ctx).doesNotHaveBean(CdnReadSigner::class.java)
                assertThat(ctx).doesNotHaveBean(CdnWriteSigner::class.java)
                assertThat(ctx).hasSingleBean(CdnProperties::class.java)
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
                assertThat(ctx).hasSingleBean(CdnReadSigner::class.java)
                assertThat(ctx.getBean(CdnReadSigner::class.java)).isSameAs(signer)
                assertThat(ctx).doesNotHaveBean(CdnWriteSigner::class.java)
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
                assertThat(ctx).hasSingleBean(CdnReadSigner::class.java)
                assertThat(ctx.getBean(CdnReadSigner::class.java)).isSameAs(signer)
            }
    }
}
