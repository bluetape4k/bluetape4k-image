package io.bluetape4k.images.examples.spring

import com.jayway.jsonpath.JsonPath
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldBeTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "bluetape4k.images.storage.local.root-dir=build/tmp/spring-boot-image-api-test/storage",
    ]
)
class SpringBootImageApiApplicationTest(
    @param:Autowired private val mockMvc: MockMvc,
) {

    @Test
    fun `uploads image stores original and thumbnail and exposes local read urls`() {
        val uploadResult = mockMvc.perform(
            multipart("/api/images")
                .file(jpegFile())
                .param("maxSide", "160")
        )
            .andExpect(request().asyncStarted())
            .andReturn()
            .dispatch()
            .andExpect(status().isCreated)
            .andReturn()

        val response = uploadResult.response.contentAsString
        val originalKey = response.readJsonPath<String>("$.original.key")
        val thumbnailKey = response.readJsonPath<String>("$.thumbnail.key")
        val originalUrl = response.readJsonPath<String>("$.original.url")
        val thumbnailUrl = response.readJsonPath<String>("$.thumbnail.url")

        originalKey.startsWith("originals/").shouldBeTrue()
        thumbnailKey.startsWith("thumbnails/").shouldBeTrue()
        originalUrl shouldBeEqualTo "/api/images/$originalKey"
        thumbnailUrl shouldBeEqualTo "/api/images/$thumbnailKey"
        response.readJsonPath<Int>("$.originalBytes").shouldBeGreaterThan(0)
        response.readJsonPath<Int>("$.thumbnailBytes").shouldBeGreaterThan(0)

        val original = mockMvc.perform(get(originalUrl))
            .andExpect(request().asyncStarted())
            .andReturn()
            .dispatch()
            .andExpect(status().isOk)
            .andReturn()
        original.response.contentType shouldBeEqualTo MediaType.IMAGE_JPEG_VALUE
        original.response.contentAsByteArray.size shouldBeGreaterThan 8

        val thumbnail = mockMvc.perform(get(thumbnailUrl))
            .andExpect(request().asyncStarted())
            .andReturn()
            .dispatch()
            .andExpect(status().isOk)
            .andReturn()
        thumbnail.response.contentType shouldBeEqualTo MediaType.IMAGE_PNG_VALUE
        thumbnail.response.contentAsByteArray.copyOfRange(0, PNG_SIGNATURE.size)
            .contentEquals(PNG_SIGNATURE)
            .shouldBeTrue()
    }

    @Test
    fun `rejects unsupported content type`() {
        val textFile = MockMultipartFile(
            "file",
            "note.txt",
            MediaType.TEXT_PLAIN_VALUE,
            "not an image".toByteArray(),
        )

        val result = mockMvc.perform(multipart("/api/images").file(textFile))
            .andExpect(request().asyncStarted())
            .andReturn()
            .dispatch()
            .andExpect(status().isBadRequest)
            .andReturn()

        val error = result.response.contentAsString
        error.readJsonPath<String>("$.error") shouldBeEqualTo "bad_request"
        error.readJsonPath<String>("$.message").contains("Unsupported image content type").shouldBeTrue()
    }

    private inline fun <reified T> String.readJsonPath(path: String): T =
        JsonPath.read(this, path)

    private fun MvcResult.dispatch(): org.springframework.test.web.servlet.ResultActions =
        mockMvc.perform(asyncDispatch(this))

    private fun jpegFile(): MockMultipartFile =
        MockMultipartFile(
            "file",
            "sample.jpg",
            MediaType.IMAGE_JPEG_VALUE,
            sampleJpegBytes(),
        )

    private fun sampleJpegBytes(): ByteArray {
        val image = BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = Color(35, 96, 146)
            graphics.fillRect(0, 0, image.width, image.height)
            graphics.color = Color.WHITE
            graphics.fillOval(80, 40, 160, 120)
        } finally {
            graphics.dispose()
        }

        val output = ByteArrayOutputStream()
        ImageIO.write(image, "jpg", output)
        return output.toByteArray()
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
