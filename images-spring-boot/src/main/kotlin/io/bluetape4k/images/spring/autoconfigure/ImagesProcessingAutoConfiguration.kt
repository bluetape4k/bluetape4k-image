package io.bluetape4k.images.spring.autoconfigure

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties

/**
 * Phase 1 — image processing auto-configuration.
 *
 * ## Behavior / Contract
 * - Enables [ImageProcessingProperties] binding under the `bluetape4k.images.processing` prefix.
 * - Toggled by `bluetape4k.images.processing.enabled` (default `true`).
 * - Currently a placeholder phase: it owns the property bean only and does not register
 *   `ImageProcessor`/`ImagePipeline` beans. Those will be added in a subsequent phase.
 *
 * This class is the first phase in the auto-configuration chain — later phases (`Storage`, `Cdn`,
 * `Health`, `Metrics`) reference it via `afterName` to guarantee ordering.
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
