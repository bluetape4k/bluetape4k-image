package io.bluetape4k.images.examples.ktor.ocr

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotContain
import io.bluetape4k.images.ocr.OcrEngine
import io.bluetape4k.images.ocr.OcrException
import io.bluetape4k.images.ocr.OcrOptions
import io.bluetape4k.images.ocr.OcrResult
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.append
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.CRC32
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO

internal class KtorOcrApiApplicationTest {

    private val testOcrEngine = TestOcrEngine()

    @BeforeEach
    fun beforeEach() {
        testOcrEngine.reset()
    }

    @Test
    fun `ready endpoint responds with plain text`() = testApplication {
        application {
            configureTestKtorOcrApi()
        }

        val response = client.get("/ready")

        response.status shouldBeEqualTo HttpStatusCode.OK
    }

    @Test
    fun `rejects non-positive input limit`() {
        assertFailsWith<IllegalArgumentException> {
            KtorOcrApiConfig(maxInputBytes = 0)
        }
    }

    @Test
    fun `recognizes uploaded image with parsed languages`() = testApplication {
        application {
            configureTestKtorOcrApi()
        }
        val client = jsonClient()

        val response = client.post("/api/ocr?languages=eng+kor") {
            setBody(imageMultipart("file", samplePngBytes(), ContentType.Image.PNG))
        }

        response.status shouldBeEqualTo HttpStatusCode.OK
        val body = response.body<OcrTextResponse>()
        body.text shouldBeEqualTo "BLUETAPE OCR"
        body.languages shouldBeEqualTo listOf("eng", "kor")
        body.characterCount shouldBeEqualTo "BLUETAPE OCR".length

        val options = requireNotNull(testOcrEngine.lastOptions.get())
        options.languages shouldBeEqualTo listOf("eng", "kor")
        options.tessdataPath shouldBeEqualTo "/tmp/example-tessdata"
    }

    @Test
    fun `accepts Long MAX_VALUE input limit without overflowing bounded read`() = testApplication {
        application {
            configureKtorOcrApi(
                config = KtorOcrApiConfig(
                    maxInputBytes = Long.MAX_VALUE,
                    tessdataPath = "/tmp/example-tessdata",
                ),
                ocrEngine = testOcrEngine,
            )
        }
        val client = jsonClient()

        val response = client.post("/api/ocr") {
            setBody(imageMultipart("file", samplePngBytes(), ContentType.Image.PNG))
        }

        response.status shouldBeEqualTo HttpStatusCode.OK
    }

    @Test
    fun `rejects request without expected file field`() = testApplication {
        application {
            configureTestKtorOcrApi()
        }
        val client = jsonClient()

        val response = client.post("/api/ocr") {
            setBody(imageMultipart("other", samplePngBytes(), ContentType.Image.PNG))
        }

        response.status shouldBeEqualTo HttpStatusCode.BadRequest
        val body = response.body<OcrApiErrorResponse>()
        body.error shouldBeEqualTo "bad_request"
        body.status shouldBeEqualTo HttpStatusCode.BadRequest.value
        body.message.contains("Expected multipart file field").shouldBeTrue()
    }

