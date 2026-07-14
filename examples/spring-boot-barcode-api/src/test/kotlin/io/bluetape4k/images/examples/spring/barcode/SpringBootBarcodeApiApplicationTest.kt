package io.bluetape4k.images.examples.spring.barcode

import com.sksamuel.scrimage.nio.JpegWriter
import com.sksamuel.scrimage.webp.WebpWriter
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldNotContain
import io.bluetape4k.images.immutableImageOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpringBootBarcodeApiApplicationTest(
    @param:Autowired private val mockMvc: MockMvc,
) {

    private val fixtures = BarcodeExampleFixtures()

    @Test
    fun `uploads QR image and returns bounded provider neutral JSON`() {
        val result = mockMvc.perform(
            multipart("/api/barcodes/extract")
                .file(file("sample.png", MediaType.IMAGE_PNG_VALUE, sampleBytes()))
        ).dispatch()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.results[0].text").value("bluetape4k-barcode-quickstart"))
            .andExpect(jsonPath("$.results[0].format").value("QR_CODE"))
            .andExpect(jsonPath("$.results[0].provider").value("ZXing"))
            .andReturn()

        val json = result.response.contentAsString
        listOf(
            "rawBytes",
            "rawBackendFormat",
            "metadata",
            "region",
            "stackTrace",
            "sample.png",
        ).forEach(json::shouldNotContain)
    }

    @Test
    fun `uploads valid image with no barcode and returns empty success`() {
        mockMvc.perform(
            multipart("/api/barcodes/extract")
                .file(file("blank.png", MediaType.IMAGE_PNG_VALUE, noResultBytes()))
        ).dispatch()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.count").value(0))
            .andExpect(jsonPath("$.results").isEmpty)
    }

    @Test
    fun `accepts JPEG and WebP multipart uploads`() {
        val image = immutableImageOf(sampleBytes())
        listOf(
            file("sample.jpg", MediaType.IMAGE_JPEG_VALUE, image.forWriter(JpegWriter(90, false)).bytes()),
            file("sample.webp", "image/webp", image.forWriter(WebpWriter.DEFAULT).bytes()),
        ).forEach { upload ->
            mockMvc.perform(multipart("/api/barcodes/extract").file(upload))
                .dispatch()
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.count").value(1))
        }
    }

    @Test
    fun `returns sanitized malformed input error`() {
        val result = mockMvc.perform(
            multipart("/api/barcodes/extract")
                .file(file("secret.bin", MediaType.IMAGE_PNG_VALUE, malformedBytes()))
        ).dispatch()
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("malformed_input"))
            .andExpect(jsonPath("$.reason").value("MALFORMED_INPUT"))
            .andExpect(jsonPath("$.message").value("The uploaded file is not a decodable image."))
            .andReturn()

        result.response.contentAsString.shouldNotContain("secret.bin")
        result.response.contentAsString.shouldNotContain("not-an-image")
    }

    @Test
    fun `rejects empty unsupported and missing content type uploads`() {
        mockMvc.perform(
            multipart("/api/barcodes/extract")
                .file(file("empty.png", MediaType.IMAGE_PNG_VALUE, ByteArray(0)))
        ).dispatch()
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("empty_input"))

        listOf(MediaType.TEXT_PLAIN_VALUE, null).forEach { contentType ->
            mockMvc.perform(
                multipart("/api/barcodes/extract")
                    .file(file("upload", contentType, byteArrayOf(1)))
            ).dispatch()
                .andExpect(status().isUnsupportedMediaType)
                .andExpect(jsonPath("$.error").value("unsupported_media_type"))
        }
    }

    @Test
    fun `rejects encoded and decoded upload limits`() {
        mockMvc.perform(
            multipart("/api/barcodes/extract")
                .file(file("large.png", MediaType.IMAGE_PNG_VALUE, ByteArray(5 * 1024 * 1024 + 1)))
        ).dispatch()
            .andExpect(status().isContentTooLarge)
            .andExpect(jsonPath("$.error").value("payload_too_large"))

        mockMvc.perform(
            multipart("/api/barcodes/extract")
                .file(file("wide.png", MediaType.IMAGE_PNG_VALUE, pngHeaderBytes(10_000, 100)))
        ).dispatch()
            .andExpect(status().isContentTooLarge)
            .andExpect(jsonPath("$.error").value("payload_too_large"))

        mockMvc.perform(
            multipart("/api/barcodes/extract")
                .file(file("pixels.png", MediaType.IMAGE_PNG_VALUE, pngHeaderBytes(5_000, 5_000)))
        ).dispatch()
            .andExpect(status().isContentTooLarge)
            .andExpect(jsonPath("$.error").value("payload_too_large"))
    }

    @Test
    fun `omitted file part uses stable empty input response`() {
        mockMvc.perform(multipart("/api/barcodes/extract"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("empty_input"))
            .andExpect(jsonPath("$.message").value("The multipart file part is required."))
    }

    private fun ResultActions.dispatch(): ResultActions {
        val result = andExpect(request().asyncStarted()).andReturn()
        return mockMvc.perform(asyncDispatch(result))
    }

    private fun file(filename: String, contentType: String?, bytes: ByteArray): MockMultipartFile =
        MockMultipartFile("file", filename, contentType, bytes)

    private fun sampleBytes(): ByteArray = fixtures.bytes(BarcodeExampleFixture.SAMPLE)
    private fun noResultBytes(): ByteArray = fixtures.bytes(BarcodeExampleFixture.NO_RESULT)
    private fun malformedBytes(): ByteArray = fixtures.bytes(BarcodeExampleFixture.MALFORMED)

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
}
