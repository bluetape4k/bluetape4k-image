package io.bluetape4k.images.examples.spring.intelligence.web

import com.sksamuel.scrimage.nio.PngWriter
import io.bluetape4k.assertions.shouldNotContain
import io.bluetape4k.images.examples.spring.intelligence.support.qrImage
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
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
@ActiveProfiles("demo")
class ImageIntelligenceControllerTest(
    @param:Autowired private val mockMvc: MockMvc,
) {

    @Test
    fun `generated visitor QR returns a completed allow envelope`() {
        val result = mockMvc.perform(
            multipart("/api/images/intelligence")
                .file(file("visitor.png", MediaType.IMAGE_PNG_VALUE, visitorQrBytes())),
        ).dispatch()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.decision").value("ALLOW"))
            .andExpect(jsonPath("$.reasons").isEmpty)
            .andExpect(jsonPath("$.ocr.provider").value("fixture-ocr"))
            .andExpect(jsonPath("$.detection.provider").value("fixture-detector"))
            .andExpect(jsonPath("$.barcodes.provider").value("zxing"))
            .andExpect(jsonPath("$.barcodes.items[0].text").value("visitor:PASS-001"))
            .andReturn()

        val json = result.response.contentAsString
        listOf(
            "WorkContext",
            "WorkReport",
            "stackTrace",
            "rawBytes",
            "tessdataPath",
        ).forEach(json::shouldNotContain)
    }

    @Test
    fun `rejects missing empty unsupported mismatched and malformed uploads`() {
        mockMvc.perform(multipart("/api/images/intelligence"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.reasonCode").value("missing_file"))

        listOf(
            file("empty.png", MediaType.IMAGE_PNG_VALUE, ByteArray(0)),
            file("text.txt", MediaType.TEXT_PLAIN_VALUE, byteArrayOf(1)),
            file("mismatch.jpg", MediaType.IMAGE_JPEG_VALUE, visitorQrBytes()),
            file("malformed.png", MediaType.IMAGE_PNG_VALUE, malformedPng()),
        ).forEach { upload ->
            mockMvc.perform(multipart("/api/images/intelligence").file(upload))
                .dispatch()
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.reasonCode").exists())
        }
    }

    @Test
    fun `rejects encoded side and pixel limits`() {
        listOf(
            file(
                "encoded.png",
                MediaType.IMAGE_PNG_VALUE,
                ByteArray(5 * 1024 * 1024 + 1),
            ),
            file("wide.png", MediaType.IMAGE_PNG_VALUE, pngHeaderBytes(10_000, 100)),
            file("pixels.png", MediaType.IMAGE_PNG_VALUE, pngHeaderBytes(5_000, 5_000)),
        ).forEach { upload ->
            mockMvc.perform(multipart("/api/images/intelligence").file(upload))
                .dispatch()
                .andExpect(status().isContentTooLarge)
                .andExpect(jsonPath("$.reasonCode").value("payload_too_large"))
        }
    }

    private fun ResultActions.dispatch(): ResultActions {
        val result = andExpect(request().asyncStarted()).andReturn()
        return mockMvc.perform(asyncDispatch(result))
    }

    private fun visitorQrBytes(): ByteArray =
        qrImage().forWriter(PngWriter.MaxCompression).bytes()

    private fun malformedPng(): ByteArray =
        byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x01, 0x02,
        )

    private fun file(filename: String, contentType: String?, bytes: ByteArray): MockMultipartFile =
        MockMultipartFile("file", filename, contentType, bytes)

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
            },
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
        val PNG_SIGNATURE: ByteArray = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
    }
}