    @Test
    fun `rejects unsupported content type`() = testApplication {
        application {
            configureTestKtorOcrApi()
        }
        val client = jsonClient()

        val response = client.post("/api/ocr") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        val bytes = "not an image".toByteArray()
                        append("file", "note.txt", ContentType.Text.Plain, bytes.size.toLong()) {
                            write(bytes)
                        }
                    }
                )
            )
        }

        response.status shouldBeEqualTo HttpStatusCode.BadRequest
        val body = response.body<OcrApiErrorResponse>()
        body.error shouldBeEqualTo "bad_request"
        body.status shouldBeEqualTo HttpStatusCode.BadRequest.value
        body.message.contains("Unsupported image content type").shouldBeTrue()
    }

    @Test
    fun `rejects decoded pixel limit before OCR engine is called`() = testApplication {
        application {
            configureTestKtorOcrApi()
        }
        val client = jsonClient()

        val response = client.post("/api/ocr") {
            setBody(imageMultipart("file", pngHeaderBytes(width = 10_000, height = 10_000), ContentType.Image.PNG))
        }

        response.status shouldBeEqualTo HttpStatusCode.BadRequest
        val body = response.body<OcrApiErrorResponse>()
        body.error shouldBeEqualTo "bad_request"
        body.message shouldContain "decodedPixels"
        testOcrEngine.lastOptions.get().shouldBeNull()
    }

    @Test
    fun `rejects upload when image dimensions cannot be probed before OCR engine is called`() = testApplication {
        application {
            configureTestKtorOcrApi()
        }
        val client = jsonClient()

        val response = client.post("/api/ocr") {
            setBody(imageMultipart("file", "not an encoded image".toByteArray(), ContentType.Image.PNG))
        }

        response.status shouldBeEqualTo HttpStatusCode.BadRequest
        val body = response.body<OcrApiErrorResponse>()
        body.error shouldBeEqualTo "bad_request"
        body.message shouldContain "dimensions could not be determined"
        testOcrEngine.lastOptions.get().shouldBeNull()
    }

    @Test
    fun `rejects header-valid malformed image before OCR engine is called`() = testApplication {
        application {
            configureTestKtorOcrApi()
        }
        val client = jsonClient()

        val response = client.post("/api/ocr") {
            setBody(imageMultipart("file", pngHeaderBytes(width = 10, height = 10), ContentType.Image.PNG))
        }

        response.status shouldBeEqualTo HttpStatusCode.BadRequest
        val body = response.body<OcrApiErrorResponse>()
        body.error shouldBeEqualTo "bad_request"
        body.message shouldContain "could not be decoded"
        testOcrEngine.lastOptions.get().shouldBeNull()
    }

    @Test
    fun `maps OCR failures to service unavailable`() = testApplication {
        application {
            configureTestKtorOcrApi()
        }
        testOcrEngine.failNext.set(true)
        val client = jsonClient()

        val response = client.post("/api/ocr") {
            setBody(imageMultipart("file", samplePngBytes(), ContentType.Image.PNG))
        }

        response.status shouldBeEqualTo HttpStatusCode.ServiceUnavailable
        val body = response.body<OcrApiErrorResponse>()
        body.error shouldBeEqualTo "ocr_unavailable"
        body.status shouldBeEqualTo HttpStatusCode.ServiceUnavailable.value
        body.message shouldBeEqualTo "OCR runtime is unavailable."
        body.message.shouldNotContain("/srv/private/tessdata")
    }

    @Test
    fun `maps image IO failures to sanitized bad request`() = testApplication {
        testOcrEngine.failWithIo.set(true)
        application {
            configureTestKtorOcrApi()
        }
        val client = jsonClient()

        val response = client.post("/api/ocr") {
            setBody(imageMultipart("file", samplePngBytes(), ContentType.Image.PNG))
        }

        response.status shouldBeEqualTo HttpStatusCode.BadRequest
        val body = response.body<OcrApiErrorResponse>()
        body.error shouldBeEqualTo "bad_request"
        body.message shouldBeEqualTo "Invalid image payload."
        body.message.shouldNotContain("/srv/private/native-codec")
    }

    private fun io.ktor.server.application.Application.configureTestKtorOcrApi() {
        configureKtorOcrApi(
            config = KtorOcrApiConfig(tessdataPath = "/tmp/example-tessdata"),
            ocrEngine = testOcrEngine,
        )
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.jsonClient() =
        createClient {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    }
                )
            }
        }

    private fun imageMultipart(
        fieldName: String,
        bytes: ByteArray,
        contentType: ContentType,
    ): MultiPartFormDataContent =
        MultiPartFormDataContent(
            formData {
                append(fieldName, "sample.png", contentType, bytes.size.toLong()) {
                    write(bytes)
                }
            }
        )

    private fun samplePngBytes(): ByteArray {
        val image = BufferedImage(360, 140, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, image.width, image.height)
            graphics.color = Color(35, 96, 146)
            graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 36)
            graphics.drawString("BLUETAPE OCR", 38, 82)
        } finally {
            graphics.dispose()
        }

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

    private class TestOcrEngine : OcrEngine {

        val lastOptions: AtomicReference<OcrOptions?> = AtomicReference()
        val failNext: AtomicBoolean = AtomicBoolean(false)
        val failWithIo: AtomicBoolean = AtomicBoolean(false)

        fun reset() {
            lastOptions.set(null)
            failNext.set(false)
            failWithIo.set(false)
        }

        override fun recognize(image: ImmutableImage, options: OcrOptions): OcrResult {
            image.width shouldBeGreaterThan 0
            lastOptions.set(options)
            if (failWithIo.getAndSet(false)) {
                throw IOException("native codec failed at /srv/private/native-codec")
            }
            if (failNext.getAndSet(false)) {
                throw OcrException("native OCR failed at /srv/private/tessdata")
            }
            return OcrResult(
                text = "BLUETAPE OCR",
                options = options,
            )
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
