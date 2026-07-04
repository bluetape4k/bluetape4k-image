package io.bluetape4k.images.spring.cdn

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.images.spring.autoconfigure.CdnProperties
import org.junit.jupiter.api.Test

class CloudFrontUrlSignerTest {

    @Test
    fun `constructor rejects missing private key source as caller validation`() {
        val error = assertFailsWith<IllegalArgumentException> {
            CloudFrontUrlSigner(baseProperties())
        }

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

        error.message shouldContain "not both"
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
