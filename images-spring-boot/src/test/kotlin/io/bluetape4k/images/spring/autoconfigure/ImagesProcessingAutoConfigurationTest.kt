package io.bluetape4k.images.spring.autoconfigure

import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.getBean
import org.springframework.beans.factory.getBeansOfType
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class ImagesProcessingAutoConfigurationTest {

    companion object: KLogging()

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ImagesProcessingAutoConfiguration::class.java))

    @Test
    fun `registers ImageProcessingProperties with defaults`() {
        contextRunner.run { ctx ->
            ctx.getBeansOfType<ImageProcessingProperties>() shouldHaveSize 1
            val props = ctx.getBean<ImageProcessingProperties>()

            log.debug { "props=$props" }
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
                ctx.getBeansOfType<ImageProcessingProperties>().shouldBeEmpty()
            }
    }

    @Test
    fun `accepts custom quality`() {
        contextRunner
            .withPropertyValues("bluetape4k.images.processing.default-quality=70")
            .run { ctx ->
                ctx.getBeansOfType<ImageProcessingProperties>() shouldHaveSize 1
                val props = ctx.getBean<ImageProcessingProperties>()
                log.debug { "props=$props" }
                props.defaultQuality shouldBeEqualTo 70
            }
    }

    @Test
    fun `accepts custom default format`() {
        contextRunner
            .withPropertyValues("bluetape4k.images.processing.default-format=png")
            .run { ctx ->
                val props = ctx.getBean<ImageProcessingProperties>()
                log.debug { "props=$props" }
                props.defaultFormat shouldBeEqualTo "png"
            }
    }

    @Test
    fun `context fails when quality is out of range`() {
        contextRunner
            .withPropertyValues("bluetape4k.images.processing.default-quality=0")
            .run { ctx ->
                log.debug { "ctx=$ctx" }
                ctx.startupFailure.shouldNotBeNull()
            }
    }
}
