package io.bluetape4k.images.ktor

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.images.captcha.CaptchaChallengeId
import io.bluetape4k.images.captcha.CaptchaOptions
import io.bluetape4k.images.captcha.CaptchaVerificationService
import io.bluetape4k.images.captcha.captchaGenerator
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.util.Base64

class CaptchaKtorRoutesTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `issues captcha as base64 png payload`() = testApplication {
        val verifier = CaptchaVerificationService()
        application {
            this.install(ServerContentNegotiation) {
                json(json)
            }
            routing {
                bluetape4kCaptchaRoutes(newConfig(verifier = verifier))
            }
        }
        val client = createJsonClient()

        val response = client.get("/captcha")

        response.status shouldBeEqualTo HttpStatusCode.OK
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

        application {
            this.install(ServerContentNegotiation) {
                json(json)
            }
            routing {
                bluetape4kCaptchaRoutes(newConfig(verifier = verifier))
            }
        }
        val client = createJsonClient()

        val response = client.post("/captcha/${issued.id.value}/verify") {
            contentType(ContentType.Application.Json)
            setBody(CaptchaVerifyRequest(challenge.text))
        }

        response.status shouldBeEqualTo HttpStatusCode.OK
        val body = response.body<CaptchaVerifyResponse>()
        body.id shouldBeEqualTo issued.id.value
        body.status shouldBeEqualTo CaptchaVerificationStatus.SUCCESS
        body.verified.shouldBeTrue()

        val replay = client.post("/captcha/${issued.id.value}/verify") {
            contentType(ContentType.Application.Json)
            setBody(CaptchaVerifyRequest(challenge.text))
        }
        replay.status shouldBeEqualTo HttpStatusCode.NotFound
    }

    @Test
    fun `keeps route helper compatible with existing application routes`() = testApplication {
        application {
            this.install(ServerContentNegotiation) {
                json(json)
            }
            routing {
                get("/ready") { call.respond(mapOf("status" to "UP")) }
                bluetape4kCaptchaRoutes(newConfig())
            }
        }
        val client = createJsonClient()

        val response = client.get("/ready")

        response.status shouldBeEqualTo HttpStatusCode.OK
    }

    private fun ApplicationTestBuilder.createJsonClient() =
        createClient {
            install(ContentNegotiation) {
                json(json)
            }
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
