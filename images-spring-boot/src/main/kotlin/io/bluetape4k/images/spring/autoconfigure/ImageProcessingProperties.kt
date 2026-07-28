package io.bluetape4k.images.spring.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties
import java.io.Serializable

/**
 * image processing auto-configuration용 configuration properties입니다.
 *
 * ## 동작
 * - `bluetape4k.images.processing` prefix에 bind됩니다.
 * - [defaultQuality]는 1..100 범위여야 합니다. 범위를 벗어난 값은 context startup failure를 발생시킵니다.
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
