package io.bluetape4k.images.spring.autoconfigure

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeSameInstanceAs
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.endpoint.SanitizableData

class CdnPropertySanitizingFunctionTest {

    private val sanitizer = CdnPropertySanitizingFunction()

    @Test
    fun `redacts privateKeyPem property`() {
        val data = SanitizableData(
            null,
            "bluetape4k.images.cdn.cloudfront.private-key-pem",
            "-----BEGIN PRIVATE KEY-----...",
        )
        val result = sanitizer.apply(data)
        result.value shouldBeEqualTo SanitizableData.SANITIZED_VALUE
    }

    @Test
    fun `redacts privateKeyPath property`() {
        val data = SanitizableData(
            null,
            "bluetape4k.images.cdn.cloudfront.private-key-path",
            "/etc/ssl/private.pem",
        )
        val result = sanitizer.apply(data)
        result.value shouldBeEqualTo SanitizableData.SANITIZED_VALUE
    }

    @Test
    fun `redacts camelCase privateKey property`() {
        val data = SanitizableData(
            null,
            "bluetape4k.images.cdn.cloudfront.privateKey",
            "some-private-key",
        )
        val result = sanitizer.apply(data)
        result.value shouldBeEqualTo SanitizableData.SANITIZED_VALUE
    }

    @Test
    fun `does not redact unrelated properties`() {
        val data = SanitizableData(
            null,
            "bluetape4k.images.cdn.cloudfront.key-pair-id",
            "APKABC123",
        )
        val result = sanitizer.apply(data)
        result.value shouldBeEqualTo "APKABC123"
    }

    @Test
    fun `does not redact distribution-domain property`() {
        val data = SanitizableData(
            null,
            "bluetape4k.images.cdn.cloudfront.distribution-domain",
            "d1234.cloudfront.net",
        )
        val result = sanitizer.apply(data)
        result.value shouldBeEqualTo "d1234.cloudfront.net"
    }

    @Test
    fun `redacts property containing private-key-pem as substring`() {
        val data = SanitizableData(
            null,
            "some.prefix.private-key-pem.suffix",
            "secret",
        )
        val result = sanitizer.apply(data)
        result.value shouldBeEqualTo SanitizableData.SANITIZED_VALUE
    }

    @Test
    fun `returns same data instance when key is not sensitive`() {
        val data = SanitizableData(null, "bluetape4k.images.cdn.enabled", "true")
        val result = sanitizer.apply(data)
        result shouldBeSameInstanceAs data
    }

    @Test
    fun `redacts null value for sensitive key`() {
        val data = SanitizableData(null, "bluetape4k.images.cdn.cloudfront.private-key-pem", null)
        val result = sanitizer.apply(data)
        result.value shouldBeEqualTo SanitizableData.SANITIZED_VALUE
    }
}
