package io.bluetape4k.images.spring.autoconfigure

import io.bluetape4k.images.spring.health.ImageStorageHealthIndicator
import io.bluetape4k.images.spring.storage.ImageStorage
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Phase 4 image storage health-indicator auto-configuration입니다.
 *
 * ## 동작 / 계약
 * - health-indicator registration 평가 시점에 [ImageStorage] bean을 사용할 수 있도록 [ImagesStorageAutoConfiguration]
 *   뒤에 `afterName`으로 ordering합니다.
 * - `bluetape4k.images.health.enabled`로 toggle됩니다(default `true`).
 * - context에 최소 하나의 [ImageStorage] bean이 필요합니다.
 * - nested [ReactiveHealthConfiguration]만 `compileOnly` reactive health-indicator type을 참조합니다.
 *   Spring Boot 4는 `ReactiveHealthIndicator`를 `org.springframework.boot:spring-boot-health` module로 분리합니다.
 *   외부 class signature에는 이 type을 두지 않아 dependency를 생략한 consumer에서도 class-loading이 깨지지 않게 합니다.
 */
@AutoConfiguration(
    afterName = [
        "io.bluetape4k.images.spring.autoconfigure.ImagesStorageAutoConfiguration",
    ],
)
@ConditionalOnProperty(
    prefix = "bluetape4k.images.health",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
@ConditionalOnBean(ImageStorage::class)
class ImagesHealthAutoConfiguration {

    /**
     * `org.springframework.boot.health.contributor.ReactiveHealthIndicator`가 classpath에 있을 때
     * [ImageStorageHealthIndicator] bean을 등록합니다.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["org.springframework.boot.health.contributor.ReactiveHealthIndicator"])
    class ReactiveHealthConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = ["imageStorageHealthIndicator"])
        fun imageStorageHealthIndicator(
            storage: ImageStorage,
            properties: ImageStorageProperties,
        ): ImageStorageHealthIndicator =
            ImageStorageHealthIndicator(
                storage = storage,
                probeKey = properties.healthProbeKey,
            )
    }
}
