package io.bluetape4k.images.examples.spring

import io.bluetape4k.images.spring.storage.ImageStorage
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wires the quickstart service to the auto-configured [ImageStorage] bean.
 */
@Configuration(proxyBeanMethods = false)
class LocalImageApiConfiguration {

    @Bean
    fun localImageApiService(storage: ImageStorage): LocalImageApiService =
        LocalImageApiService(storage)
}
