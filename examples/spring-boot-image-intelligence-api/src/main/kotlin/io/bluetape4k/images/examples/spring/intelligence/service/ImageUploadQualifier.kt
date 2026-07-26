package io.bluetape4k.images.examples.spring.intelligence.service

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.images.ImageDimensions
import io.bluetape4k.images.analysis.ImageMetadataReadOptions
import io.bluetape4k.images.analysis.readImageMetadataReport
import io.bluetape4k.images.examples.spring.intelligence.config.ImageIntelligenceProperties
import io.bluetape4k.images.immutableImageOf
import io.bluetape4k.images.probeImageDimensions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.MediaType
import org.springframework.web.multipart.MultipartFile
import java.util.Locale

internal val ALLOWED_IMAGE_CONTENT_TYPES: Set<String> = setOf(
    MediaType.IMAGE_PNG_VALUE,
    MediaType.IMAGE_JPEG_VALUE,
    "image/webp",
)

internal class QualifiedImage(
    val mediaType: String,
    val dimensions: ImageDimensions,
    val image: ImmutableImage,
)

internal open class InvalidImageUploadException(
    val reasonCode: String,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

internal class ImagePayloadTooLargeException(
    reasonCode: String,
    message: String,
) : InvalidImageUploadException(reasonCode, message)

internal class ImageUploadQualifier(
    private val properties: ImageIntelligenceProperties,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val cpuDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val dimensionProbe: (ByteArray) -> ImageDimensions? = ::probeImageDimensions,
    private val metadataDimensionProbe: (ByteArray, Int) -> ImageDimensions? = { bytes, maxBytes ->
        readImageMetadataReport(
            bytes,
            ImageMetadataReadOptions(maxBytes = maxBytes),
        ).dimensions
    },
    private val imageDecoder: (ByteArray) -> ImmutableImage = ::immutableImageOf,
) {

    suspend fun qualify(file: MultipartFile): QualifiedImage {
        if (file.isEmpty) {
            throw invalidUpload(
                reasonCode = "empty_input",
                message = "The uploaded file is empty.",
            )
        }

        val declaredMediaType = file.contentType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf(ALLOWED_IMAGE_CONTENT_TYPES::contains)
            ?: throw invalidUpload(
                reasonCode = "unsupported_media_type",
                message = "The uploaded content type is not supported.",
            )

        requireEncodedSize(file.size)

        val bytes = try {
            withContext(ioDispatcher) { file.bytes }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            throw invalidUpload(
                reasonCode = "image_read_failed",
                message = "The uploaded file could not be read.",
                cause = exception,
            )
        }
        requireEncodedSize(bytes.size.toLong())

        return withContext(cpuDispatcher) {
            val detectedMediaType = detectMediaType(bytes)
                ?: throw invalidUpload(
                    reasonCode = "unsupported_image_format",
                    message = "The uploaded image format is not supported.",
                )
            if (declaredMediaType != detectedMediaType) {
                throw invalidUpload(
                    reasonCode = "media_type_mismatch",
                    message = "The uploaded content type does not match the image data.",
                )
            }

            val dimensions = try {
                dimensionProbe(bytes)
                    ?: metadataDimensionProbe(bytes, properties.maxInputBytes.toInt())
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                null
            } ?: throw invalidUpload(
                reasonCode = "image_not_decodable",
                message = "The uploaded file is not a decodable image.",
            )
            requireDecodedSize(dimensions)

            val image = try {
                imageDecoder(bytes)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                throw invalidUpload(
                    reasonCode = "image_not_decodable",
                    message = "The uploaded file is not a decodable image.",
                    cause = exception,
                )
            }

            QualifiedImage(
                mediaType = detectedMediaType,
                dimensions = dimensions,
                image = image,
            )
        }
    }

    private fun requireEncodedSize(size: Long) {
        if (size > properties.maxInputBytes) {
            throw ImagePayloadTooLargeException(
                reasonCode = "payload_too_large",
                message = "The uploaded file exceeds the configured size limit.",
            )
        }
    }

    private fun requireDecodedSize(dimensions: ImageDimensions) {
        if (dimensions.width > properties.maxInputSide || dimensions.height > properties.maxInputSide) {
            throw ImagePayloadTooLargeException(
                reasonCode = "payload_too_large",
                message = "The decoded image exceeds the configured side limit.",
            )
        }
        if (dimensions.pixelCount > properties.maxInputPixels) {
            throw ImagePayloadTooLargeException(
                reasonCode = "payload_too_large",
                message = "The decoded image exceeds the configured pixel limit.",
            )
        }
    }

    private fun invalidUpload(
        reasonCode: String,
        message: String,
        cause: Throwable? = null,
    ): InvalidImageUploadException =
        InvalidImageUploadException(reasonCode, message, cause)

    private fun detectMediaType(bytes: ByteArray): String? =
        when {
            bytes.hasPrefix(PNG_SIGNATURE) -> MediaType.IMAGE_PNG_VALUE
            bytes.hasPrefix(JPEG_SIGNATURE) -> MediaType.IMAGE_JPEG_VALUE
            bytes.isWebp() -> "image/webp"
            else -> null
        }

    private fun ByteArray.hasPrefix(prefix: IntArray): Boolean =
        size >= prefix.size &&
            prefix.indices.all { index -> this[index].toInt() and 0xFF == prefix[index] }

    private fun ByteArray.isWebp(): Boolean =
        size >= WEBP_HEADER_SIZE &&
            copyOfRange(0, 4).contentEquals(RIFF_SIGNATURE) &&
            copyOfRange(8, 12).contentEquals(WEBP_SIGNATURE)

    private companion object {
        private val PNG_SIGNATURE: IntArray = intArrayOf(
            0x89,
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
        )
        private val JPEG_SIGNATURE: IntArray = intArrayOf(0xFF, 0xD8, 0xFF)
        private val RIFF_SIGNATURE: ByteArray = byteArrayOf(0x52, 0x49, 0x46, 0x46)
        private val WEBP_SIGNATURE: ByteArray = byteArrayOf(0x57, 0x45, 0x42, 0x50)
        private const val WEBP_HEADER_SIZE: Int = 12
    }
}
