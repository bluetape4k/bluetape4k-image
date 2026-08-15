package io.bluetape4k.images.spring.metrics

import io.bluetape4k.images.spring.storage.ImageObjectMetadataReader
import io.bluetape4k.images.spring.storage.ImageStorage
import io.bluetape4k.logging.KLogging
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.config.BeanPostProcessor

/**
 * context initialization 중 모든 [ImageStorage] bean을 [MetricImageStorage] 계열로 감쌉니다.
 *
 * ## 동작/계약
 * - 아직 [MetricImageStorage]가 아닌 bean만 wrap합니다. [ImageStorage] bean이 교체되거나
 *   proxy되어도 double instrumentation을 방지합니다.
 * - [ImageObjectMetadataReader] capability를 가진 bean은
 *   [MetricImageStorageWithMetadata]로 감싸 capability를 보존합니다.
 * - [ImageStorage]가 아닌 bean은 변경 없이 통과합니다.
 * - wrapper는 lazily 생성됩니다. 같은 [MeterRegistry]가 모든 wrapped instance에 공유되며,
 *   Micrometer registry는 설계상 thread-safe이므로 올바른 동작입니다.
 *
 * 이 post-processor는 [io.bluetape4k.images.spring.autoconfigure.ImagesMetricsAutoConfiguration]에서 등록됩니다.
 */
class ImageStorageMetricsBeanPostProcessor(
    private val registry: MeterRegistry,
) : BeanPostProcessor {

    companion object : KLogging()

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
        if (bean !is ImageStorage || bean is MetricImageStorage) {
            return bean
        }
        return if (bean is ImageObjectMetadataReader) {
            MetricImageStorageWithMetadata(
                delegate = bean,
                registry = registry,
                metadataDelegate = bean,
            )
        } else {
            MetricImageStorage(delegate = bean, registry = registry)
        }
    }
}
