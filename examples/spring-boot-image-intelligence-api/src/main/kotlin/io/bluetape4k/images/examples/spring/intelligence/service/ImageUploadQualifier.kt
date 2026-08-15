package io.bluetape4k.images.examples.spring.intelligence.service

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.images.ImageDimensionProbeResult
import io.bluetape4k.images.ImageDimensions
import io.bluetape4k.images.analysis.ImageMetadataReadOutcome
import io.bluetape4k.images.analysis.ImageMetadataReadOptions
import io.bluetape4k.images.analysis.readImageMetadataReportDetailed
import io.bluetape4k.images.examples.spring.intelligence.config.ImageIntelligenceProperties
import io.bluetape4k.images.immutableImageOf
import io.bluetape4k.images.probeImageDimensionsDetailed
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.warn
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

/**
 * 신뢰하는 image probe adapter가 입력 형식 오류로 분류한 실패입니다.
 *
 * [runProbe]는 이 명시적인 분류만 undecodable fallback으로 바꾸며, 임의의
 * `IIOException`/`IllegalArgumentException`은 내부 실패로 보존합니다.
 */
internal class MalformedImageProbeException(
    cause: Throwable? = null,
) : RuntimeException("The image probe rejected the encoded input.", cause)

private fun probeImageDimensionsForUpload(bytes: ByteArray): ImageDimensions? =
    when (val result = probeImageDimensionsDetailed(bytes)) {
        is ImageDimensionProbeResult.Success -> result.dimensions
        ImageDimensionProbeResult.Unavailable -> null
        is ImageDimensionProbeResult.Malformed -> throw MalformedImageProbeException(result.cause)
        is ImageDimensionProbeResult.Failure -> throw result.cause
    }

/**
 * 이미지 헤더 probe 중 입력 오류가 아닌 내부 실패를 나타냅니다.
 *
 * 원인은 운영 로그와 예외 cause에만 보존하고, HTTP 응답에는 고정된 reason code와
 * 정제된 detail만 노출합니다.
 */
internal class ImageProbeFailureException(
    cause: Throwable,
) : InvalidImageUploadException(
    reasonCode = "image_probe_failed",
    message = "The uploaded image could not be inspected.",
    cause = cause,
)

internal class ImageUploadQualifier(
    private val properties: ImageIntelligenceProperties,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val cpuDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val dimensionProbe: (ByteArray) -> ImageDimensions? = ::probeImageDimensionsForUpload,
    // 상세 metadata 결과로 parser 실패와 내부 실패를 구분합니다.
    private val metadataDimensionProbe: (ByteArray, Int) -> ImageDimensions? = { bytes, maxBytes ->
        when (
            val result = readImageMetadataReportDetailed(
                bytes,
                ImageMetadataReadOptions(maxBytes = maxBytes),
            )
        ) {
            is ImageMetadataReadOutcome.Success -> result.report.dimensions
            is ImageMetadataReadOutcome.Malformed -> throw MalformedImageProbeException(result.cause)
            is ImageMetadataReadOutcome.Failure -> throw result.cause
        }
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

            val dimensions = runProbe("dimension", dimensionProbe, bytes)
                ?: runProbe("metadata", { input ->
                    metadataDimensionProbe(input, properties.maxInputBytes.toInt())
                }, bytes)
                ?: throw invalidUpload(
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

    private fun runProbe(
        stage: String,
        probe: (ByteArray) -> ImageDimensions?,
        bytes: ByteArray,
    ): ImageDimensions? =
        try {
            probe(bytes)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: MalformedImageProbeException) {
            null
        } catch (exception: Exception) {
            log.warn(exception) {
                "Image upload probe failed. stage=$stage reason=image_probe_failed"
            }
            throw ImageProbeFailureException(exception)
        }

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

    private companion object : KLogging() {
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
