package io.bluetape4k.images.examples.ktor.ocr

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.images.ocr.OcrEngine
import io.bluetape4k.images.ocr.OcrException
import io.bluetape4k.images.ocr.OcrOptions
import io.bluetape4k.images.ocr.OcrResult
import io.bluetape4k.ktor.core.ApiErrorResponse
import io.bluetape4k.ktor.testing.bluetape4kJsonClient
import io.bluetape4k.ktor.testing.shouldHaveStatus
import io.ktor.client.call.body
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.append
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
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

        response shouldHaveStatus HttpStatusCode.OK
    }

    @Test
    fun `recognizes uploaded image with parsed languages`() = testApplication {
        application {
            configureTestKtorOcrApi()
        }
        val client = bluetape4kJsonClient()

        val response = client.post("/api/ocr?languages=eng+kor") {
            setBody(imageMultipart("file", samplePngBytes(), ContentType.Image.PNG))
        }

        response shouldHaveStatus HttpStatusCode.OK
        val body = response.body<OcrTextResponse>()
        body.text shouldBeEqualTo "BLUETAPE OCR"
        body.languages shouldBeEqualTo listOf("eng", "kor")
        body.characterCount shouldBeEqualTo "BLUETAPE OCR".length

        val options = requireNotNull(testOcrEngine.lastOptions.get())
        options.languages shouldBeEqualTo listOf("eng", "kor")
        options.tessdataPath shouldBeEqualTo "/tmp/example-tessdata"
    }

    @Test
    fun `rejects request without expected file field`() = testApplication {
        application {
            configureTestKtorOcrApi()
        }
        val client = bluetape4kJsonClient()

        val response = client.post("/api/ocr") {
            setBody(imageMultipart("other", samplePngBytes(), ContentType.Image.PNG))
        }

        response shouldHaveStatus HttpStatusCode.BadRequest
        val body = response.body<ApiErrorResponse>()
        body.error shouldBeEqualTo "bad_request"
        body.status shouldBeEqualTo HttpStatusCode.BadRequest.value
        body.message.contains("Expected multipart file field").shouldBeTrue()
    }

    @Test
    fun `rejects unsupported content type`() = testApplication {
        application {
            configureTestKtorOcrApi()
        }
        val client = bluetape4kJsonClient()

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

        response shouldHaveStatus HttpStatusCode.BadRequest
        val body = response.body<ApiErrorResponse>()
        body.error shouldBeEqualTo "bad_request"
        body.status shouldBeEqualTo HttpStatusCode.BadRequest.value
        body.message.contains("Unsupported image content type").shouldBeTrue()
    }

    @Test
    fun `maps OCR failures to service unavailable`() = testApplication {
        application {
            configureTestKtorOcrApi()
        }
        testOcrEngine.failNext.set(true)
        val client = bluetape4kJsonClient()

        val response = client.post("/api/ocr") {
            setBody(imageMultipart("file", samplePngBytes(), ContentType.Image.PNG))
        }

        response shouldHaveStatus HttpStatusCode.ServiceUnavailable
        val body = response.body<ApiErrorResponse>()
        body.error shouldBeEqualTo "ocr_unavailable"
        body.status shouldBeEqualTo HttpStatusCode.ServiceUnavailable.value
        body.message shouldBeEqualTo "Test OCR runtime is unavailable."
    }

    private fun io.ktor.server.application.Application.configureTestKtorOcrApi() {
        configureKtorOcrApi(
            config = KtorOcrApiConfig(tessdataPath = "/tmp/example-tessdata"),
            ocrEngine = testOcrEngine,
        )
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

    private class TestOcrEngine : OcrEngine {

        val lastOptions: AtomicReference<OcrOptions?> = AtomicReference()
        val failNext: AtomicBoolean = AtomicBoolean(false)

        fun reset() {
            lastOptions.set(null)
            failNext.set(false)
        }

        override fun recognize(image: ImmutableImage, options: OcrOptions): OcrResult {
            image.width shouldBeGreaterThan 0
            lastOptions.set(options)
            if (failNext.getAndSet(false)) {
                throw OcrException("Test OCR runtime is unavailable.")
            }
            return OcrResult(
                text = "BLUETAPE OCR",
                options = options,
            )
        }
    }
}
