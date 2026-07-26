package io.bluetape4k.images.examples.spring.intelligence.web

import io.bluetape4k.images.examples.spring.intelligence.model.ImageIntelligenceResponse
import io.bluetape4k.images.examples.spring.intelligence.service.ImageIntelligenceOperations
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
internal class ImageIntelligenceController(
    private val service: ImageIntelligenceOperations,
) {

    @PostMapping(
        "/api/images/intelligence",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
    )
    suspend fun analyze(
        @RequestParam("file") file: MultipartFile,
    ): ImageIntelligenceResponse =
        service.analyze(file)
}
