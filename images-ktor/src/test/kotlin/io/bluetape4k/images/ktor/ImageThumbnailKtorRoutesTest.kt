package io.bluetape4k.images.ktor

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeLessOrEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.images.immutableImageOf
import io.bluetape4k.ktor.core.ApiErrorResponse
import io.bluetape4k.ktor.core.Bluetape4kKtorCoreConfig
import io.bluetape4k.ktor.testing.bluetape4kJsonClient
import io.bluetape4k.ktor.testing.installBluetape4kKtorCoreForTest
import io.bluetape4k.ktor.testing.shouldHaveStatus
import io.ktor.client.call.body
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.append
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import javax.imageio.ImageIO

class ImageThumbnailKtorRoutesTest {

    @Test
    fun `creates thumbnail from multipart image upload`() = testApplication {
        installBluetape4kKtorCoreForTest(testCoreConfig) {
            bluetape4kImageThumbnailRoutes()
        }
        val client = bluetape4kJsonClient()
        val sourceBytes = pngBytes(width = 120, height = 80)

        val response = client.post("/images/thumbnail?maxSide=32") {
            setBody(imageMultipart(sourceBytes))
        }

        response shouldHaveStatus HttpStatusCode.OK
        response.headers[HttpHeaders.ContentType] shouldBeEqualTo ContentType.Image.PNG.toString()

        val thumbnail = immutableImageOf(response.bodyAsBytes())
        thumbnail.width shouldBeLessOrEqualTo 32
        thumbnail.height shouldBeLessOrEqualTo 32
    }

    @Test
    fun `returns bad request when multipart file field is missing`() = testApplication {
        installBluetape4kKtorCoreForTest(testCoreConfig) {
            bluetape4kImageThumbnailRoutes()
        }
        val client = bluetape4kJsonClient()

        val response = client.post("/images/thumbnail") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("other", pngBytes(width = 16, height = 16))
                    }
                )
            )
        }

        response shouldHaveStatus HttpStatusCode.BadRequest
        val body = response.body<ApiErrorResponse>()
        body.error shouldBeEqualTo "bad_request"
        body.status shouldBeEqualTo HttpStatusCode.BadRequest.value
    }

    @Test
    fun `returns bad request when maxSide exceeds configured limit`() = testApplication {
        installBluetape4kKtorCoreForTest(testCoreConfig) {
            bluetape4kImageThumbnailRoutes(
                ImageThumbnailKtorRoutesConfig(defaultMaxSide = 64, maxAllowedSide = 64)
            )
        }
        val client = bluetape4kJsonClient()

        val response = client.post("/images/thumbnail?maxSide=128") {
            setBody(imageMultipart(pngBytes(width = 120, height = 80)))
        }

        response shouldHaveStatus HttpStatusCode.BadRequest
        val body = response.body<ApiErrorResponse>()
        body.error shouldBeEqualTo "bad_request"
    }

    @Test
    fun `returns bad request before thumbnailing when decoded pixels exceed limit`() = testApplication {
        installBluetape4kKtorCoreForTest(testCoreConfig) {
            bluetape4kImageThumbnailRoutes()
        }
        val client = bluetape4kJsonClient()

        val response = client.post("/images/thumbnail") {
            setBody(imageMultipart(pngHeaderBytes(width = 10_000, height = 10_000)))
        }

        response shouldHaveStatus HttpStatusCode.BadRequest
        val body = response.body<ApiErrorResponse>()
        body.error shouldBeEqualTo "bad_request"
        body.message shouldContain "decodedPixels"
    }

    @Test
    fun `returns bad request when uploaded image payload is malformed`() = testApplication {
        installBluetape4kKtorCoreForTest(testCoreConfig) {
            bluetape4kImageThumbnailRoutes()
        }
        val client = bluetape4kJsonClient()

        val response = client.post("/images/thumbnail") {
            setBody(imageMultipart("not an image".toByteArray()))
        }

        response shouldHaveStatus HttpStatusCode.BadRequest
        val body = response.body<ApiErrorResponse>()
        body.error shouldBeEqualTo "bad_request"
        body.message shouldContain "Image parsing failed"
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

    private fun pngHeaderBytes(width: Int, height: Int): ByteArray {
        val output = ByteArrayOutputStream()
        output.write(PNG_SIGNATURE)
        output.writePngChunk(
            type = "IHDR",
            data = ByteArray(13).also { data ->
                data.writeInt(0, width)
                data.writeInt(4, height)
                data[8] = 8
                data[9] = 2
            }
        )
        output.writePngChunk(type = "IEND", data = ByteArray(0))
        return output.toByteArray()
    }

    private fun ByteArray.writeInt(offset: Int, value: Int) {
        this[offset] = (value ushr 24).toByte()
        this[offset + 1] = (value ushr 16).toByte()
        this[offset + 2] = (value ushr 8).toByte()
        this[offset + 3] = value.toByte()
    }

    private fun ByteArrayOutputStream.writePngChunk(type: String, data: ByteArray) {
        writeInt(data.size)
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        write(typeBytes)
        write(data)

        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        writeInt(crc.value.toInt())
    }

    private fun ByteArrayOutputStream.writeInt(value: Int) {
        write((value ushr 24) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 8) and 0xFF)
        write(value and 0xFF)
    }

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
