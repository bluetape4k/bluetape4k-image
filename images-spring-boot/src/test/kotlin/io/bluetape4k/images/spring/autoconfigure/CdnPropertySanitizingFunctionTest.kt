package io.bluetape4k.images.spring.autoconfigure

import com.fasterxml.jackson.annotation.JsonIgnore
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeSameInstanceAs
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.assertions.shouldNotContain
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.endpoint.SanitizableData

class CdnPropertySanitizingFunctionTest {

    companion object: KLogging()

    private val sanitizer = CdnPropertySanitizingFunction()

    @Test
    fun `cloudfront toString redacts both private key sources`() {
        val value = CdnProperties.CloudFront(
            privateKeyPem = "fixture-private-key",
            privateKeyPath = "/run/secrets/cloudfront-private-key.pem",
        )

        with(value.toString()) {
            log.debug { "value=$this" }
            this shouldNotContain "secret"
            this shouldNotContain "/run/secrets/cloudfront-private-key.pem"
            this shouldContain "[REDACTED]"
        }
    }

    @Test
    fun `cloudfront private key getters are excluded from Jackson views`() {
        CdnProperties.CloudFront::class.java.getMethod("getPrivateKeyPem")
            .getAnnotation(JsonIgnore::class.java)
            .shouldNotBeNull()

        CdnProperties.CloudFront::class.java.getMethod("getPrivateKeyPath")
            .getAnnotation(JsonIgnore::class.java)
            .shouldNotBeNull()
    }

    @Test
    fun `redacts privateKeyPem property`() {
        val data = SanitizableData(
            null,
            "bluetape4k.images.cdn.cloudfront.private-key-pem",
            "fixture-private-key",
        )
        log.debug { "data=${data.value}" }

        val result = sanitizer.apply(data)
        log.debug { "result=${result.value}" }
        result.value shouldBeEqualTo SanitizableData.SANITIZED_VALUE
    }

    @Test
    fun `redacts privateKeyPath property`() {
        val data = SanitizableData(
            null,
            "bluetape4k.images.cdn.cloudfront.private-key-path",
            "/etc/ssl/private.pem",
        )
        log.debug { "data=${data.value}" }

        val result = sanitizer.apply(data)
        log.debug { "result=${result.value}" }
        result.value shouldBeEqualTo SanitizableData.SANITIZED_VALUE
    }

    @Test
    fun `redacts camelCase privateKey property`() {
        val data = SanitizableData(
            null,
            "bluetape4k.images.cdn.cloudfront.privateKey",
            "some-private-key",
        )
        log.debug { "data=${data.value}" }

        val result = sanitizer.apply(data)
        log.debug { "result=${result.value}" }
        result.value shouldBeEqualTo SanitizableData.SANITIZED_VALUE
    }

    @Test
    fun `does not redact unrelated properties`() {
        val data = SanitizableData(
            null,
            "bluetape4k.images.cdn.cloudfront.key-pair-id",
            "APKABC123",
        )
        log.debug { "data=${data.value}" }

        val result = sanitizer.apply(data)
        log.debug { "result=${result.value}" }
        result.value shouldBeEqualTo "APKABC123"
    }

    @Test
    fun `does not redact distribution-domain property`() {
        val data = SanitizableData(
            null,
            "bluetape4k.images.cdn.cloudfront.distribution-domain",
            "d1234.cloudfront.net",
        )
        log.debug { "data=${data.value}" }

        val result = sanitizer.apply(data)
        log.debug { "result=${result.value}" }
        result.value shouldBeEqualTo "d1234.cloudfront.net"
    }

    @Test
    fun `redacts property containing private-key-pem as substring`() {
        val data = SanitizableData(
            null,
            "some.prefix.private-key-pem.suffix",
            "secret",
        )
        log.debug { "data=${data.value}" }

        val result = sanitizer.apply(data)
        log.debug { "result=${result.value}" }
        result.value shouldBeEqualTo SanitizableData.SANITIZED_VALUE
    }

    @Test
    fun `returns same data instance when key is not sensitive`() {
        val data = SanitizableData(null, "bluetape4k.images.cdn.enabled", "true")
        val result = sanitizer.apply(data)
        log.debug { "result=${result.value}" }
        result shouldBeSameInstanceAs data
    }

    @Test
    fun `redacts null value for sensitive key`() {
        val data = SanitizableData(null, "bluetape4k.images.cdn.cloudfront.private-key-pem", null)
        log.debug { "data=${data.value}" }

        val result = sanitizer.apply(data)
        log.debug { "result=${result.value}" }
        result.value shouldBeEqualTo SanitizableData.SANITIZED_VALUE
    }
}
