package io.bluetape4k.images.examples.ktor

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldBeLessOrEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.images.immutableImageOf
import io.bluetape4k.images.ktor.CaptchaIssueResponse
import io.bluetape4k.images.ktor.ImageRouteErrorResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.append
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

class KtorImageApiApplicationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `ready endpoint responds with plain text`() = testApplication {
        application {
            configureKtorImageApi()
        }

        val response = client.get("/ready")

        response.status shouldBeEqualTo HttpStatusCode.OK
    }

    @Test
    fun `issues captcha through quickstart route`() = testApplication {
        application {
            configureKtorImageApi()
        }
        val client = createJsonClient()

        val response = client.get("/api/captcha?length=4")

        response.status shouldBeEqualTo HttpStatusCode.OK
        val body = response.body<CaptchaIssueResponse>()
        body.id.length shouldBeGreaterThan 0
        body.contentType shouldBeEqualTo ContentType.Image.PNG.toString()
        val imageBytes = Base64.getDecoder().decode(body.imageBase64)
        imageBytes.copyOfRange(0, PNG_SIGNATURE.size)
            .contentEquals(PNG_SIGNATURE)
            .shouldBeTrue()
    }

    @Test
    fun `creates thumbnail through quickstart image route`() = testApplication {
        application {
            configureKtorImageApi()
        }
        val client = createJsonClient()

        val response = client.post("/api/images/thumbnail?maxSide=40") {
            setBody(imageMultipart(pngBytes(width = 120, height = 80)))
        }

        response.status shouldBeEqualTo HttpStatusCode.OK
        response.contentType()?.withoutParameters() shouldBeEqualTo ContentType.Image.PNG
        val thumbnail = immutableImageOf(response.bodyAsBytes())
        thumbnail.width shouldBeLessOrEqualTo 40
        thumbnail.height shouldBeLessOrEqualTo 40
    }

    @Test
    fun `rejects thumbnail request without expected file field`() = testApplication {
        application {
            configureKtorImageApi()
        }
        val client = createJsonClient()

        val response = client.post("/api/images/thumbnail") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("other", pngBytes(width = 16, height = 16))
                    }
                )
            )
        }

        response.status shouldBeEqualTo HttpStatusCode.BadRequest
        val body = response.body<ImageRouteErrorResponse>()
        body.error shouldBeEqualTo "bad_request"
        body.status shouldBeEqualTo HttpStatusCode.BadRequest.value
    }

    private fun ApplicationTestBuilder.createJsonClient() =
        createClient {
            install(ContentNegotiation) {
                json(json)
            }
        }

    private fun imageMultipart(bytes: ByteArray): MultiPartFormDataContent =
        MultiPartFormDataContent(
            formData {
                append("file", "source.png", ContentType.Image.PNG, bytes.size.toLong()) {
                    write(bytes)
                }
            }
        )

    private fun pngBytes(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = Color(31, 110, 185)
            graphics.fillRect(0, 0, width, height)
            graphics.color = Color.WHITE
            graphics.fillOval(width / 4, height / 4, width / 2, height / 2)
        } finally {
            graphics.dispose()
        }

        return ByteArrayOutputStream().use { output ->
            ImageIO.write(image, "png", output)
            output.toByteArray()
        }
    }

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
