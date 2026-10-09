package io.bluetape4k.images.examples.spring.controller

import io.bluetape4k.images.examples.spring.model.ApiErrorResponse
import io.bluetape4k.images.examples.spring.model.ImageUploadResponse
import io.bluetape4k.images.examples.spring.model.contentTypeForName
import io.bluetape4k.images.examples.spring.service.LocalImageApiService
import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.logging.coroutines.KLoggingChannel
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile


/**
 * quickstart image API의 upload 및 local download endpoint를 노출합니다.
 */
@RestController
@RequestMapping("/api/images")
class ImageApiController(
    private val imageService: LocalImageApiService,
) {

    companion object: KLoggingChannel()

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun upload(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("maxSide", defaultValue = "320") maxSide: Int,
    ): ResponseEntity<ImageUploadResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(imageService.upload(file, maxSide))

    @GetMapping("/{prefix}/{name:.+}")
    suspend fun download(
        @PathVariable prefix: String,
        @PathVariable name: String,
    ): ResponseEntity<ByteArray> {
        val key = ImageObjectKey.of(prefix, name)
        val contentType = contentTypeForName(name)
        val bytes = imageService.download(key)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, contentType)
            .body(bytes)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(e: IllegalArgumentException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.badRequest().body(
            ApiErrorResponse(
                error = "bad_request",
                message = e.message ?: "Invalid image request.",
            )
        )
}
