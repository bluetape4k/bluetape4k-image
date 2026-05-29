package io.bluetape4k.images.ktor

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeLessOrEqualTo
import io.bluetape4k.images.immutableImageOf
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.append
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class ImageThumbnailKtorRoutesTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `creates thumbnail from multipart image upload`() = testApplication {
        application {
            this.install(ServerContentNegotiation) {
                json(json)
            }
            routing {
                bluetape4kImageThumbnailRoutes()
            }
        }
        val client = createJsonClient()
        val sourceBytes = pngBytes(width = 120, height = 80)

        val response = client.post("/images/thumbnail?maxSide=32") {
            setBody(imageMultipart(sourceBytes))
        }

        response.status shouldBeEqualTo HttpStatusCode.OK
        response.headers[HttpHeaders.ContentType] shouldBeEqualTo ContentType.Image.PNG.toString()

        val thumbnail = immutableImageOf(response.bodyAsBytes())
        thumbnail.width shouldBeLessOrEqualTo 32
        thumbnail.height shouldBeLessOrEqualTo 32
    }

    @Test
    fun `returns bad request when multipart file field is missing`() = testApplication {
        application {
            this.install(ServerContentNegotiation) {
                json(json)
            }
            routing {
                bluetape4kImageThumbnailRoutes()
            }
        }
        val client = createJsonClient()

        val response = client.post("/images/thumbnail") {
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

    @Test
    fun `returns bad request when maxSide exceeds configured limit`() = testApplication {
        application {
            this.install(ServerContentNegotiation) {
                json(json)
            }
            routing {
                bluetape4kImageThumbnailRoutes(
                    ImageThumbnailKtorRoutesConfig(defaultMaxSide = 64, maxAllowedSide = 64)
                )
            }
        }
        val client = createJsonClient()

        val response = client.post("/images/thumbnail?maxSide=128") {
            setBody(imageMultipart(pngBytes(width = 120, height = 80)))
        }

        response.status shouldBeEqualTo HttpStatusCode.BadRequest
        val body = response.body<ImageRouteErrorResponse>()
        body.error shouldBeEqualTo "bad_request"
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
        graphics.color = Color(42, 120, 220)
        graphics.fillRect(0, 0, width, height)
        graphics.dispose()

        return ByteArrayOutputStream().use { output ->
            ImageIO.write(image, "png", output)
            output.toByteArray()
        }
    }
}
