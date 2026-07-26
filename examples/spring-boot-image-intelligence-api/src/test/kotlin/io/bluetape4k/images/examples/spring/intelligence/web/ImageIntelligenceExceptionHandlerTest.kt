package io.bluetape4k.images.examples.spring.intelligence.web

import io.bluetape4k.assertions.shouldNotContain
import io.bluetape4k.images.examples.spring.intelligence.service.ImageIntelligenceOperations
import io.bluetape4k.images.examples.spring.intelligence.service.ImageWorkflowException
import org.junit.jupiter.api.Test
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

    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(
            ImageIntelligenceController(
                ImageIntelligenceOperations {
                    throw ImageWorkflowException(
                        reasonCode = "missing_workflow_result",
                        message = "secret-context-value=/private/native",
                    )
                },
            ),
        )
        .setControllerAdvice(ImageIntelligenceExceptionHandler())
        .build()

    @Test
    fun `workflow corruption returns a sanitized problem detail`() {
        val result = mockMvc.perform(
            multipart("/api/images/intelligence")
                .file(
                    MockMultipartFile(
                        "file",
                        "upload.png",
                        MediaType.IMAGE_PNG_VALUE,
                        byteArrayOf(1),
                    ),
                ),
        ).dispatch()
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.reasonCode").value("workflow_failed"))
            .andReturn()

        result.response.contentAsString.shouldNotContain("secret-context-value")
        result.response.contentAsString.shouldNotContain("/private/native")
        result.response.contentAsString.shouldNotContain("stackTrace")
    }

    private fun ResultActions.dispatch(): ResultActions {
        val result = andExpect(request().asyncStarted()).andReturn()
        return mockMvc.perform(asyncDispatch(result))
    }
}
