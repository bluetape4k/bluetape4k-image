package io.bluetape4k.images.examples.ktor.ocr

import io.bluetape4k.images.immutableImageOf
import io.bluetape4k.images.ocr.OcrEngine
import io.bluetape4k.images.ocr.OcrException
import io.bluetape4k.images.ocr.OcrOptions
import io.bluetape4k.images.ocr.TesseractOcrEngine
import io.bluetape4k.images.ocr.suspendExtractText
import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requirePositiveNumber
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import io.ktor.serialization.kotlinx.json.json
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Runs the local-only Ktor OCR API quickstart.
 */
fun main() {
    embeddedServer(
        factory = Netty,
        port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
        host = "0.0.0.0",
        module = Application::configureKtorOcrApi,
    ).start(wait = true)
}

/**
 * Installs JSON support and the Ktor OCR routes used by the quickstart.
 */
fun Application.configureKtorOcrApi(
    config: KtorOcrApiConfig = KtorOcrApiConfig.fromEnvironment(),
    ocrEngine: OcrEngine = TesseractOcrEngine(),
) {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        )
    }

    val ocrService = KtorOcrService(
        ocrEngine = ocrEngine,
        tessdataPath = config.tessdataPath,
    )

    routing {
        get("/ready") {
            call.respondText("OK", ContentType.Text.Plain)
        }
        route(config.routePath) {
            post {
                call.respondOcrRoute {
                    val upload = call.receiveOcrUpload(config)
                    val languages = parseLanguages(call.request.queryParameters["languages"] ?: config.defaultLanguages)
                    val response = ocrService.recognize(upload.bytes, languages)

                    call.respond(HttpStatusCode.OK, response)
                }
            }
        }
    }
}

/**
 * Example configuration for host-specific OCR runtime settings.
 */
data class KtorOcrApiConfig(
    val routePath: String = "/api/ocr",
    val multipartFieldName: String = "file",
    val maxInputBytes: Long = 10L * 1024L * 1024L,
    val defaultLanguages: String = OcrOptions.DEFAULT_LANGUAGE,
    val tessdataPath: String? = null,
) : java.io.Serializable {

    init {
        routePath.requireNotBlank("routePath")
        multipartFieldName.requireNotBlank("multipartFieldName")
        maxInputBytes.requirePositiveNumber("maxInputBytes")
        defaultLanguages.requireNotBlank("defaultLanguages")
        tessdataPath?.requireNotBlank("tessdataPath")
    }

    companion object {
        private const val serialVersionUID: Long = 1L

        fun fromEnvironment(environment: Map<String, String> = System.getenv()): KtorOcrApiConfig =
            KtorOcrApiConfig(
                tessdataPath = environment["EXAMPLE_OCR_TESSDATA_PATH"]?.takeIf { it.isNotBlank() },
            )
    }
}

/**
 * Converts multipart image uploads into `images-ocr` recognition calls.
 */
class KtorOcrService(
    private val ocrEngine: OcrEngine,
    private val tessdataPath: String?,
) {

    suspend fun recognize(uploadBytes: ByteArray, languages: List<String>): OcrTextResponse {
        val text = immutableImageOf(uploadBytes).suspendExtractText(
            options = OcrOptions(
                languages = languages,
                tessdataPath = tessdataPath,
            ),
            engine = ocrEngine,
        )

        return OcrTextResponse(
            text = text,
            languages = languages,
            characterCount = text.length,
        )
    }
}

/**
 * OCR result returned by the quickstart API.
 */
@Serializable
data class OcrTextResponse(
    val text: String,
    val languages: List<String>,
    val characterCount: Int,
) : java.io.Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Error payload returned by the quickstart API.
 */
@Serializable
data class OcrApiErrorResponse(
    val error: String,
    val message: String,
    val status: Int,
    val path: String,
) : java.io.Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

private data class OcrUpload(
    val bytes: ByteArray,
)

private suspend fun ApplicationCall.receiveOcrUpload(config: KtorOcrApiConfig): OcrUpload {
    val multipart = receiveMultipart()
    var foundWrongField = false

    while (true) {
        val part = multipart.readPart() ?: break
        try {
            if (part.name == config.multipartFieldName) {
                val contentType = part.contentType?.withoutParameters()?.toString()?.lowercase().orEmpty()
                contentType.requireNotBlank("contentType")
                require(contentType in ALLOWED_CONTENT_TYPES) {
                    "Unsupported image content type: $contentType"
                }

                val bytes = when (part) {
                    is PartData.FileItem -> part.provider().readUploadBytes(config)
                    is PartData.BinaryChannelItem -> part.provider().readUploadBytes(config)
                    is PartData.BinaryItem -> throw IllegalArgumentException(
                        "Multipart field '${config.multipartFieldName}' must be a streamed file upload."
                    )
                    is PartData.FormItem -> throw IllegalArgumentException(
                        "Multipart field '${config.multipartFieldName}' must be a file."
                    )
                }
                require(bytes.size <= config.maxInputBytes) {
                    "OCR upload exceeds maxInputBytes=${config.maxInputBytes}."
                }
                require(bytes.isNotEmpty()) {
                    "OCR upload must not be empty."
                }

                return OcrUpload(bytes)
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

private suspend fun ByteReadChannel.readUploadBytes(config: KtorOcrApiConfig): ByteArray =
    readRemaining(config.maxInputBytes + 1).readByteArray()

private fun parseLanguages(value: String): List<String> {
    val languages = value.split(LANGUAGE_SEPARATOR)
        .map { language ->
            val normalized = language.trim()
            normalized.requireNotBlank("language")
            normalized
        }
    require(languages.isNotEmpty()) {
        "languages must not be empty"
    }
    return languages
}

private suspend fun ApplicationCall.respondOcrRoute(block: suspend () -> Unit) {
    try {
        block()
    } catch (e: IllegalArgumentException) {
        respondOcrApiError(
            status = HttpStatusCode.BadRequest,
            error = "bad_request",
            message = e.message ?: "Invalid OCR request.",
        )
    } catch (e: IOException) {
        respondOcrApiError(
            status = HttpStatusCode.BadRequest,
            error = "bad_request",
            message = e.message ?: "Invalid image payload.",
        )
    } catch (e: OcrException) {
        respondOcrApiError(
            status = HttpStatusCode.ServiceUnavailable,
            error = "ocr_unavailable",
            message = e.message ?: "OCR runtime is unavailable.",
        )
    }
}

private suspend fun ApplicationCall.respondOcrApiError(
    status: HttpStatusCode,
    error: String,
    message: String,
) {
    respond(
        status = status,
        message = OcrApiErrorResponse(
            error = error,
            message = message,
            status = status.value,
            path = request.local.uri,
        )
    )
}

private val LANGUAGE_SEPARATOR = Regex("[,+\\s]+")

private val ALLOWED_CONTENT_TYPES = setOf(
    ContentType.Image.JPEG.toString(),
    ContentType.Image.PNG.toString(),
    "image/webp",
    "image/gif",
)
