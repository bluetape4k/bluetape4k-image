package io.bluetape4k.images.examples.spring

import io.bluetape4k.images.spring.storage.ImageStorage
import io.bluetape4k.support.requirePositiveNumber
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.Serializable

/**
 * quickstart image API의 upload safety limit입니다.
 */
@ConfigurationProperties(prefix = "example.image")
data class ImageApiProperties(
    val maxInputBytes: Long = 10L * 1024L * 1024L,
    val maxInputPixels: Long = 16_777_216L,
    val maxInputSide: Int = 8_192,
) : Serializable {

    init {
        maxInputBytes.requirePositiveNumber("maxInputBytes")
        maxInputPixels.requirePositiveNumber("maxInputPixels")
        maxInputSide.requirePositiveNumber("maxInputSide")
    }

    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * quickstart service를 auto-configured [ImageStorage] bean에 연결합니다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ImageApiProperties::class)
class LocalImageApiConfiguration {

    @Bean
    fun localImageApiService(
        storage: ImageStorage,
        properties: ImageApiProperties,
    ): LocalImageApiService =
        LocalImageApiService(storage, properties)
}
