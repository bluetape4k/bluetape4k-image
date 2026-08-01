package io.bluetape4k.images.spring.autoconfigure

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeSameInstanceAs
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
        // ImageStorage bean이 생성되지 않도록 storage를 비활성화합니다.
        // ImagesHealthAutoConfiguration은 @ConditionalOnBean(ImageStorage)을 가지므로 활성화되지 않습니다.
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
        // @ConditionalOnMissingBean(name="imageStorageHealthIndicator")를 만족시키는 named bean을 제공합니다.
        // auto-configuration은 두 번째 instance를 등록하면 안 됩니다.
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
                ctx.getBean(ImageStorageHealthIndicator::class.java) shouldBeSameInstanceAs customIndicator
            }
    }

    @Test
    fun `uses healthProbeKey from ImageStorageProperties`() {
        contextRunner
            .withPropertyValues("bluetape4k.images.storage.health-probe-key=.my-probe")
            .run { ctx ->
                assertThat(ctx).hasSingleBean(ImageStorageHealthIndicator::class.java)
                val props = ctx.getBean(ImageStorageProperties::class.java)
                props.healthProbeKey shouldBeEqualTo ".my-probe"
            }
    }
}
