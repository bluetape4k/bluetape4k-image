package io.bluetape4k.images.examples.spring.intelligence

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

fun main(args: Array<String>) {
    runApplication<ImageIntelligenceApiApplication>(*args)
}

/**
 * OCR, detection, barcode analysis를 조합하는 Spring Boot example입니다.
 */
@SpringBootApplication
class ImageIntelligenceApiApplication
