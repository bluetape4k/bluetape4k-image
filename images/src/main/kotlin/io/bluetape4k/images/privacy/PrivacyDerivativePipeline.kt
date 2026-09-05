package io.bluetape4k.images.privacy

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.coroutines.flow.extensions.mapParallel
import io.bluetape4k.images.ImageDimensions
import io.bluetape4k.images.analysis.ExifData
import io.bluetape4k.images.analysis.ImageMetadataReadOptions
import io.bluetape4k.images.analysis.ImageMetadataReadResult
import io.bluetape4k.images.analysis.ImageMetadataReport
import io.bluetape4k.images.analysis.readImageMetadataReportStrict
import io.bluetape4k.images.batch.DEFAULT_MAX_PIXELS
import io.bluetape4k.images.batch.ImageProcessingOptions
import io.bluetape4k.images.batch.PixelPermitLimiter
import io.bluetape4k.images.coroutines.SuspendImageWriter
import io.bluetape4k.images.coroutines.SuspendJpegWriter
import io.bluetape4k.images.coroutines.SuspendPngWriter
import io.bluetape4k.images.immutableImageOf
import io.bluetape4k.images.moderation.SensitiveCoordinateSpace
import io.bluetape4k.images.moderation.SensitiveRegion
import io.bluetape4k.images.moderation.SensitiveRegionGeometry
import io.bluetape4k.images.probeImageDimensions
import io.bluetape4k.images.suspendBytes
import io.bluetape4k.images.thumbnail.ThumbnailCrop
import io.bluetape4k.images.thumbnail.ThumbnailSize
import io.bluetape4k.images.transforms.flipHorizontal
import io.bluetape4k.images.transforms.flipVertical
import io.bluetape4k.images.transforms.rotateDegrees
import io.bluetape4k.images.transforms.smartCropToWithBounds
import io.bluetape4k.images.withGraphics
import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requirePositiveNumber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.awt.AlphaComposite
import java.awt.Color
import java.nio.file.Path
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.TimeSource

/**
 * public-safe derivative 이미지 크기에 대한 alias입니다.
 */
typealias PrivacyImageDimensions = ImageDimensions

/**
 * privacy derivative 생성 실패 시 사용하는 처리 stage입니다.
 */
enum class PrivacyDerivativeFailureStage {
    VALIDATION,
    LOAD,
    TRANSFORM,
    WRITE,
    VERIFY,
}

/**
 * public derivative에 의도적으로 복사하지 않는 metadata category입니다.
 */
enum class PrivacyMetadataCategory {
    GPS,
    EXIF,
    ORIENTATION,
    XMP,
    IPTC,
    ICC,
}

/**
 * public-safe derivative를 만드는 동안 적용된 action입니다.
 */
enum class PrivacyDerivativeAction {
    GPS_REMOVED,
    METADATA_STRIPPED,
    ORIENTATION_NORMALIZED,
    RESIZED,
    REDACTED,
    ENCODED,
}

/**
 * 위치 지정 privacy treatment를 위한 redaction rendering mode입니다.
 */
enum class PrivacyRedactionMode {
    /** sensitive region 위에 불투명 또는 반투명 rectangle을 그립니다. */
    SOLID_MASK,
}

/**
 * privacy derivative의 인코딩 출력 format입니다.
 *
 * ## 동작/계약
 * - [writer]는 derivative를 다시 인코딩하는 coroutine-aware encoder입니다.
 * - re-encoding 뒤 bounded metadata reader가 output을 다시 검사하며, 요청된 metadata가
 *   남거나 reader가 실패하면 derivative를 성공으로 반환하지 않습니다.
 * - [extension]은 caller-side naming을 위해 정규화됩니다. 이 class는 storage path를
 *   선택하지 않습니다.
 */
