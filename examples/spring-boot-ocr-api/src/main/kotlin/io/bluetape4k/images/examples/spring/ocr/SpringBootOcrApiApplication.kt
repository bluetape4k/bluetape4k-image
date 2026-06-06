package io.bluetape4k.images.examples.spring.ocr

import io.bluetape4k.images.immutableImageOf
import io.bluetape4k.images.ocr.OcrEngine
import io.bluetape4k.images.ocr.OcrException
import io.bluetape4k.images.ocr.OcrOptions
import io.bluetape4k.images.ocr.TesseractOcrEngine
import io.bluetape4k.images.ocr.suspendExtractText
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.io.Serializable

fun main(args: Array<String>) {
    runApplication<SpringBootOcrApiApplication>(*args)
}

/**
 * Spring Boot quickstart application for exposing OCR extraction over HTTP.
 */
@SpringBootApplication
class SpringBootOcrApiApplication

/**
 * Example configuration for host-specific OCR runtime settings.
 */
@ConfigurationProperties(prefix = "example.ocr")
data class ExampleOcrProperties(
    val tessdataPath: String? = null,
) : Serializable {

    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Wires the quickstart OCR service and its default Tess4J-backed engine.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ExampleOcrProperties::class)
class OcrApiConfiguration {

    @Bean
    fun ocrEngine(): OcrEngine =
        TesseractOcrEngine()

    @Bean
    fun springBootOcrService(
        ocrEngine: OcrEngine,
        properties: ExampleOcrProperties,
    ): SpringBootOcrService =
        SpringBootOcrService(
            ocrEngine = ocrEngine,
            tessdataPath = properties.tessdataPath,
        )
}

/**
 * Exposes OCR text extraction for multipart image uploads.
 */
@RestController
@RequestMapping("/api/ocr")
class OcrApiController(
    private val ocrService: SpringBootOcrService,
) {

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun recognize(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("languages", defaultValue = "eng") languages: String,
    ): ResponseEntity<OcrTextResponse> =
        ResponseEntity.ok(ocrService.recognize(file, languages))

    @ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(e: IllegalArgumentException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.badRequest().body(
            ApiErrorResponse(
                error = "bad_request",
                message = e.message ?: "Invalid OCR request.",
            )
        )

    @ExceptionHandler(OcrException::class)
    fun ocrUnavailable(e: OcrException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
            ApiErrorResponse(
                error = "ocr_unavailable",
                message = e.message ?: "OCR runtime is unavailable.",
            )
        )
}

/**
 * Converts multipart image uploads into `images-ocr` recognition calls.
 */
class SpringBootOcrService(
    private val ocrEngine: OcrEngine,
    private val tessdataPath: String?,
) {

    suspend fun recognize(file: MultipartFile, languages: String): OcrTextResponse {
        val contentType = file.contentType?.lowercase().orEmpty()
        contentType.requireNotBlank("contentType")
        require(contentType in ALLOWED_CONTENT_TYPES) {
            "Unsupported image content type: $contentType"
        }
        require(!file.isEmpty) {
            "file must not be empty"
        }

        val parsedLanguages = parseLanguages(languages)
        val uploadBytes = withContext(Dispatchers.IO) { file.bytes }
        val text = immutableImageOf(uploadBytes).suspendExtractText(
            options = OcrOptions(
                languages = parsedLanguages,
                tessdataPath = tessdataPath?.takeIf { it.isNotBlank() },
            ),
            engine = ocrEngine,
        )

        return OcrTextResponse(
            text = text,
            languages = parsedLanguages,
            characterCount = text.length,
        )
    }

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

    private companion object {
        private val LANGUAGE_SEPARATOR = Regex("[,+]")

        private val ALLOWED_CONTENT_TYPES = setOf(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            "image/webp",
            "image/gif",
        )
    }
}

/**
 * OCR result returned by the quickstart API.
 */
data class OcrTextResponse(
    val text: String,
    val languages: List<String>,
    val characterCount: Int,
) : Serializable {

    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Error response for invalid OCR quickstart requests.
 */
data class ApiErrorResponse(
    val error: String,
    val message: String,
) : Serializable {

    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}
