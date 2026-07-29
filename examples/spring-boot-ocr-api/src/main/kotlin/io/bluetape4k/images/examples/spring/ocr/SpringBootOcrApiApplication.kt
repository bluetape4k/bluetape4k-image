package io.bluetape4k.images.examples.spring.ocr

import io.bluetape4k.images.immutableImageOf
import io.bluetape4k.images.ocr.OcrEngine
import io.bluetape4k.images.ocr.OcrException
import io.bluetape4k.images.ocr.OcrOptions
import io.bluetape4k.images.ocr.TesseractOcrEngine
import io.bluetape4k.images.ocr.suspendExtractText
import io.bluetape4k.images.probeImageDimensions
import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requirePositiveNumber
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
 * OCR extraction을 HTTP로 노출하는 Spring Boot quickstart application입니다.
 */
@SpringBootApplication
class SpringBootOcrApiApplication

/**
 * host-specific OCR runtime setting을 담는 example configuration입니다.
 */
@ConfigurationProperties(prefix = "example.ocr")
data class ExampleOcrProperties(
    val maxInputBytes: Long = 10L * 1024L * 1024L,
    val maxInputPixels: Long = 16_777_216L,
    val maxInputSide: Int = 8_192,
    val tessdataPath: String? = null,
) : Serializable {

    init {
        maxInputBytes.requirePositiveNumber("maxInputBytes")
        maxInputPixels.requirePositiveNumber("maxInputPixels")
        maxInputSide.requirePositiveNumber("maxInputSide")
    }

    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * quickstart OCR service와 기본 Tess4J-backed engine을 연결합니다.
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
            properties = properties,
        )
}

/**
 * multipart image upload에 대한 OCR text extraction을 노출합니다.
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
 * multipart image upload를 `images-ocr` recognition call로 변환합니다.
 */
class SpringBootOcrService(
    private val ocrEngine: OcrEngine,
    private val properties: ExampleOcrProperties,
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
        require(uploadBytes.size <= properties.maxInputBytes) {
            "OCR upload exceeds maxInputBytes=${properties.maxInputBytes}."
        }
        probeImageDimensions(uploadBytes)
            ?.requireMaxPixels(properties.maxInputPixels, "OCR upload")
            ?.requireMaxSide(properties.maxInputSide, "OCR upload")

        val text = immutableImageOf(uploadBytes).suspendExtractText(
            options = OcrOptions(
                languages = parsedLanguages,
                tessdataPath = properties.tessdataPath?.takeIf { it.isNotBlank() },
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
 * quickstart API가 반환하는 OCR result입니다.
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
 * invalid OCR quickstart request에 대한 error response입니다.
 */
data class ApiErrorResponse(
    val error: String,
    val message: String,
) : Serializable {

    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}
