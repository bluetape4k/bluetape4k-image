package io.bluetape4k.images

import io.bluetape4k.support.requirePositiveNumber
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayInputStream
import java.io.Serializable
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.imageio.IIOException
import javax.imageio.stream.ImageInputStream

/**
 * 첫 번째 이미지 프레임의 헤더에서 읽은 크기입니다.
 *
 * 이 크기 정보는 비용이 큰 전체 이미지 디코딩, 썸네일 생성, OCR, native 처리 전에
 * 입력을 검증하기 위한 것입니다.
 *
 * 예:
 * ```kotlin
 * val dimensions = probeImageDimensions(uploadBytes)
 * dimensions?.requireMaxPixels(16_777_216)
 * dimensions?.requireMaxSide(8_192)
 * ```
 */
data class ImageDimensions(
    val width: Int,
    val height: Int,
) : Serializable {

    init {
        width.requirePositiveNumber("width")
        height.requirePositiveNumber("height")
    }

    /**
     * 첫 번째 프레임의 전체 픽셀 수입니다. 계산식은 `width * height`입니다.
     */
    val pixelCount: Long
        get() = width.toLong() * height.toLong()

    /**
     * 이 이미지가 설정된 디코딩 픽셀 예산을 초과하면 실패합니다.
     */
    fun requireMaxPixels(maxPixels: Long, subject: String = "image"): ImageDimensions {
        maxPixels.requirePositiveNumber("maxPixels")
        require(pixelCount <= maxPixels) {
            "$subject decodedPixels=$pixelCount exceeds maxInputPixels=$maxPixels (dimensions=${width}x$height)."
        }
        return this
    }

    /**
     * 디코딩된 너비나 높이 중 하나라도 설정된 한 변 예산을 초과하면 실패합니다.
     */
    fun requireMaxSide(maxSide: Int, subject: String = "image"): ImageDimensions {
        maxSide.requirePositiveNumber("maxSide")
        require(width <= maxSide && height <= maxSide) {
            "$subject decodedDimensions=${width}x$height exceeds maxInputSide=$maxSide."
        }
        return this
    }

    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * 인코딩 이미지 바이트의 헤더 probe 결과입니다.
 *
 * `Unavailable`은 ImageIO reader가 없다는 뜻이고, `Malformed`는 reader가 입력을
 * 형식 오류로 거부했다는 뜻입니다. 그 밖의 예외는 [Failure]로 유지해 내부 parser
 * 또는 runtime 실패가 잘못된 400 응답으로 축약되지 않도록 합니다. [Failure.cause]와
 * [Malformed.cause]는 로그·진단용이며 외부 응답에 직접 노출하지 않아야 합니다.
 */
sealed interface ImageDimensionProbeResult {
    /** 헤더에서 유효한 크기를 읽은 결과입니다. */
    data class Success(val dimensions: ImageDimensions) : ImageDimensionProbeResult

    /** 지원되는 ImageIO reader를 찾지 못한 결과입니다. */
    data object Unavailable : ImageDimensionProbeResult

    /** reader가 인코딩 입력을 형식 오류로 거부한 결과입니다. */
    data class Malformed(val cause: Throwable) : ImageDimensionProbeResult

    /** 입력 형식 외의 내부 probe 실패입니다. */
    data class Failure(val cause: Throwable) : ImageDimensionProbeResult
}

/**
 * 픽셀을 디코딩하지 않고 인코딩 이미지 바이트에서 첫 번째 프레임 크기를 읽습니다.
 *
 * ImageIO reader로 이미지 헤더만 탐색하므로 전체 픽셀 버퍼를 할당하지 않습니다.
 * 신뢰하지 않는 업로드 입력의 조기 안전성 검사에 사용합니다.
 *
 * ImageIO가 payload에 맞는 reader를 찾지 못하면 `null`을 반환합니다.
 */
fun probeImageDimensions(bytes: ByteArray): ImageDimensions? {
    val input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)) ?: return null
    return input.use(::probeImageDimensions)
}

/**
 * 인코딩 이미지 바이트를 헤더만 읽어 명시적인 결과로 분류합니다.
 *
 * 바이트 배열은 caller-owned I/O가 없는 입력이므로 ImageIO reader가 발생시킨
 * [IIOException]은 인코딩 parser의 입력 거부([ImageDimensionProbeResult.Malformed])로
 * 분류합니다. 취소는 그대로 재전파하고, 그 밖의 예외는 원인을 보존한
 * [ImageDimensionProbeResult.Failure]로 반환합니다.
 */
fun probeImageDimensionsDetailed(bytes: ByteArray): ImageDimensionProbeResult =
    try {
        probeImageDimensions(bytes)?.let(ImageDimensionProbeResult::Success)
            ?: ImageDimensionProbeResult.Unavailable
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: IIOException) {
        ImageDimensionProbeResult.Malformed(exception)
    } catch (exception: Exception) {
        ImageDimensionProbeResult.Failure(exception)
    }

/**
 * 픽셀을 디코딩하지 않고 이미지 경로에서 첫 번째 프레임 크기를 읽습니다.
 *
 * ImageIO reader로 이미지 헤더만 탐색하므로 전체 픽셀 버퍼를 할당하지 않습니다.
 * 배치 입력의 조기 안전성 검사에 사용합니다.
 *
 * ImageIO가 파일에 맞는 reader를 찾지 못하면 `null`을 반환합니다.
 */
fun probeImageDimensions(path: Path): ImageDimensions? {
    val input = ImageIO.createImageInputStream(path.toFile()) ?: return null
    return input.use(::probeImageDimensions)
}

/**
 * 인코딩 이미지 바이트에서 첫 번째 프레임의 픽셀 수를 읽습니다.
 */
fun probeImagePixelCount(bytes: ByteArray): Long? =
    probeImageDimensions(bytes)?.pixelCount

/**
 * 이미지 경로에서 첫 번째 프레임의 픽셀 수를 읽습니다.
 */
fun probeImagePixelCount(path: Path): Long? =
    probeImageDimensions(path)?.pixelCount

private fun probeImageDimensions(input: ImageInputStream): ImageDimensions? {
    val readers = ImageIO.getImageReaders(input)
    if (!readers.hasNext()) {
        return null
    }

    val reader = readers.next()
    try {
        input.seek(0)
        reader.input = input
        return ImageDimensions(
            width = reader.getWidth(0),
            height = reader.getHeight(0),
        )
    } finally {
        reader.dispose()
    }
}