data class PrivacyDerivativeFormat(
    val writer: SuspendImageWriter,
    val extension: String,
) {

    val normalizedExtension: String = extension.trim().removePrefix(".").lowercase()

    init {
        normalizedExtension.requireNotBlank("extension")
        require(!normalizedExtension.contains(PATH_SEPARATOR)) { "extension must not contain a path separator." }
        require(!normalizedExtension.contains(WINDOWS_PATH_SEPARATOR)) { "extension must not contain a path separator." }
    }

    companion object {
        private const val PATH_SEPARATOR = '/'
        private const val WINDOWS_PATH_SEPARATOR = '\\'

        /** 대부분의 public thumbnail과 preview에 적합한 JPEG 출력입니다. */
        val Jpeg: PrivacyDerivativeFormat = PrivacyDerivativeFormat(SuspendJpegWriter.Default, "jpg")

        /** lossless public derivative를 위한 PNG 출력입니다. */
        val Png: PrivacyDerivativeFormat = PrivacyDerivativeFormat(SuspendPngWriter.MaxCompression, "png")
    }
}

/**
 * public derivative를 인코딩하기 전에 적용할 위치 지정 privacy redaction입니다.
 *
 * ## 동작/계약
 * - [region]은 sensitive-content moderation과 동일한 geometry model을 사용합니다.
 * - 이 core pipeline은 rectangle geometry만 render합니다. 다른 geometry는 이 함수를
 *   호출하기 전에 detector adapter에서 rasterize해야 합니다.
 * - [maskOpacity]는 validation으로 `0.0..1.0` inclusive 범위에 묶입니다.
 */
data class PrivacyRedaction(
    val region: SensitiveRegion,
    val mode: PrivacyRedactionMode = PrivacyRedactionMode.SOLID_MASK,
    val maskColorArgb: Int = Color.BLACK.rgb,
    val maskOpacity: Double = 1.0,
) {

    init {
        require(maskOpacity.isFinite()) { "maskOpacity must be finite, but was $maskOpacity" }
        require(maskOpacity in OPACITY_MIN..OPACITY_MAX) { "maskOpacity must be in 0.0..1.0, but was $maskOpacity" }
    }
}

/**
 * privacy-safe derivative image를 만들기 위한 option입니다.
 *
 * ## 동작/계약
 * - 비용이 큰 transform 전에 source 크기를 검증합니다.
 * - metadata 및 GPS 제거는 report 가능한 policy decision이며, output 재검증 결과만
 *   `strippedMetadataCategories`에 기록합니다.
 * - [thumbnailSize]는 기존 thumbnail model을 사용해 public preview 크기 정책을
 *   thumbnail pipeline과 일관되게 유지합니다.
 */
data class PrivacyDerivativeOptions(
    val stripMetadata: Boolean = true,
    val removeGps: Boolean = true,
    val normalizeOrientation: Boolean = true,
    val maxPixels: Long = DEFAULT_MAX_PIXELS,
    val maxSide: Int? = null,
    val thumbnailSize: ThumbnailSize? = null,
    val thumbnailCrop: ThumbnailCrop = ThumbnailCrop.Fit,
    val outputFormat: PrivacyDerivativeFormat = PrivacyDerivativeFormat.Jpeg,
    val redactions: List<PrivacyRedaction> = emptyList(),
) {

    init {
        maxPixels.requirePositiveNumber("maxPixels")
        maxSide?.requirePositiveNumber("maxSide")
    }
}

/**
 * derivative report 또는 batch result에 기록되는 failure item입니다.
 */
data class PrivacyDerivativeFailure(
    val stage: PrivacyDerivativeFailureStage,
    val message: String,
) {

    init {
        message.requireNotBlank("message")
    }
}

/**
 * audit log와 client diagnostic을 위한 적용 redaction 요약입니다.
 */
