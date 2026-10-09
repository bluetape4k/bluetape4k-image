package io.bluetape4k.images.examples.spring.config

import io.bluetape4k.images.ImageDecodeLimits
import io.bluetape4k.support.requirePositiveNumber
import org.springframework.boot.context.properties.ConfigurationProperties
import java.io.Serializable

/**
 * quickstart image API의 upload safety limit입니다.
 */
@ConfigurationProperties(prefix = "example.image")
data class ImageApiProperties(
    val maxInputBytes: Long = 10L * 1024L * 1024L,
    val maxInputPixels: Long = 16_777_216L,
    val maxInputSide: Int = 8_192,
): Serializable {

    init {
        maxInputBytes.requirePositiveNumber("maxInputBytes")
        maxInputPixels.requirePositiveNumber("maxInputPixels")
        maxInputSide.requirePositiveNumber("maxInputSide")
    }

    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}

internal fun ImageApiProperties.toDecodeLimits(): ImageDecodeLimits =
    ImageDecodeLimits(
        maxEncodedBytes = maxInputBytes,
        maxDecodedPixels = maxInputPixels,
        maxDecodedSide = maxInputSide,
    )
