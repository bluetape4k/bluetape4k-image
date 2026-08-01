package io.bluetape4k.images.spring.autoconfigure

import io.bluetape4k.images.spring.metrics.ImageStorageMetricsBeanPostProcessor
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Phase 5 Micrometer decoration auto-configuration입니다.
 *
 * ## 동작 / 계약
 * - [BeanPostProcessor] 등록 시점에 [ImageStorage] bean을 사용할 수 있도록 [ImagesStorageAutoConfiguration]
 *   뒤에 `afterName`으로 ordering합니다. 또한 Spring Boot 4의 Micrometer registry auto-configuration 뒤에
 *   실행되어 registry bean이 먼저 검색되도록 합니다.
 * - `bluetape4k.images.metrics.enabled`로 toggle됩니다(default `true`).
 * - `io.micrometer.core.instrument.MeterRegistry`가 classpath에 있고 bean으로 등록된 경우에만 활성화됩니다.
 *   따라서 Micrometer를 의존성으로 포함했지만 registry를 사용하지 않는 consumer의 context startup을 방해하지
 *   않습니다. 외부 class는 `compileOnly` Micrometer type을 직접 참조하지 않으며, nested
 *   [MetricsDecorationConfiguration] class가 유일한 integration point입니다.
 * - context startup 중 모든 [io.bluetape4k.images.spring.storage.ImageStorage] bean을
 *   [io.bluetape4k.images.spring.metrics.MetricImageStorage]로 감싸는 [ImageStorageMetricsBeanPostProcessor]를 등록합니다.
 *
 * ### Maintainer note
 * [BeanPostProcessor]를 반환하는 `@Bean` factory는 의도적으로 instance method입니다(`@JvmStatic` 아님).
 * non-static `@Bean`이 [BeanPostProcessor]를 반환하면 Spring이 INFO log를 낼 수 있습니다. 여기서는 post-processor가
 * runtime Spring-managed [MeterRegistry]에 의존하고, 그 registry가 일반 `@Autowired` lookup으로 wiring되므로 허용합니다.
 */
@AutoConfiguration(
    afterName = [
        "io.bluetape4k.images.spring.autoconfigure.ImagesStorageAutoConfiguration",
        "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration",
        "org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration",
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
    @ConditionalOnBean(MeterRegistry::class)
    class MetricsDecorationConfiguration {

        @Bean
        fun imageStorageMetricsBeanPostProcessor(registry: MeterRegistry): BeanPostProcessor =
            ImageStorageMetricsBeanPostProcessor(registry)
    }
}
