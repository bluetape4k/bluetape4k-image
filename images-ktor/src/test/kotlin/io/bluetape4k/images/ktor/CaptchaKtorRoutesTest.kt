package io.bluetape4k.images.ktor

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.images.captcha.CaptchaChallengeId
import io.bluetape4k.images.captcha.CaptchaOptions
import io.bluetape4k.images.captcha.CaptchaVerificationService
import io.bluetape4k.images.captcha.captchaGenerator
import io.bluetape4k.ktor.core.Bluetape4kKtorCoreConfig
import io.bluetape4k.ktor.testing.bluetape4kJsonClient
import io.bluetape4k.ktor.testing.installBluetape4kKtorCoreForTest
import io.bluetape4k.ktor.testing.shouldHaveStatus
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import java.util.Base64

class CaptchaKtorRoutesTest {

    @Test
    fun `issues captcha as base64 png payload`() = testApplication {
        val verifier = CaptchaVerificationService()
        installBluetape4kKtorCoreForTest(testCoreConfig) {
            bluetape4kCaptchaRoutes(newConfig(verifier = verifier))
        }
        val client = bluetape4kJsonClient()

        val response = client.get("/captcha")

        response shouldHaveStatus HttpStatusCode.OK
        val body = response.body<CaptchaIssueResponse>()
        body.id shouldBeEqualTo "captcha-1"
        body.contentType shouldBeEqualTo "image/png"
        body.expiresAt.isNotBlank().shouldBeTrue()
        val imageBytes = Base64.getDecoder().decode(body.imageBase64)
        imageBytes.size shouldBeGreaterThan 8
        imageBytes.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE).shouldBeTrue()
    }

    @Test
    fun `verifies captcha once`() = testApplication {
        val generator = captchaGenerator { length(4) }
        val verifier = CaptchaVerificationService()
        val challenge = generator.generate(4)
        val issued = verifier.issue(CaptchaChallengeId("captcha-verify"), challenge)

        installBluetape4kKtorCoreForTest(testCoreConfig) {
            bluetape4kCaptchaRoutes(newConfig(verifier = verifier))
        }
        val client = bluetape4kJsonClient()

        val response = client.post("/captcha/${issued.id.value}/verify") {
            contentType(ContentType.Application.Json)
            setBody(CaptchaVerifyRequest(challenge.text))
        }

        response shouldHaveStatus HttpStatusCode.OK
        val body = response.body<CaptchaVerifyResponse>()
        body.id shouldBeEqualTo issued.id.value
        body.status shouldBeEqualTo CaptchaVerificationStatus.SUCCESS
        body.verified.shouldBeTrue()

        val replay = client.post("/captcha/${issued.id.value}/verify") {
            contentType(ContentType.Application.Json)
            setBody(CaptchaVerifyRequest(challenge.text))
        }
        replay shouldHaveStatus HttpStatusCode.NotFound
    }

    @Test
    fun `keeps route helper compatible with existing application routes`() = testApplication {
        installBluetape4kKtorCoreForTest(testCoreConfig) {
            get("/ready") { call.respond(mapOf("status" to "UP")) }
            bluetape4kCaptchaRoutes(newConfig())
        }

        val response = client.get("/ready")

        response shouldHaveStatus HttpStatusCode.OK
    }

    private fun newConfig(
        verifier: CaptchaVerificationService = CaptchaVerificationService(),
    ): CaptchaKtorRoutesConfig =
        CaptchaKtorRoutesConfig(
            generator = captchaGenerator { length(CaptchaOptions.DEFAULT_LENGTH) },
            verificationService = verifier,
            idFactory = { CaptchaChallengeId("captcha-1") },
        )

    private companion object {
        val testCoreConfig = Bluetape4kKtorCoreConfig(installHealthRoutes = false)

        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
        )
    }
}
