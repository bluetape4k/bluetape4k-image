package io.bluetape4k.images.examples.spring.barcode

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

fun main(args: Array<String>) {
    runApplication<SpringBootBarcodeApiApplication>(*args)
}

/**
 * Spring Boot quickstart application for barcode extraction.
 */
@SpringBootApplication
class SpringBootBarcodeApiApplication
