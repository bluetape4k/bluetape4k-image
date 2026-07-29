package io.bluetape4k.images.examples.spring.barcode

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

fun main(args: Array<String>) {
    runApplication<SpringBootBarcodeApiApplication>(*args)
}

/**
 * barcode extraction용 Spring Boot quickstart application입니다.
 */
@SpringBootApplication
class SpringBootBarcodeApiApplication
