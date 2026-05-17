package io.bluetape4k.images.spring.autoconfigure

import io.bluetape4k.images.spring.cdn.CdnReadSigner
import org.assertj.core.api.Assertions.assertThat
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
}
