package io.bluetape4k.images.spring.autoconfigure

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class ImagesProcessingAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ImagesProcessingAutoConfiguration::class.java))

    @Test
    fun `registers ImageProcessingProperties with defaults`() {
        contextRunner.run { ctx ->
            assertThat(ctx).hasSingleBean(ImageProcessingProperties::class.java)
            val props = ctx.getBean(ImageProcessingProperties::class.java)
            props.enabled.shouldBeTrue()
            props.defaultFormat shouldBeEqualTo "jpeg"
            props.defaultQuality shouldBeEqualTo 85
        }
    }

    @Test
    fun `disabled when processing enabled=false`() {
        contextRunner
            .withPropertyValues("bluetape4k.images.processing.enabled=false")
            .run { ctx ->
                assertThat(ctx).doesNotHaveBean(ImageProcessingProperties::class.java)
            }
    }

    @Test
    fun `accepts custom quality`() {
        contextRunner
            .withPropertyValues("bluetape4k.images.processing.default-quality=70")
            .run { ctx ->
                assertThat(ctx).hasSingleBean(ImageProcessingProperties::class.java)
                val props = ctx.getBean(ImageProcessingProperties::class.java)
                props.defaultQuality shouldBeEqualTo 70
            }
    }

    @Test
    fun `accepts custom default format`() {
        contextRunner
            .withPropertyValues("bluetape4k.images.processing.default-format=png")
            .run { ctx ->
                val props = ctx.getBean(ImageProcessingProperties::class.java)
                props.defaultFormat shouldBeEqualTo "png"
            }
    }

    @Test
    fun `context fails when quality is out of range`() {
        contextRunner
            .withPropertyValues("bluetape4k.images.processing.default-quality=0")
            .run { ctx ->
                assertThat(ctx).hasFailed()
            }
    }
}
