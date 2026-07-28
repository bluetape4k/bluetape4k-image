package io.bluetape4k.images.examples.spring

import com.sksamuel.scrimage.nio.PngWriter
import io.bluetape4k.codec.Base58
import io.bluetape4k.images.immutableImageOf
import io.bluetape4k.images.probeImageDimensions
import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.UploadOptions
import io.bluetape4k.images.spring.storage.ImageStorage
import io.bluetape4k.support.requireInRange
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
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
import java.io.Serializable

fun main(args: Array<String>) {
    runApplication<SpringBootImageApiApplication>(*args)
}

/**
 * local-storage image API용 Spring Boot quickstart application입니다.
 */
@SpringBootApplication
class SpringBootImageApiApplication

/**
 * quickstart image API의 upload 및 local download endpoint를 노출합니다.
 */
@RestController
@RequestMapping("/api/images")
class ImageApiController(
    private val imageService: LocalImageApiService,
) {

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun upload(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("maxSide", defaultValue = "320") maxSide: Int,
    ): ResponseEntity<ImageUploadResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(imageService.upload(file, maxSide))

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

/**
 * original upload와 generated thumbnail을 [ImageStorage]에 저장합니다.
 */
class LocalImageApiService(
    private val storage: ImageStorage,
    private val properties: ImageApiProperties,
) {

    suspend fun upload(file: MultipartFile, maxSide: Int): ImageUploadResponse {
        maxSide.requireInRange(64, 2048, "maxSide")
        val contentType = file.contentType?.lowercase().orEmpty()
        contentType.requireNotBlank("contentType")
        require(contentType in UploadOptions.ALLOWED_CONTENT_TYPES) {
            "Unsupported image content type: $contentType"
        }
        require(!file.isEmpty) {
            "file must not be empty"
        }

        val uploadBytes = withContext(Dispatchers.IO) { file.bytes }
        require(uploadBytes.size <= properties.maxInputBytes) {
            "Image upload exceeds maxInputBytes=${properties.maxInputBytes}."
        }
        probeImageDimensions(uploadBytes)
            ?.requireMaxPixels(properties.maxInputPixels, "Image upload")
            ?.requireMaxSide(properties.maxInputSide, "Image upload")

        val image = immutableImageOf(uploadBytes)
        val thumbnailBytes = withContext(Dispatchers.Default) {
            image.fit(maxSide, maxSide)
                .forWriter(PngWriter.MaxCompression)
                .bytes()
        }

        val id = Base58.randomString(12)
        val originalKey = ImageObjectKey.of("originals", "$id.${extensionForContentType(contentType)}")
        val thumbnailKey = ImageObjectKey.of("thumbnails", "$id.png")

        val original = storage.upload(
            key = originalKey,
            bytes = uploadBytes,
            options = UploadOptions(contentType = contentType),
        )
        val thumbnail = storage.upload(
            key = thumbnailKey,
            bytes = thumbnailBytes,
            options = UploadOptions(contentType = MediaType.IMAGE_PNG_VALUE),
        )

        return ImageUploadResponse(
            original = StoredImageResponse.from(original.key),
            thumbnail = StoredImageResponse.from(thumbnail.key),
            originalBytes = original.sizeBytes,
            thumbnailBytes = thumbnail.sizeBytes,
        )
    }

    suspend fun download(key: ImageObjectKey): ByteArray =
        storage.download(key)
}

/**
 * local object key와 read URL을 포함한 upload result입니다.
 */
data class ImageUploadResponse(
    val original: StoredImageResponse,
    val thumbnail: StoredImageResponse,
    val originalBytes: Long,
    val thumbnailBytes: Long,
) : Serializable {

    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * quickstart API가 반환하는 local storage object reference입니다.
 */
data class StoredImageResponse(
    val key: String,
    val url: String,
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L

        fun from(key: ImageObjectKey): StoredImageResponse =
            StoredImageResponse(
                key = key.fullKey,
                url = "/api/images/${key.fullKey}",
            )
    }
}

/**
 * invalid quickstart API request에 대한 error response입니다.
 */
data class ApiErrorResponse(
    val error: String,
    val message: String,
) : Serializable {

    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}

private fun extensionForContentType(contentType: String): String =
    when (contentType) {
        MediaType.IMAGE_JPEG_VALUE -> "jpg"
        MediaType.IMAGE_PNG_VALUE -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/avif" -> "avif"
        "image/heic" -> "heic"
        else -> throw IllegalArgumentException("Unsupported image content type: $contentType")
    }

private fun contentTypeForName(name: String): String =
    when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> MediaType.IMAGE_JPEG_VALUE
        "png" -> MediaType.IMAGE_PNG_VALUE
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "avif" -> "image/avif"
        "heic" -> "image/heic"
        else -> MediaType.APPLICATION_OCTET_STREAM_VALUE
    }
