package io.bluetape4k.images.examples.spring.barcode

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldNotContain
import io.bluetape4k.images.barcode.BarcodeException
import io.bluetape4k.images.barcode.BarcodeFailureReason
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.http.HttpStatus
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.support.MissingServletRequestPartException

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BarcodeApiExceptionHandlerTest {

    private val handler = BarcodeApiExceptionHandler()

    @Test
    fun `maps request and resolver failures to stable responses`() {
        val request = handler.handleRequest(
            BarcodeRequestException(
                status = HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                error = "unsupported_media_type",
                message = "The uploaded content type is not supported.",
            )
        )
        request.statusCode shouldBeEqualTo HttpStatus.UNSUPPORTED_MEDIA_TYPE
        request.body shouldBeEqualTo BarcodeErrorResponse(
            error = "unsupported_media_type",
            message = "The uploaded content type is not supported.",
        )

        val oversized = handler.handleMaxUploadSize(MaxUploadSizeExceededException(5L * 1024L * 1024L))
        oversized.statusCode shouldBeEqualTo HttpStatus.CONTENT_TOO_LARGE
        oversized.body?.error shouldBeEqualTo "payload_too_large"

        val missing = handler.handleMissingPart(MissingServletRequestPartException("file"))
        missing.statusCode shouldBeEqualTo HttpStatus.BAD_REQUEST
        missing.body?.error shouldBeEqualTo "empty_input"
    }

    @ParameterizedTest
    @EnumSource(BarcodeFailureReason::class)
    fun `maps barcode failures without echoing provider detail`(reason: BarcodeFailureReason) {
        val response = handler.handleBarcode(
            BarcodeException(reason, "provider secret /private/image.png")
        )
        val expectedStatus = when (reason) {
            BarcodeFailureReason.MALFORMED_INPUT,
            BarcodeFailureReason.UNSUPPORTED_FORMAT -> HttpStatus.BAD_REQUEST

            BarcodeFailureReason.PROVIDER_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE
            else -> HttpStatus.INTERNAL_SERVER_ERROR
        }
        val expectedMessage = when (reason) {
            BarcodeFailureReason.MALFORMED_INPUT -> "The uploaded file is not a decodable image."
            BarcodeFailureReason.UNSUPPORTED_FORMAT -> "The requested barcode format is not supported."
            BarcodeFailureReason.PROVIDER_UNAVAILABLE -> "The barcode provider is unavailable."
            else -> "Barcode extraction failed."
        }

        response.statusCode shouldBeEqualTo expectedStatus
        response.body?.error shouldBeEqualTo reason.name.lowercase()
        response.body?.reason shouldBeEqualTo reason.name
        response.body?.message shouldBeEqualTo expectedMessage
        response.body?.message.orEmpty().shouldNotContain("provider secret")
        response.body?.message.orEmpty().shouldNotContain("/private")
    }
}
