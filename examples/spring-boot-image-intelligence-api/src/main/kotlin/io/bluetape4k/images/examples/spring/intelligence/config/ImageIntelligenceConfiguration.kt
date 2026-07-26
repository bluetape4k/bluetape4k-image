package io.bluetape4k.images.examples.spring.intelligence.config

import io.bluetape4k.support.requirePositiveNumber
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.io.Serializable
import java.time.Duration

/**
 * Input and provider execution limits for the image-intelligence example.
 */
@ConfigurationProperties(prefix = "example.image-intelligence")
data class ImageIntelligenceProperties(
    val maxInputBytes: Long = 5L * 1024L * 1024L,
    val maxInputPixels: Long = 16_777_216L,
    val maxInputSide: Int = 8_192,
    val ocrTimeout: Duration = Duration.ofSeconds(3),
    val detectionTimeout: Duration = Duration.ofSeconds(2),
    val barcodeTimeout: Duration = Duration.ofSeconds(2),
    val ocrConcurrency: Int = 1,
    val detectionConcurrency: Int = 2,
    val barcodeConcurrency: Int = 4,
    val tessdataPath: String? = null,
) : Serializable {

    init {
        maxInputBytes.requirePositiveNumber("maxInputBytes")
        require(maxInputBytes <= Int.MAX_VALUE) { "maxInputBytes must fit Int" }
        maxInputPixels.requirePositiveNumber("maxInputPixels")
        maxInputSide.requirePositiveNumber("maxInputSide")
        require(ocrTimeout.toMillis() > 0L) { "ocrTimeout must be at least 1 ms" }
        require(detectionTimeout.toMillis() > 0L) { "detectionTimeout must be at least 1 ms" }
        require(barcodeTimeout.toMillis() > 0L) { "barcodeTimeout must be at least 1 ms" }
        ocrConcurrency.requirePositiveNumber("ocrConcurrency")
        detectionConcurrency.requirePositiveNumber("detectionConcurrency")
        barcodeConcurrency.requirePositiveNumber("barcodeConcurrency")
        require(tessdataPath == null || tessdataPath.isNotBlank()) {
            "tessdataPath must be null or non-blank"
        }
    }

    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ImageIntelligenceProperties::class)
internal class ImageIntelligenceConfiguration
