package io.bluetape4k.images.examples.spring

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * local-storage image API용 Spring Boot quickstart application입니다.
 */
@SpringBootApplication
class SpringBootImageApiApplication

fun main(args: Array<String>) {
    runApplication<SpringBootImageApiApplication>(*args)
}
