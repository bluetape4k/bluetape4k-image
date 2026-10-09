package io.bluetape4k.images.examples.spring.config

import io.bluetape4k.images.examples.spring.service.LocalImageApiService
import io.bluetape4k.images.spring.storage.ImageStorage
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


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
