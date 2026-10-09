package io.bluetape4k.images.examples.spring.barcode

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * barcode extraction용 Spring Boot quickstart application입니다.
 */
@SpringBootApplication
class SpringBootBarcodeApiApplication

fun main(args: Array<String>) {
    runApplication<SpringBootBarcodeApiApplication>(*args)
}