data class AppliedPrivacyRedaction(
    val regionId: String?,
    val mode: PrivacyRedactionMode,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {

    init {
        x.requireNonNegative("x")
        y.requireNonNegative("y")
        width.requirePositiveNumber("width")
        height.requirePositiveNumber("height")
    }
}

/**
 * 단일 public-safe derivative에 대한 audit report입니다.
 */
data class PrivacyDerivativeReport(
    val source: String?,
    val sourceDimensions: PrivacyImageDimensions,
    val outputDimensions: PrivacyImageDimensions,
    val strippedMetadataCategories: Set<PrivacyMetadataCategory>,
    val appliedActions: List<PrivacyDerivativeAction>,
    val redactions: List<AppliedPrivacyRedaction>,
    val failures: List<PrivacyDerivativeFailure>,
    val elapsedMillis: Long,
    val metadataVerification: PrivacyMetadataVerification = PrivacyMetadataVerification(),
) {

    init {
        source.requireNotBlankIfPresent("source")
        elapsedMillis.requireNonNegative("elapsedMillis")
    }
}

/**
 * derivative output metadata에 대한 요청·원본·잔존·검증 결과입니다.
 *
 * raw metadata payload나 parser 예외는 포함하지 않습니다. [verified]가 `true`인 경우에만
 * 요청된 category가 output에 남아 있지 않다는 뜻입니다.
 */
data class PrivacyMetadataVerification(
    val requested: Set<PrivacyMetadataCategory> = emptySet(),
    val sourcePresent: Set<PrivacyMetadataCategory> = emptySet(),
    val remaining: Set<PrivacyMetadataCategory> = emptySet(),
    val verified: Boolean = true,
)

/**
 * derivative output metadata를 strict하게 검증할 수 없거나, 요청된 category가 남은 경우의
 * fail-closed 예외입니다.
 *
 * parser의 원시 오류와 output byte는 노출하지 않고 제한된 잔존 category만 제공합니다.
 */
class PrivacyDerivativeVerificationException(
    val remainingCategories: Set<PrivacyMetadataCategory> = emptySet(),
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/**
 * 성공한 privacy derivative payload입니다.
 */
data class PrivacyDerivativeResult(
    val image: ImmutableImage,
    val bytes: ByteArray,
    val report: PrivacyDerivativeReport,
)

/**
 * [processPrivacyDerivatives]의 batch result입니다.
 */
sealed interface PrivacyDerivativeBatchResult {
    /** 이 result를 생성한 source path입니다. */
    val source: Path

    /** 성공적으로 생성된 derivative입니다. */
    data class Success(
        override val source: Path,
        val result: PrivacyDerivativeResult,
    ) : PrivacyDerivativeBatchResult

    /** derivative 생성 실패 item입니다. */
    data class Failure(
        override val source: Path,
        val stage: PrivacyDerivativeFailureStage,
        val cause: Throwable,
    ) : PrivacyDerivativeBatchResult
}

/**
 * 이 이미지의 public-safe derivative를 만듭니다.
 *
 * ## 동작/계약
 * - source image는 mutate하지 않습니다.
 * - resize/redaction 작업 전에 source 크기를 검증합니다.
 * - 반환되는 byte는 [PrivacyDerivativeOptions.outputFormat]으로 새로 인코딩됩니다.
 * - 요청된 metadata category가 output에 남거나 output reader가 실패하면
 *   [PrivacyDerivativeVerificationException]을 던져 fail-closed 처리합니다.
 * - cancellation은 변경하지 않고 다시 던집니다.
 */
suspend fun ImmutableImage.suspendPrivacyDerivative(
    options: PrivacyDerivativeOptions = PrivacyDerivativeOptions(),
    sourceExif: ExifData = ExifData.EMPTY,
    source: Path? = null,
    sourceMetadata: ImageMetadataReport? = null,
): PrivacyDerivativeResult {
    val started = TimeSource.Monotonic.markNow()
    val sourceDimensions = PrivacyImageDimensions(width = width, height = height)
    sourceDimensions.requireWithin(options, source?.toString() ?: "image")

    try {
        val transformed = applyDerivativeTransforms(options, sourceDimensions, sourceExif.orientation)
        val bytes = transformed.image.suspendBytes(options.outputFormat.writer)
        val outputDimensions = PrivacyImageDimensions(width = transformed.image.width, height = transformed.image.height)
        val metadataVerification = verifyDerivativeMetadata(
            bytes = bytes,
            options = options,
            sourceExif = sourceExif,
            sourceMetadata = sourceMetadata,
        )
        val strippedMetadata = metadataVerification.verifiedRemovedCategories()
        val redactions = transformed.redactions
        val actions = buildList {
            if (PrivacyMetadataCategory.GPS in strippedMetadata) {
                add(PrivacyDerivativeAction.GPS_REMOVED)
            }
            if (strippedMetadata.any { it in METADATA_STRIPPED_CATEGORIES }) {
                add(PrivacyDerivativeAction.METADATA_STRIPPED)
            }
            if (PrivacyMetadataCategory.ORIENTATION in strippedMetadata) {
                add(PrivacyDerivativeAction.ORIENTATION_NORMALIZED)
            }
            if (options.thumbnailSize != null) {
                add(PrivacyDerivativeAction.RESIZED)
            }
            if (redactions.isNotEmpty()) {
                add(PrivacyDerivativeAction.REDACTED)
            }
            add(PrivacyDerivativeAction.ENCODED)
        }

        return PrivacyDerivativeResult(
            image = transformed.image,
            bytes = bytes,
            report = PrivacyDerivativeReport(
                source = source?.toString(),
                sourceDimensions = sourceDimensions,
                outputDimensions = outputDimensions,
                strippedMetadataCategories = strippedMetadata,
                appliedActions = actions,
                redactions = redactions,
                failures = emptyList(),
                elapsedMillis = started.elapsedNow().inWholeMilliseconds,
                metadataVerification = metadataVerification,
            ),
        )
    } catch (e: CancellationException) {
        throw e
    }
}

/**
 * [suspendPrivacyDerivative]와 같은 core transform logic으로 image path를
 * privacy-safe derivative로 처리합니다.
 *
 * ## 동작/계약
 * - ImageIO가 source format을 지원하면 전체 decode 전에 header 크기를 probe합니다.
 * - [ImageProcessingOptions.maxInFlightPixels]로 동시에 디코딩되는 작업량을 제한합니다.
 * - [ImageProcessingOptions.skipFailures]가 `true`이면 실패를
 *   [PrivacyDerivativeBatchResult.Failure]로 emit하고 [onFailure]로 전달합니다.
 */
fun Flow<Path>.processPrivacyDerivatives(
    privacyOptions: PrivacyDerivativeOptions = PrivacyDerivativeOptions(),
    processingOptions: ImageProcessingOptions = ImageProcessingOptions(),
    onFailure: suspend (PrivacyDerivativeBatchResult.Failure) -> Unit = {},
): Flow<PrivacyDerivativeBatchResult> {
    val limiter = PixelPermitLimiter(processingOptions.maxInFlightPixels)

    return mapParallel(processingOptions.parallelism) { source ->
        processOnePrivacyDerivative(source, privacyOptions, processingOptions, limiter, onFailure)
    }
}

private suspend fun processOnePrivacyDerivative(
    source: Path,
    privacyOptions: PrivacyDerivativeOptions,
    processingOptions: ImageProcessingOptions,
    limiter: PixelPermitLimiter,
    onFailure: suspend (PrivacyDerivativeBatchResult.Failure) -> Unit,
): PrivacyDerivativeBatchResult {
    try {
        val probedDimensions = runDerivativeStage(source, PrivacyDerivativeFailureStage.VALIDATION) {
            withContext(processingOptions.ioDispatcher) { probeImageDimensions(source) }
        }
        probedDimensions?.requireWithin(privacyOptions, source.toString())

        val permitPixels = probedDimensions?.pixelCount ?: privacyOptions.maxPixels
        return limiter.withPermit(permitPixels) {
            val image = runDerivativeStage(source, PrivacyDerivativeFailureStage.LOAD) {
                withContext(processingOptions.ioDispatcher) { immutableImageOf(source) }
            }
            val sourceMetadata = runDerivativeStage(source, PrivacyDerivativeFailureStage.LOAD) {
                withContext(processingOptions.ioDispatcher) {
                    when (val inspection = source.readImageMetadataReportStrict(
                        ImageMetadataReadOptions(stripSensitiveMetadata = false),
                    )) {
                        is ImageMetadataReadResult.Success -> inspection.report
                        is ImageMetadataReadResult.Failure ->
                            throw IllegalStateException(
                                "Privacy source metadata inspection failed: ${inspection.kind}.",
                            )
                    }
                }
            }
            val sourceExif = sourceMetadata.exif
            image.privacyDimensions().requireWithin(privacyOptions, source.toString())

            val result = runDerivativeStage(source, PrivacyDerivativeFailureStage.TRANSFORM) {
                try {
                    withContext(processingOptions.transformDispatcher) {
                        image.suspendPrivacyDerivative(
                            options = privacyOptions,
                            sourceExif = sourceExif,
                            source = source,
                            sourceMetadata = sourceMetadata,
                        )
                    }
                } catch (e: PrivacyDerivativeVerificationException) {
                    throw PrivacyDerivativeException(
                        source = source,
                        stage = PrivacyDerivativeFailureStage.VERIFY,
                        message = "Privacy derivative verification failed. source=$source",
                        cause = e,
                    )
                }
            }
            PrivacyDerivativeBatchResult.Success(source = source, result = result)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        val failure = PrivacyDerivativeBatchResult.Failure(
            source = source,
            stage = e.derivativeStage(),
            cause = e.cause ?: e,
        )
        if (!processingOptions.skipFailures) {
            throw e
        }
        onFailure(failure)
        return failure
    }
}

private suspend inline fun <T> runDerivativeStage(
    source: Path,
    stage: PrivacyDerivativeFailureStage,
    crossinline block: suspend () -> T,
): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: PrivacyDerivativeException) {
        throw e
    } catch (e: Throwable) {
        throw PrivacyDerivativeException(source, stage, "Privacy derivative stage failed. source=$source, stage=$stage", e)
    }

private class PrivacyDerivativeException(
    val source: Path,
    val stage: PrivacyDerivativeFailureStage,
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause)

private fun Throwable.derivativeStage(): PrivacyDerivativeFailureStage =
    (this as? PrivacyDerivativeException)?.stage ?: PrivacyDerivativeFailureStage.TRANSFORM

private fun verifyDerivativeMetadata(
    bytes: ByteArray,
    options: PrivacyDerivativeOptions,
    sourceExif: ExifData,
    sourceMetadata: ImageMetadataReport?,
): PrivacyMetadataVerification {
    val requested = options.requestedMetadataCategories()
    val sourcePresent = sourceMetadata?.privacyMetadataCategories()
        ?: sourceExif.privacyMetadataCategories()
    val outputReport = when (
        val inspection = readImageMetadataReportStrict(
            bytes,
            ImageMetadataReadOptions(stripSensitiveMetadata = false),
        )
    ) {
        is ImageMetadataReadResult.Success -> inspection.report
        is ImageMetadataReadResult.Failure ->
            throw PrivacyDerivativeVerificationException(
                message = "Privacy derivative metadata verification failed: output metadata could not be inspected.",
            )
    }
    val remaining = outputReport.privacyMetadataCategories()
    val unstripped = remaining.intersect(requested)
    if (unstripped.isNotEmpty()) {
        throw PrivacyDerivativeVerificationException(
            remainingCategories = unstripped,
            message = "Privacy derivative metadata verification failed: requested metadata remains in output ($unstripped).",
        )
    }

    return PrivacyMetadataVerification(
        requested = requested,
        sourcePresent = sourcePresent,
        remaining = remaining,
        verified = true,
    )
}

private fun ImmutableImage.applyDerivativeTransforms(
    options: PrivacyDerivativeOptions,
    sourceDimensions: PrivacyImageDimensions,
    orientation: Int?,
): PrivacyDerivativeTransformResult {
    val oriented = if (options.normalizeOrientation) {
        normalizeExifOrientation(orientation)
    } else {
        this.copy()
    }

    val transformed = when (val size = options.thumbnailSize) {
        null -> PrivacyDerivativeImageTransform(
            image = oriented,
            crop = CropWindow(x = 0.0, y = 0.0, width = oriented.width.toDouble(), height = oriented.height.toDouble()),
        )

        else -> when (val crop = options.thumbnailCrop) {
            ThumbnailCrop.Fit -> PrivacyDerivativeImageTransform(
                image = oriented.scaleTo(size.width, size.height),
                crop = CropWindow(x = 0.0, y = 0.0, width = oriented.width.toDouble(), height = oriented.height.toDouble()),
            )

            is ThumbnailCrop.Smart -> oriented.smartCropToWithBounds(size.width, size.height, crop.strategy).let {
                PrivacyDerivativeImageTransform(
                    image = it.image,
                    crop = CropWindow(
                        x = it.crop.x.toDouble(),
                        y = it.crop.y.toDouble(),
                        width = it.crop.width.toDouble(),
                        height = it.crop.height.toDouble(),
                    ),
                )
            }
        }
    }

    val outputDimensions = PrivacyImageDimensions(width = transformed.image.width, height = transformed.image.height)
    val redactions = options.redactions.renderableRedactions(
        sourceDimensions = sourceDimensions,
        outputDimensions = outputDimensions,
        orientation = if (options.normalizeOrientation) orientation else null,
        crop = transformed.crop,
    )
    var current = transformed.image
    if (options.redactions.isNotEmpty()) {
        current = current.withGraphics { graphics ->
            redactions.forEach { redaction ->
                graphics.composite = AlphaComposite.SrcOver.derive(redaction.request.maskOpacity.toFloat())
                graphics.color = Color(redaction.request.maskColorArgb, true)
                graphics.fillRect(
                    redaction.applied.x,
                    redaction.applied.y,
                    redaction.applied.width,
                    redaction.applied.height,
                )
            }
        }
    }
    return PrivacyDerivativeTransformResult(current, redactions.map { it.applied })
}

private fun ImmutableImage.normalizeExifOrientation(orientation: Int?): ImmutableImage =
    when (orientation) {
        2 -> flipHorizontal()
        3 -> rotateDegrees(180.0)
        4 -> flipVertical()
        5 -> flipHorizontal().rotateDegrees(270.0)
        6 -> rotateDegrees(90.0)
        7 -> flipHorizontal().rotateDegrees(90.0)
        8 -> rotateDegrees(270.0)
        else -> copy()
    }

private fun List<PrivacyRedaction>.renderableRedactions(
    sourceDimensions: PrivacyImageDimensions,
    outputDimensions: PrivacyImageDimensions,
    orientation: Int?,
    crop: CropWindow,
): List<RenderablePrivacyRedaction> =
    mapNotNull { redaction ->
        when (val geometry = redaction.region.geometry) {
            is SensitiveRegionGeometry.Rectangle ->
                geometry.toAppliedRedaction(
                    sourceDimensions = sourceDimensions,
                    outputDimensions = outputDimensions,
                    orientation = orientation,
                    crop = crop,
                    redaction = redaction,
                )?.let { RenderablePrivacyRedaction(redaction, it) }

            else -> null
        }
    }

private fun SensitiveRegionGeometry.Rectangle.toAppliedRedaction(
    sourceDimensions: PrivacyImageDimensions,
    outputDimensions: PrivacyImageDimensions,
    orientation: Int?,
    crop: CropWindow,
    redaction: PrivacyRedaction,
): AppliedPrivacyRedaction? {
    requireWithin(sourceDimensions)
    val sourceBounds = when (coordinateSpace) {
        SensitiveCoordinateSpace.PIXEL -> RectangleBounds(
            left = x,
            top = y,
            right = x + width,
            bottom = y + height,
        )

        SensitiveCoordinateSpace.NORMALIZED -> RectangleBounds(
            left = x * sourceDimensions.width,
            top = y * sourceDimensions.height,
            right = (x + width) * sourceDimensions.width,
            bottom = (y + height) * sourceDimensions.height,
        )
    }
    val orientedBounds = sourceBounds.transformOrientation(orientation, sourceDimensions)
    val croppedBounds = orientedBounds.intersect(crop) ?: return null
    val bounds = croppedBounds.toPixelBounds(crop, outputDimensions) ?: return null

    return AppliedPrivacyRedaction(
        regionId = redaction.region.id,
        mode = redaction.mode,
        x = bounds.x,
        y = bounds.y,
        width = bounds.width,
        height = bounds.height,
    )
}

private class PrivacyDerivativeTransformResult(
    val image: ImmutableImage,
    val redactions: List<AppliedPrivacyRedaction>,
)

/** derivative image와 orientation 이후 crop window를 함께 보관합니다. */
private class PrivacyDerivativeImageTransform(
    val image: ImmutableImage,
    val crop: CropWindow,
)

/** orientation이 적용된 이미지 좌표계의 crop window입니다. */
private class CropWindow(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
)

/** source rectangle을 변환하는 동안 유지하는 연속 좌표 bounds입니다. */
private class RectangleBounds(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
) {
    fun transformOrientation(
        orientation: Int?,
        sourceDimensions: PrivacyImageDimensions,
    ): RectangleBounds = when (orientation) {
        2 -> RectangleBounds(
            left = sourceDimensions.width - right,
            top = top,
            right = sourceDimensions.width - left,
            bottom = bottom,
        )

        3 -> RectangleBounds(
            left = sourceDimensions.width - right,
            top = sourceDimensions.height - bottom,
            right = sourceDimensions.width - left,
            bottom = sourceDimensions.height - top,
        )

        4 -> RectangleBounds(
            left = left,
            top = sourceDimensions.height - bottom,
            right = right,
            bottom = sourceDimensions.height - top,
        )

        5 -> RectangleBounds(
            left = top,
            top = left,
            right = bottom,
            bottom = right,
        )

        6 -> RectangleBounds(
            left = sourceDimensions.height - bottom,
            top = left,
            right = sourceDimensions.height - top,
            bottom = right,
        )

        7 -> RectangleBounds(
            left = sourceDimensions.height - bottom,
            top = sourceDimensions.width - right,
            right = sourceDimensions.height - top,
            bottom = sourceDimensions.width - left,
        )

        8 -> RectangleBounds(
            left = top,
            top = sourceDimensions.width - right,
            right = bottom,
            bottom = sourceDimensions.width - left,
        )

        else -> this
    }

    fun intersect(crop: CropWindow): RectangleBounds? {
        val intersection = RectangleBounds(
            left = maxOf(left, crop.x),
            top = maxOf(top, crop.y),
            right = minOf(right, crop.x + crop.width),
            bottom = minOf(bottom, crop.y + crop.height),
        )
        return intersection.takeIf { it.right > it.left && it.bottom > it.top }
    }

    fun toPixelBounds(crop: CropWindow, outputDimensions: PrivacyImageDimensions): PixelBounds? {
        val rawLeft = (left - crop.x) * outputDimensions.width / crop.width
        val rawTop = (top - crop.y) * outputDimensions.height / crop.height
        val rawRight = (right - crop.x) * outputDimensions.width / crop.width
        val rawBottom = (bottom - crop.y) * outputDimensions.height / crop.height
        val x = floor(rawLeft).toInt().coerceIn(0, outputDimensions.width - 1)
        val y = floor(rawTop).toInt().coerceIn(0, outputDimensions.height - 1)
        val right = ceil(rawRight).toInt().coerceIn(x + 1, outputDimensions.width)
        val bottom = ceil(rawBottom).toInt().coerceIn(y + 1, outputDimensions.height)
        return PixelBounds(
            x = x,
            y = y,
            width = right - x,
            height = bottom - y,
        )
    }
}

private class RenderablePrivacyRedaction(
    val request: PrivacyRedaction,
    val applied: AppliedPrivacyRedaction,
)

private data class PixelBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

private fun PrivacyImageDimensions.requireWithin(
    options: PrivacyDerivativeOptions,
    subject: String,
): PrivacyImageDimensions {
    requireMaxPixels(options.maxPixels, subject)
    options.maxSide?.let { requireMaxSide(it, subject) }
    return this
}

private fun ImmutableImage.privacyDimensions(): PrivacyImageDimensions =
    PrivacyImageDimensions(width = width, height = height)

private fun PrivacyDerivativeOptions.requestedMetadataCategories(): Set<PrivacyMetadataCategory> =
    buildSet {
        if (removeGps) {
            add(PrivacyMetadataCategory.GPS)
        }
        if (stripMetadata) {
            add(PrivacyMetadataCategory.EXIF)
            add(PrivacyMetadataCategory.XMP)
            add(PrivacyMetadataCategory.IPTC)
            add(PrivacyMetadataCategory.ICC)
        }
        if (normalizeOrientation) {
            add(PrivacyMetadataCategory.ORIENTATION)
        }
    }

private fun PrivacyMetadataVerification.verifiedRemovedCategories(): Set<PrivacyMetadataCategory> =
    sourcePresent.intersect(requested).subtract(remaining)

private fun ExifData.privacyMetadataCategories(): Set<PrivacyMetadataCategory> =
    buildSet {
        if (hasAnyGpsMetadata()) {
            add(PrivacyMetadataCategory.GPS)
        }
        if (hasPrivacyExifMetadata()) {
            add(PrivacyMetadataCategory.EXIF)
        }
        if (orientation != null) {
            add(PrivacyMetadataCategory.ORIENTATION)
        }
    }

private fun ImageMetadataReport.privacyMetadataCategories(): Set<PrivacyMetadataCategory> =
    buildSet {
        if (containsGps || exif.hasAnyGpsMetadata()) {
            add(PrivacyMetadataCategory.GPS)
        }
        if (containsExif || exif.hasPrivacyExifMetadata()) {
            add(PrivacyMetadataCategory.EXIF)
        }
        if (orientation != null) {
            add(PrivacyMetadataCategory.ORIENTATION)
        }
        if (containsXmp) {
            add(PrivacyMetadataCategory.XMP)
        }
        if (containsIptc) {
            add(PrivacyMetadataCategory.IPTC)
        }
        if (containsIccProfile) {
            add(PrivacyMetadataCategory.ICC)
        }
    }

private fun ExifData.hasPrivacyExifMetadata(): Boolean =
    listOf(
        dateTimeOriginal,
        cameraMake,
        cameraModel,
        lensModel,
        iso,
        shutterSpeed,
        aperture,
        focalLength,
        focalLength35mm,
        flashFired,
        whiteBalance,
    ).any { it != null }

private fun ExifData.hasAnyGpsMetadata(): Boolean =
    gpsLatitude != null || gpsLongitude != null || gpsAltitude != null

private fun Int.requireNonNegative(name: String) {
    require(this >= 0) { "$name must be >= 0, but was $this" }
}

private fun Long.requireNonNegative(name: String) {
    require(this >= 0L) { "$name must be >= 0, but was $this" }
}

private fun String?.requireNotBlankIfPresent(name: String) {
    if (this != null) {
        requireNotBlank(name)
    }
}

private const val OPACITY_MIN = 0.0
private const val OPACITY_MAX = 1.0

private val METADATA_STRIPPED_CATEGORIES = setOf(
    PrivacyMetadataCategory.EXIF,
    PrivacyMetadataCategory.XMP,
    PrivacyMetadataCategory.IPTC,
    PrivacyMetadataCategory.ICC,
)
