package io.bluetape4k.images.ktor

import com.sksamuel.scrimage.nio.ImageWriter
import com.sksamuel.scrimage.nio.PngWriter
import io.bluetape4k.images.ImageDecodeLimits
import io.bluetape4k.images.immutableExternalImageOf
import io.bluetape4k.images.toByteArray
import io.bluetape4k.ktor.core.ApiErrorResponse
import io.bluetape4k.ktor.core.intQueryParameter
import io.bluetape4k.ktor.core.respondApiError
import io.bluetape4k.logging.KotlinLogging
import io.bluetape4k.logging.warn
import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requirePositiveNumber
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import java.io.IOException

private const val DEFAULT_IMAGE_ROUTE = "/images"
private const val DEFAULT_IMAGE_FIELD = "file"
private const val DEFAULT_MAX_INPUT_BYTES = 10 * 1024 * 1024
private const val DEFAULT_MAX_INPUT_PIXELS = 16_777_216L
private const val DEFAULT_MAX_INPUT_SIDE = 8_192
private const val DEFAULT_THUMBNAIL_SIDE = 320
private const val DEFAULT_MAX_THUMBNAIL_SIDE = 2_048
private const val INVALID_IMAGE_PAYLOAD_MESSAGE = "Invalid image payload."
private const val UNKNOWN_IMAGE_DIMENSIONS_MESSAGE = "Image input dimensions could not be determined."

private val log = KotlinLogging.logger {}

/**
 * compact image thumbnail endpoint를 위한 Ktor route configuration입니다.
 *
 * 이 helper는 의도적으로 작게 유지됩니다. multipart image part 하나를 decode하고
 * `bluetape4k-images`로 thumbnail을 만든 뒤 인코딩 byte를 caller에 돌려줍니다.
 * persistence, S3, CDN URL, native libvips가 필요한 application은 이 route 밖에서
 * 해당 concern을 조합해야 합니다.
 */
class ImageThumbnailKtorRoutesConfig(
    val routePath: String = DEFAULT_IMAGE_ROUTE,
    val multipartFieldName: String = DEFAULT_IMAGE_FIELD,
    val maxInputBytes: Long = DEFAULT_MAX_INPUT_BYTES.toLong(),
    val maxInputPixels: Long = DEFAULT_MAX_INPUT_PIXELS,
    val maxInputSide: Int = DEFAULT_MAX_INPUT_SIDE,
    val defaultMaxSide: Int = DEFAULT_THUMBNAIL_SIDE,
    val maxAllowedSide: Int = DEFAULT_MAX_THUMBNAIL_SIDE,
    val writer: ImageWriter = PngWriter.MaxCompression,
    val responseContentType: ContentType = ContentType.Image.PNG,
) {

    init {
        routePath.requireNotBlank("routePath")
        multipartFieldName.requireNotBlank("multipartFieldName")
        maxInputBytes.requirePositiveNumber("maxInputBytes")
        maxInputPixels.requirePositiveNumber("maxInputPixels")
        maxInputSide.requirePositiveNumber("maxInputSide")
        defaultMaxSide.requirePositiveNumber("defaultMaxSide")
        maxAllowedSide.requirePositiveNumber("maxAllowedSide")
        require(defaultMaxSide <= maxAllowedSide) {
            "defaultMaxSide must be less than or equal to maxAllowedSide."
        }
    }
}

/**
 * multipart thumbnail endpoint를 등록합니다.
 *
 * 라우트:
 * - `POST {routePath}/thumbnail?maxSide=320`은 multipart field `file`을 읽고
 *   인코딩된 thumbnail byte를 반환합니다.
 */
