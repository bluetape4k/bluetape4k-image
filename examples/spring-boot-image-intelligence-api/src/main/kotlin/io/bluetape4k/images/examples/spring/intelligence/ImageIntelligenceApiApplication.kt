package io.bluetape4k.images.examples.spring.intelligence

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

fun main(args: Array<String>) {
    runApplication<ImageIntelligenceApiApplication>(*args)
}

/**
 * Spring Boot example that composes OCR, detection, and barcode analysis.
 */
@SpringBootApplication
class ImageIntelligenceApiApplication
