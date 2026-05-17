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
 * Phase 4 — image storage health-indicator auto-configuration.
 *
 * ## Behavior / Contract
 * - Ordered after [ImagesStorageAutoConfiguration] via `afterName` so the [ImageStorage] bean
 *   is available when health-indicator registration is evaluated.
 * - Toggled by `bluetape4k.images.health.enabled` (default `true`).
 * - Requires at least one [ImageStorage] bean in the context.
 * - The nested [ReactiveHealthConfiguration] is the only place that references the
 *   `compileOnly` reactive health-indicator type (Spring Boot 4 splits
 *   `ReactiveHealthIndicator` into the `org.springframework.boot:spring-boot-health` module).
 *   The outer class keeps the type out of its signature so that omitting the dependency does
 *   not break class-loading.
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
     * Registers the [ImageStorageHealthIndicator] bean when
     * `org.springframework.boot.health.contributor.ReactiveHealthIndicator` is on the classpath.
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
