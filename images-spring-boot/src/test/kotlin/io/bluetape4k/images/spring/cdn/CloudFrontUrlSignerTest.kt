package io.bluetape4k.images.spring.cdn

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotContain
import io.bluetape4k.images.spring.ImageStorageException
import io.bluetape4k.images.spring.autoconfigure.CdnProperties
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test

class CloudFrontUrlSignerTest {

    companion object: KLogging()

    @Test
    fun `constructor rejects missing private key source as caller validation`() {
        val error = assertFailsWith<IllegalArgumentException> {
            CloudFrontUrlSigner(baseProperties())
        }
        log.debug { "error message: ${error.message}" }
        error.message shouldContain "must be provided"
    }

    @Test
    fun `constructor rejects multiple private key sources as caller validation`() {
        val error = assertFailsWith<IllegalArgumentException> {
            CloudFrontUrlSigner(
                baseProperties(
                    privateKeyPem = "inline-test-private-key",
                    privateKeyPath = "/run/secrets/cloudfront-private-key.pem",
                )
            )
        }
        log.debug { "error message: ${error.message}" }
        error.message shouldContain "not both"
    }

    @Test
    fun `private key path failures do not expose path or parser cause`() {
        val path = "/tmp/issue-481-private-key.pem"
        val error = assertFailsWith<ImageStorageException.ValidationException> {
            CloudFrontUrlSigner(baseProperties(privateKeyPath = path))
        }

        log.debug { "error message: ${error.message}" }
        error.message shouldNotContain path
        error.cause.shouldBeNull()
    }

    private fun baseProperties(
        privateKeyPem: String? = null,
        privateKeyPath: String? = null,
    ): CdnProperties.CloudFront =
        CdnProperties.CloudFront(
            distributionDomain = "d111111abcdef8.cloudfront.net",
            keyPairId = "K1234567890",
            privateKeyPem = privateKeyPem,
            privateKeyPath = privateKeyPath,
        )
}
