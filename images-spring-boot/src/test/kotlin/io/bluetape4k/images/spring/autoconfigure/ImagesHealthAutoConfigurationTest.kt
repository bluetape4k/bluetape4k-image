package io.bluetape4k.images.spring.autoconfigure

import io.bluetape4k.images.spring.health.ImageStorageHealthIndicator
import io.bluetape4k.images.spring.storage.ImageStorage
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class ImagesHealthAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                ImagesProcessingAutoConfiguration::class.java,
                ImagesStorageAutoConfiguration::class.java,
                ImagesHealthAutoConfiguration::class.java,
            )
        )

    @Test
    fun `registers ImageStorageHealthIndicator when ImageStorage bean present`() {
        contextRunner.run { ctx ->
            assertThat(ctx).hasSingleBean(ImageStorageHealthIndicator::class.java)
        }
    }

    @Test
    fun `does not register when no ImageStorage bean present`() {
        // Disable storage so no ImageStorage bean is created;
        // ImagesHealthAutoConfiguration has @ConditionalOnBean(ImageStorage) and will not activate.
        contextRunner
            .withPropertyValues("bluetape4k.images.storage.enabled=false")
            .run { ctx ->
                assertThat(ctx).doesNotHaveBean(ImageStorageHealthIndicator::class.java)
            }
    }

    @Test
    fun `disabled when health enabled=false`() {
        contextRunner
            .withPropertyValues("bluetape4k.images.health.enabled=false")
            .run { ctx ->
                assertThat(ctx).doesNotHaveBean(ImageStorageHealthIndicator::class.java)
            }
    }

    @Test
    fun `user-provided health indicator bean takes precedence`() {
        // Supply a named bean that satisfies @ConditionalOnMissingBean(name="imageStorageHealthIndicator");
        // auto-configuration must not register a second instance.
        val mockStorage = mockk<ImageStorage>(relaxed = true)
        val customIndicator = ImageStorageHealthIndicator(
            storage = mockStorage,
            probeKey = ".custom-probe",
        )
        contextRunner
            .withBean(
                "imageStorageHealthIndicator",
                ImageStorageHealthIndicator::class.java,
                { customIndicator },
            )
            .run { ctx ->
                assertThat(ctx).hasSingleBean(ImageStorageHealthIndicator::class.java)
                assertThat(ctx.getBean(ImageStorageHealthIndicator::class.java))
                    .isSameAs(customIndicator)
            }
    }

    @Test
    fun `uses healthProbeKey from ImageStorageProperties`() {
        contextRunner
            .withPropertyValues("bluetape4k.images.storage.health-probe-key=.my-probe")
            .run { ctx ->
                assertThat(ctx).hasSingleBean(ImageStorageHealthIndicator::class.java)
                val props = ctx.getBean(ImageStorageProperties::class.java)
                assertThat(props.healthProbeKey).isEqualTo(".my-probe")
            }
    }
}
