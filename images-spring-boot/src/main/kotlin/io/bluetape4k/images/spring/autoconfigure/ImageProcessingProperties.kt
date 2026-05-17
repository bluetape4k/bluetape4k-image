package io.bluetape4k.images.spring.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties
import java.io.Serializable

/**
 * Configuration properties for image processing auto-configuration.
 *
 * ## Behavior
 * - Bound to the `bluetape4k.images.processing` prefix.
 * - [defaultQuality] must be in the range 1..100; values outside this range cause context startup failure.
 */
@ConfigurationProperties(prefix = "bluetape4k.images.processing")
data class ImageProcessingProperties(
    val enabled: Boolean = true,
    val defaultFormat: String = "jpeg",
    val defaultQuality: Int = 85,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }

    init {
        require(defaultQuality in 1..100) { "defaultQuality must be in 1..100, but was $defaultQuality" }
    }
}
