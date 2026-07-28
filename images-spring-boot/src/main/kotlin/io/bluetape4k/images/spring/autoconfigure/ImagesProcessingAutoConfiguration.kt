package io.bluetape4k.images.spring.autoconfigure

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties

/**
 * Phase 1 image processing auto-configuration입니다.
 *
 * ## 동작 / 계약
 * - `bluetape4k.images.processing` prefix 아래 [ImageProcessingProperties] binding을 활성화합니다.
 * - `bluetape4k.images.processing.enabled`로 toggle됩니다(default `true`).
 * - 현재는 placeholder phase입니다. property bean만 소유하며 `ImageProcessor`/`ImagePipeline` bean은 등록하지 않습니다.
 *   해당 bean은 후속 phase에서 추가합니다.
 *
 * 이 class는 auto-configuration chain의 첫 phase입니다. 이후 phase(`Storage`, `Cdn`, `Health`, `Metrics`)는
 * ordering 보장을 위해 `afterName`으로 이 class를 참조합니다.
 */
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "bluetape4k.images.processing",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
@EnableConfigurationProperties(ImageProcessingProperties::class)
class ImagesProcessingAutoConfiguration