fun Route.bluetape4kImageThumbnailRoutes(
    config: ImageThumbnailKtorRoutesConfig = ImageThumbnailKtorRoutesConfig(),
) {
    route(config.routePath) {
        post("/thumbnail") {
            call.respondImageRoute {
                val maxSide = call.thumbnailMaxSide(config)
                val uploadBytes = call.receiveImageUpload(config)
                val thumbnailBytes = withContext(Dispatchers.IO) {
                    try {
                        immutableExternalImageOf(uploadBytes, config.toDecodeLimits())
                            .max(maxSide, maxSide)
                            .forWriter(config.writer)
                            .toByteArray()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IllegalArgumentException) {
                        if (e.message == UNKNOWN_IMAGE_DIMENSIONS_MESSAGE) {
                            throw IllegalArgumentException(INVALID_IMAGE_PAYLOAD_MESSAGE, e)
                        }
                        throw e
                    } catch (e: IOException) {
                        throw e
                    } catch (e: RuntimeException) {
                        throw IllegalArgumentException("Invalid image payload.", e)
                    }
                }

                call.respondBytes(thumbnailBytes, config.responseContentType, HttpStatusCode.OK)
            }
        }
    }
}

/**
 * shared bluetape4k Ktor error payload에 대한 source-compatible alias입니다.
 */
@Deprecated(
    message = "Use io.bluetape4k.ktor.core.ApiErrorResponse.",
    replaceWith = ReplaceWith("ApiErrorResponse", "io.bluetape4k.ktor.core.ApiErrorResponse")
)
typealias ImageRouteErrorResponse = ApiErrorResponse

private suspend fun ApplicationCall.receiveImageUpload(config: ImageThumbnailKtorRoutesConfig): ByteArray {
    val multipart = receiveMultipart()
    var foundWrongField = false

    while (true) {
        val part = multipart.readPart() ?: break
        try {
            if (part.name == config.multipartFieldName) {
                val bytes = when (part) {
                    is PartData.FileItem -> part.provider().readImageBytes(config)
                    is PartData.BinaryChannelItem -> part.provider().readImageBytes(config)
                    is PartData.BinaryItem -> throw IllegalArgumentException(
                        "Multipart field '${config.multipartFieldName}' must be a streamed file upload."
                    )
                    is PartData.FormItem -> throw IllegalArgumentException(
                        "Multipart field '${config.multipartFieldName}' must be a file."
                    )
                }
                require(bytes.size <= config.maxInputBytes) {
                    "Image upload exceeds maxInputBytes=${config.maxInputBytes}."
                }
                require(bytes.isNotEmpty()) {
                    "Image upload must not be empty."
                }
                return bytes
            }
            if (part is PartData.FileItem || part is PartData.BinaryItem || part is PartData.BinaryChannelItem) {
                foundWrongField = true
            }
        } finally {
            part.release()
        }
    }

    val detail = if (foundWrongField) {
        "Expected multipart file field '${config.multipartFieldName}'."
    } else {
        "Multipart file field '${config.multipartFieldName}' is required."
    }
    throw IllegalArgumentException(detail)
}

private suspend fun ByteReadChannel.readImageBytes(config: ImageThumbnailKtorRoutesConfig): ByteArray =
    readRemaining(config.maxInputBytes.coerceAtMost(Long.MAX_VALUE - 1L) + 1L).readByteArray()

private fun ImageThumbnailKtorRoutesConfig.toDecodeLimits(): ImageDecodeLimits =
    ImageDecodeLimits(
        maxEncodedBytes = maxInputBytes,
        maxDecodedPixels = maxInputPixels,
        maxDecodedSide = maxInputSide,
    )

private fun ApplicationCall.thumbnailMaxSide(config: ImageThumbnailKtorRoutesConfig): Int {
    val maxSide = intQueryParameter(
        name = "maxSide",
        defaultValue = config.defaultMaxSide,
        range = 1..config.maxAllowedSide
    )
    return requireNotNull(maxSide) { "maxSide must be resolved from the default or query parameter." }
}

private suspend fun ApplicationCall.respondImageRoute(block: suspend () -> Unit) {
    try {
        block()
    } catch (e: IllegalArgumentException) {
        respondApiError(
            status = HttpStatusCode.BadRequest,
            error = "bad_request",
            message = e.message ?: "Invalid image request."
        )
    } catch (e: IOException) {
        log.warn(e) {
            "Image thumbnail request failed. reason=io_failure path=${request.local.uri}"
        }
        respondApiError(
            status = HttpStatusCode.BadRequest,
            error = "bad_request",
            message = INVALID_IMAGE_PAYLOAD_MESSAGE,
        )
    }
}
