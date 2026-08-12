package io.bluetape4k.images.examples.spring.ocr

import com.jayway.jsonpath.JsonPath
import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.images.ocr.OcrEngine
import io.bluetape4k.images.ocr.OcrException
import io.bluetape4k.images.ocr.OcrOptions
import io.bluetape4k.images.ocr.OcrResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "example.ocr.tessdata-path=/tmp/example-tessdata",
    ]
)
internal class SpringBootOcrApiApplicationTest(
    @param:Autowired private val mockMvc: MockMvc,
    @param:Autowired private val testOcrEngine: TestOcrEngine,
) {

    @BeforeEach
    fun beforeEach() {
        testOcrEngine.reset()
    }

    @Test
    fun `recognizes uploaded image with parsed languages`() {
        val result = mockMvc.perform(
            multipart("/api/ocr")
                .file(pngFile())
                .param("languages", "eng+kor")
        )
            .andExpect(request().asyncStarted())
            .andReturn()
            .dispatch()
            .andExpect(status().isOk)
            .andReturn()

        val response = result.response.contentAsString
        response.readJsonPath<String>("$.text") shouldBeEqualTo "BLUETAPE OCR"
        response.readJsonPath<List<String>>("$.languages") shouldBeEqualTo listOf("eng", "kor")
        response.readJsonPath<Int>("$.characterCount") shouldBeEqualTo "BLUETAPE OCR".length

        val options = requireNotNull(testOcrEngine.lastOptions.get())
        options.languages shouldBeEqualTo listOf("eng", "kor")
        options.tessdataPath shouldBeEqualTo "/tmp/example-tessdata"
    }

    @Test
    fun `rejects unsupported content type`() {
        val textFile = MockMultipartFile(
            "file",
            "note.txt",
            MediaType.TEXT_PLAIN_VALUE,
            "not an image".toByteArray(),
        )

        val result = mockMvc.perform(multipart("/api/ocr").file(textFile))
            .andExpect(request().asyncStarted())
            .andReturn()
            .dispatch()
            .andExpect(status().isBadRequest)
            .andReturn()

        val error = result.response.contentAsString
        error.readJsonPath<String>("$.error") shouldBeEqualTo "bad_request"
        error.readJsonPath<String>("$.message").contains("Unsupported image content type").shouldBeTrue()
    }

    @Test
    fun `maps OCR failures to service unavailable`() {
        testOcrEngine.failNext.set(true)

        val result = mockMvc.perform(multipart("/api/ocr").file(pngFile()))
            .andExpect(request().asyncStarted())
            .andReturn()
            .dispatch()
            .andExpect(status().isServiceUnavailable)
            .andReturn()

        val error = result.response.contentAsString
        error.readJsonPath<String>("$.error") shouldBeEqualTo "ocr_unavailable"
        error.readJsonPath<String>("$.message") shouldBeEqualTo "Test OCR runtime is unavailable."
    }

    @Test
    fun `rejects decoded pixel limit before OCR engine is called`() {
        val oversizedImage = MockMultipartFile(
            "file",
            "oversized.png",
            MediaType.IMAGE_PNG_VALUE,
            pngHeaderBytes(width = 10_000, height = 10_000),
        )

        val result = mockMvc.perform(multipart("/api/ocr").file(oversizedImage))
            .andExpect(request().asyncStarted())
            .andReturn()
            .dispatch()
            .andExpect(status().isBadRequest)
            .andReturn()

        val error = result.response.contentAsString
        error.readJsonPath<String>("$.error") shouldBeEqualTo "bad_request"
        error.readJsonPath<String>("$.message") shouldContain "decodedPixels"
        testOcrEngine.lastOptions.get().shouldBeNull()
    }

    @Test
    fun `rejects upload when image dimensions cannot be probed before OCR engine is called`() {
        val malformedImage = MockMultipartFile(
            "file",
            "malformed.png",
            MediaType.IMAGE_PNG_VALUE,
            "not an encoded image".toByteArray(),
        )

        val result = mockMvc.perform(multipart("/api/ocr").file(malformedImage))
            .andExpect(request().asyncStarted())
            .andReturn()
            .dispatch()
            .andExpect(status().isBadRequest)
            .andReturn()

        val error = result.response.contentAsString
        error.readJsonPath<String>("$.error") shouldBeEqualTo "bad_request"
        error.readJsonPath<String>("$.message") shouldContain "dimensions could not be determined"
        testOcrEngine.lastOptions.get().shouldBeNull()
    }

    @Test
    fun `rejects header-valid malformed image before OCR engine is called`() {
        val malformedImage = MockMultipartFile(
            "file",
            "malformed.png",
            MediaType.IMAGE_PNG_VALUE,
            pngHeaderBytes(width = 10, height = 10),
        )

        val result = mockMvc.perform(multipart("/api/ocr").file(malformedImage))
            .andExpect(request().asyncStarted())
            .andReturn()
            .dispatch()
            .andExpect(status().isBadRequest)
            .andReturn()

        val error = result.response.contentAsString
        error.readJsonPath<String>("$.error") shouldBeEqualTo "bad_request"
        error.readJsonPath<String>("$.message") shouldContain "could not be decoded"
        testOcrEngine.lastOptions.get().shouldBeNull()
    }

    private inline fun <reified T> String.readJsonPath(path: String): T =
        JsonPath.read(this, path)

    private fun MvcResult.dispatch(): org.springframework.test.web.servlet.ResultActions =
        mockMvc.perform(asyncDispatch(this))

    private fun pngFile(): MockMultipartFile =
        MockMultipartFile(
            "file",
            "sample.png",
            MediaType.IMAGE_PNG_VALUE,
            samplePngBytes(),
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

        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return output.toByteArray()
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

    @TestConfiguration(proxyBeanMethods = false)
    class TestOcrConfiguration {

        @Bean
        @Primary
        fun testOcrEngine(): TestOcrEngine =
            TestOcrEngine()
    }
}

internal class TestOcrEngine : OcrEngine {

    val lastOptions: AtomicReference<OcrOptions?> = AtomicReference()
    val failNext: AtomicBoolean = AtomicBoolean(false)

    fun reset() {
        lastOptions.set(null)
        failNext.set(false)
    }

    override fun recognize(image: ImmutableImage, options: OcrOptions): OcrResult {
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
