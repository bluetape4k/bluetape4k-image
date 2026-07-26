package io.bluetape4k.images.examples.spring.intelligence.web

import io.bluetape4k.images.examples.spring.intelligence.service.ImagePayloadTooLargeException
import io.bluetape4k.images.examples.spring.intelligence.service.ImageWorkflowException
import io.bluetape4k.images.examples.spring.intelligence.service.InvalidImageUploadException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.support.MissingServletRequestPartException

@RestControllerAdvice
internal class ImageIntelligenceExceptionHandler {

    @ExceptionHandler(InvalidImageUploadException::class)
    fun invalidUpload(exception: InvalidImageUploadException): ProblemDetail =
        problem(
            status = if (exception is ImagePayloadTooLargeException) {
                HttpStatus.CONTENT_TOO_LARGE
            } else {
                HttpStatus.BAD_REQUEST
            },
            title = "Image upload rejected",
            detail = exception.safeDetail(),
            reasonCode = exception.reasonCode,
        )

    @ExceptionHandler(
        MissingServletRequestPartException::class,
        MissingServletRequestParameterException::class,
    )
    fun missingFile(): ProblemDetail =
        problem(
            status = HttpStatus.BAD_REQUEST,
            title = "Required multipart file is missing",
            detail = "The multipart file part is required.",
            reasonCode = "missing_file",
        )

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun multipartOverflow(): ProblemDetail =
        problem(
            status = HttpStatus.CONTENT_TOO_LARGE,
            title = "Image upload rejected",
            detail = "The uploaded file exceeds the configured size limit.",
            reasonCode = "payload_too_large",
        )

    @ExceptionHandler(ImageWorkflowException::class)
    fun workflowFailure(): ProblemDetail =
        problem(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            title = "Image analysis failed",
            detail = "The image analysis workflow could not be completed.",
            reasonCode = "workflow_failed",
        )

    private fun InvalidImageUploadException.safeDetail(): String =
        when (reasonCode) {
            "empty_input" -> "The uploaded file is empty."
            "unsupported_media_type" -> "The uploaded content type is not supported."
            "unsupported_image_format" -> "The uploaded image format is not supported."
            "media_type_mismatch" -> "The uploaded content type does not match the image data."
            "image_not_decodable" -> "The uploaded file is not a decodable image."
            "image_read_failed" -> "The uploaded file could not be read."
            "payload_too_large" -> "The uploaded file exceeds a configured image limit."
            else -> "The uploaded image was rejected."
        }

    private fun problem(
        status: HttpStatus,
        title: String,
        detail: String,
        reasonCode: String,
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).apply {
            this.title = title
            setProperty("reasonCode", reasonCode)
        }
}
