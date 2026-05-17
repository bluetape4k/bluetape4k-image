package io.bluetape4k.images.spring.metrics

import io.bluetape4k.images.spring.storage.ImageStorage
import io.bluetape4k.logging.KLogging
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.config.BeanPostProcessor

/**
 * Wraps every [ImageStorage] bean with [MetricImageStorage] during context initialization.
 *
 * ## Behavior / Contract
 * - Only beans that are not already a [MetricImageStorage] are wrapped — preventing double
 *   instrumentation when an [ImageStorage] bean is replaced or proxied.
 * - Non-[ImageStorage] beans pass through unchanged.
 * - The wrapper is created lazily — the same [MeterRegistry] is shared across all wrapped
 *   instances, which is correct because Micrometer registries are thread-safe by design.
 *
 * This post-processor is registered by [io.bluetape4k.images.spring.autoconfigure.ImagesMetricsAutoConfiguration].
 */
class ImageStorageMetricsBeanPostProcessor(
    private val registry: MeterRegistry,
) : BeanPostProcessor {

    companion object : KLogging()

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
        if (bean !is ImageStorage || bean is MetricImageStorage) {
            return bean
        }
        return MetricImageStorage(delegate = bean, registry = registry)
    }
}
