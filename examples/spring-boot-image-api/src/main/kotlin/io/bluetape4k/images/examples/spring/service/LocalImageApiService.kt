package io.bluetape4k.images.examples.spring.service

import com.sksamuel.scrimage.nio.PngWriter
import io.bluetape4k.codec.Base58
import io.bluetape4k.images.examples.spring.config.ImageApiProperties
import io.bluetape4k.images.examples.spring.config.toDecodeLimits
import io.bluetape4k.images.examples.spring.model.ImageUploadResponse
import io.bluetape4k.images.examples.spring.model.StoredImageResponse
import io.bluetape4k.images.examples.spring.model.extensionForContentType
import io.bluetape4k.images.immutableExternalImageOf
import io.bluetape4k.images.spring.ImageObjectKey
import io.bluetape4k.images.spring.UploadOptions
import io.bluetape4k.images.spring.storage.ImageStorage
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.support.requireInRange
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.MediaType
import org.springframework.web.multipart.MultipartFile

/**
 * original upload와 generated thumbnail을 [ImageStorage]에 저장합니다.
 */
class LocalImageApiService(
    private val storage: ImageStorage,
    private val properties: ImageApiProperties,
) {

    companion object: KLoggingChannel()

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
        val image = immutableExternalImageOf(
            uploadBytes,
            properties.toDecodeLimits(),
        )
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
