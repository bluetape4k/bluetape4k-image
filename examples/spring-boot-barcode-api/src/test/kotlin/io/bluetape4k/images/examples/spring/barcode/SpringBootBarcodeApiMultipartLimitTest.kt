package io.bluetape4k.images.examples.spring.barcode

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.javatimes.seconds
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpringBootBarcodeApiMultipartLimitTest(
    @param:Value($$"${local.server.port}") private val port: Int,
) {

    companion object: KLogging()

    @Test
    fun `real multipart parser returns stable payload too large JSON`() {
        val boundary = "bluetape4k-boundary"
        val prefix = (
                "--$boundary\r\n" +
                        "Content-Disposition: form-data; name=\"file\"; filename=\"over-limit.png\"\r\n" +
                        "Content-Type: image/png\r\n" +
                        "\r\n"
                ).toByteArray()
        val suffix = "\r\n--$boundary--\r\n".toByteArray()
        val body = HttpRequest.BodyPublishers.ofByteArrays(
            listOf(prefix, ByteArray(6 * 1024 * 1024), suffix)
        )
        val request = HttpRequest.newBuilder()
            .uri(URI("http://127.0.0.1:$port/api/barcodes/extract"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(body)
            .build()

        val response = HttpClient.newBuilder()
            .connectTimeout(5.seconds())
            .build()
            .use { client -> client.send(request, HttpResponse.BodyHandlers.ofString()) }

        log.debug { "response=$response, body=${response.body()}" }
        response.statusCode() shouldBeEqualTo 413
        response.body() shouldContain "\"error\":\"payload_too_large\""
        response.body() shouldContain "\"message\":\"The uploaded file exceeds the configured size limit.\""
    }
}
