package io.bluetape4k.images.spring.autoconfigure

import io.bluetape4k.images.spring.metrics.ImageStorageMetricsBeanPostProcessor
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Phase 5 — Micrometer decoration auto-configuration.
 *
 * ## Behavior / Contract
 * - Ordered after [ImagesStorageAutoConfiguration] via `afterName` so the [ImageStorage] bean
 *   is available when the [BeanPostProcessor] is registered.
 * - Toggled by `bluetape4k.images.metrics.enabled` (default `true`).
 * - Activated only when `io.micrometer.core.instrument.MeterRegistry` is on the classpath; the
 *   outer class therefore never directly references the `compileOnly` Micrometer type — the
 *   [MetricsDecorationConfiguration] nested class is the lone integration point.
 * - Registers an [ImageStorageMetricsBeanPostProcessor] that wraps every [io.bluetape4k.images.spring.storage.ImageStorage]
 *   bean with [io.bluetape4k.images.spring.metrics.MetricImageStorage] during context startup.
 *
 * ### Maintainer note
 * The `@Bean` factory for the [BeanPostProcessor] is intentionally an instance method (not
 * `@JvmStatic`). Spring may emit an INFO log when a non-static `@Bean` returns a
 * [BeanPostProcessor]; this is acceptable here because the post-processor depends on a runtime
 * Spring-managed [MeterRegistry] that is itself wired through normal `@Autowired` lookup.
 */
@AutoConfiguration(
    afterName = [
        "io.bluetape4k.images.spring.autoconfigure.ImagesStorageAutoConfiguration",
    ],
)
@ConditionalOnProperty(
    prefix = "bluetape4k.images.metrics",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class ImagesMetricsAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["io.micrometer.core.instrument.MeterRegistry"])
    class MetricsDecorationConfiguration {

        @Bean
        fun imageStorageMetricsBeanPostProcessor(registry: MeterRegistry): BeanPostProcessor =
            ImageStorageMetricsBeanPostProcessor(registry)
    }
}
