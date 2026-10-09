package io.bluetape4k.images.examples.spring.intelligence.web

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldNotContain
import io.bluetape4k.images.examples.spring.intelligence.service.ImageProbeFailureException
import io.bluetape4k.images.examples.spring.intelligence.service.ImageWorkflowException
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class ImageIntelligenceExceptionHandlerTest {

    companion object: KLogging()

    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(
            ImageIntelligenceController {
                throw ImageWorkflowException(
                    reasonCode = "missing_workflow_result",
                    message = "secret-context-value=/private/native",
                )
            },
        )
        .setControllerAdvice(ImageIntelligenceExceptionHandler())
        .build()

    @Test
    fun `workflow corruption returns a sanitized problem detail`() {
        val result = mockMvc
            .perform(
                multipart("/api/images/intelligence")
                    .file(
                        MockMultipartFile(
                            "file",
                            "upload.png",
                            MediaType.IMAGE_PNG_VALUE,
                            byteArrayOf(1),
                        ),
                    ),
            )
            .dispatch()
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.reasonCode").value("workflow_failed"))
            .andReturn()

        val content = result.response.contentAsString
        log.debug { "content=$content" }
        content shouldNotContain "secret-context-value"
        content shouldNotContain "/private/native"
        content shouldNotContain "stackTrace"
    }

    @Test
    fun `probe failure returns an internal sanitized problem detail`() {
        val problem = ImageIntelligenceExceptionHandler().invalidUpload(
            ImageProbeFailureException(IllegalStateException("parser-secret=/private/native")),
        )

        log.debug { "problem=$problem" }
        problem.status shouldBeEqualTo HttpStatus.INTERNAL_SERVER_ERROR.value()
        problem.detail shouldBeEqualTo "The uploaded image could not be inspected."
        problem.properties?.get("reasonCode") shouldBeEqualTo "image_probe_failed"
        problem.detail shouldNotContain "parser-secret"
        problem.detail shouldNotContain "/private/native"
    }

    private fun ResultActions.dispatch(): ResultActions {
        val result = andExpect(request().asyncStarted()).andReturn()
        return mockMvc.perform(asyncDispatch(result))
    }
}
