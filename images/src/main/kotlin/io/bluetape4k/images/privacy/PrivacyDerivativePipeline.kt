package io.bluetape4k.images.privacy

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.coroutines.flow.extensions.mapParallel
import io.bluetape4k.images.ImageDimensions
import io.bluetape4k.images.analysis.ExifData
import io.bluetape4k.images.analysis.readExif
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
import io.bluetape4k.images.transforms.smartCropTo
import io.bluetape4k.images.withGraphics
import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requirePositiveNumber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.awt.AlphaComposite
import java.awt.Color
import java.io.Serializable
import java.nio.file.Path
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/**
 * Public-safe alias for derivative image dimensions.
 */
typealias PrivacyImageDimensions = ImageDimensions

/**
 * Processing stage used when privacy derivative generation fails.
 */
enum class PrivacyDerivativeFailureStage {
    VALIDATION,
    LOAD,
    TRANSFORM,
    WRITE,
}

/**
 * Metadata categories intentionally not copied into public derivatives.
 */
enum class PrivacyMetadataCategory {
    GPS,
    EXIF,
    ORIENTATION,
}

/**
 * Actions applied while building a public-safe derivative.
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
 * Redaction rendering mode for localized privacy treatment.
 */
enum class PrivacyRedactionMode {
    /** Draws an opaque or translucent rectangle over the sensitive region. */
    SOLID_MASK,
}

/**
 * Encoded output format for a privacy derivative.
 *
 * ## Contract
 * - [writer] is the coroutine-aware encoder used to re-encode the derivative.
 * - Re-encoding writes fresh bytes and does not copy source EXIF payloads.
 * - [extension] is normalized for caller-side naming; this class does not
 *   choose storage paths.
 */
data class PrivacyDerivativeFormat(
    @Transient val writer: SuspendImageWriter,
    val extension: String,
) : Serializable {

    val normalizedExtension: String = extension.trim().removePrefix(".").lowercase()

    init {
        normalizedExtension.requireNotBlank("extension")
        require(!normalizedExtension.contains(PATH_SEPARATOR)) { "extension must not contain a path separator." }
        require(!normalizedExtension.contains(WINDOWS_PATH_SEPARATOR)) { "extension must not contain a path separator." }
    }

    companion object {
        private const val serialVersionUID: Long = 5543586672644358734L
        private const val PATH_SEPARATOR = '/'
        private const val WINDOWS_PATH_SEPARATOR = '\\'

        /** JPEG output suitable for most public thumbnails and previews. */
        val Jpeg: PrivacyDerivativeFormat = PrivacyDerivativeFormat(SuspendJpegWriter.Default, "jpg")

        /** PNG output for lossless public derivatives. */
        val Png: PrivacyDerivativeFormat = PrivacyDerivativeFormat(SuspendPngWriter.MaxCompression, "png")
    }
}

/**
 * Localized privacy redaction to apply before encoding the public derivative.
 *
 * ## Contract
 * - [region] uses the same geometry model as sensitive-content moderation.
 * - Only rectangle geometries are rendered by this core pipeline. Other
 *   geometries should be rasterized by detector adapters before calling this
 *   function.
 * - [maskOpacity] is clamped by validation to the inclusive `0.0..1.0` range.
 */
data class PrivacyRedaction(
    val region: SensitiveRegion,
    val mode: PrivacyRedactionMode = PrivacyRedactionMode.SOLID_MASK,
    val maskColorArgb: Int = Color.BLACK.rgb,
    val maskOpacity: Double = 1.0,
) : Serializable {

    init {
        require(maskOpacity.isFinite()) { "maskOpacity must be finite, but was $maskOpacity" }
        require(maskOpacity in OPACITY_MIN..OPACITY_MAX) { "maskOpacity must be in 0.0..1.0, but was $maskOpacity" }
    }

    companion object {
        private const val serialVersionUID: Long = -8893722484044437024L
    }
}

/**
 * Options for building a privacy-safe derivative image.
 *
 * ## Contract
 * - Source dimensions are validated before expensive transforms.
 * - Metadata and GPS removal are reportable policy decisions. Re-encoding the
 *   derivative writes fresh bytes instead of copying source metadata.
 * - [thumbnailSize] uses the existing thumbnail model to keep public preview
 *   sizing consistent with the thumbnail pipeline.
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
) : Serializable {

    init {
        maxPixels.requirePositiveNumber("maxPixels")
        maxSide?.requirePositiveNumber("maxSide")
    }

    companion object {
        private const val serialVersionUID: Long = 4532641171246800582L
    }
}

/**
 * Failure item captured in a derivative report or batch result.
 */
data class PrivacyDerivativeFailure(
    val stage: PrivacyDerivativeFailureStage,
    val message: String,
) : Serializable {

    init {
        message.requireNotBlank("message")
    }

    companion object {
        private const val serialVersionUID: Long = -6318078195129524167L
    }
}

/**
 * Applied redaction summary for audit logs and client diagnostics.
 */
data class AppliedPrivacyRedaction(
    val regionId: String?,
    val mode: PrivacyRedactionMode,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) : Serializable {

    init {
        x.requireNonNegative("x")
        y.requireNonNegative("y")
        width.requirePositiveNumber("width")
        height.requirePositiveNumber("height")
    }

    companion object {
        private const val serialVersionUID: Long = 8179781583223543732L
    }
}

/**
 * Audit report for one public-safe derivative.
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
) : Serializable {

    init {
        source.requireNotBlankIfPresent("source")
        elapsedMillis.requireNonNegative("elapsedMillis")
    }

    companion object {
        private const val serialVersionUID: Long = 270973442118913329L
    }
}

/**
 * Successful privacy derivative payload.
 */
data class PrivacyDerivativeResult(
    val image: ImmutableImage,
    val bytes: ByteArray,
    val report: PrivacyDerivativeReport,
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = -5269759565320199140L
    }
}

/**
 * Batch result for [processPrivacyDerivatives].
 */
sealed interface PrivacyDerivativeBatchResult : Serializable {
    /** Source path that produced this result. */
    val source: Path

    /** Successfully generated derivative. */
    data class Success(
        override val source: Path,
        val result: PrivacyDerivativeResult,
    ) : PrivacyDerivativeBatchResult {
        companion object {
            private const val serialVersionUID: Long = -4865455572333443922L
        }
    }

    /** Failed derivative generation item. */
    data class Failure(
        override val source: Path,
        val stage: PrivacyDerivativeFailureStage,
        val cause: Throwable,
    ) : PrivacyDerivativeBatchResult {
        companion object {
            private const val serialVersionUID: Long = 6204453107661915775L
        }
    }
}

/**
 * Builds a public-safe derivative of this image.
 *
 * ## Contract
 * - The source image is not mutated.
 * - Source dimensions are validated before resize/redaction work.
 * - The returned bytes are freshly encoded by [PrivacyDerivativeOptions.outputFormat].
 * - Cancellation is rethrown unchanged.
 */
suspend fun ImmutableImage.suspendPrivacyDerivative(
    options: PrivacyDerivativeOptions = PrivacyDerivativeOptions(),
    sourceExif: ExifData = ExifData.EMPTY,
    source: Path? = null,
): PrivacyDerivativeResult {
    val started = TimeSource.Monotonic.markNow()
    val sourceDimensions = PrivacyImageDimensions(width = width, height = height)
    sourceDimensions.requireWithin(options, source?.toString() ?: "image")

    try {
        val transformed = applyDerivativeTransforms(options, sourceDimensions, sourceExif.orientation)
        val bytes = transformed.image.suspendBytes(options.outputFormat.writer)
        val outputDimensions = PrivacyImageDimensions(width = transformed.image.width, height = transformed.image.height)
        val strippedMetadata = sourceExif.strippedMetadataCategories(options)
        val redactions = transformed.redactions
        val actions = buildList {
            if (options.removeGps && sourceExif.hasGps) {
                add(PrivacyDerivativeAction.GPS_REMOVED)
            }
            if (options.stripMetadata && sourceExif.hasExifMetadata()) {
                add(PrivacyDerivativeAction.METADATA_STRIPPED)
            }
            if (options.normalizeOrientation && sourceExif.orientation.needsOrientationNormalization()) {
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
            ),
        )
    } catch (e: CancellationException) {
        throw e
    }
}

/**
 * Processes image paths into privacy-safe derivatives using the same core
 * transform logic as [suspendPrivacyDerivative].
 *
 * ## Contract
 * - Header dimensions are probed before full decode when ImageIO supports the
 *   source format.
 * - [ImageProcessingOptions.maxInFlightPixels] gates concurrent decoded work.
 * - When [ImageProcessingOptions.skipFailures] is `true`, failures are emitted
 *   as [PrivacyDerivativeBatchResult.Failure] and forwarded to [onFailure].
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
            val sourceExif = runDerivativeStage(source, PrivacyDerivativeFailureStage.LOAD) {
                withContext(processingOptions.ioDispatcher) { source.readExif() }
            }
            image.privacyDimensions().requireWithin(privacyOptions, source.toString())

            val result = runDerivativeStage(source, PrivacyDerivativeFailureStage.TRANSFORM) {
                withContext(processingOptions.transformDispatcher) {
                    image.suspendPrivacyDerivative(
                        options = privacyOptions,
                        sourceExif = sourceExif,
                        source = source,
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

    var current = when (val size = options.thumbnailSize) {
        null -> oriented
        else -> when (val crop = options.thumbnailCrop) {
            ThumbnailCrop.Fit -> oriented.scaleTo(size.width, size.height)
            is ThumbnailCrop.Smart -> oriented.smartCropTo(size.width, size.height, crop.strategy)
        }
    }

    val outputDimensions = PrivacyImageDimensions(width = current.width, height = current.height)
    val redactions = options.redactions.renderableRedactions(sourceDimensions, outputDimensions)
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
): List<RenderablePrivacyRedaction> =
    mapNotNull { redaction ->
        when (val geometry = redaction.region.geometry) {
            is SensitiveRegionGeometry.Rectangle ->
                RenderablePrivacyRedaction(
                    redaction,
                    geometry.toAppliedRedaction(sourceDimensions, outputDimensions, redaction),
                )

            else -> null
        }
    }

private fun SensitiveRegionGeometry.Rectangle.toAppliedRedaction(
    sourceDimensions: PrivacyImageDimensions,
    outputDimensions: PrivacyImageDimensions,
    redaction: PrivacyRedaction,
): AppliedPrivacyRedaction {
    requireWithin(sourceDimensions)
    val bounds = when (coordinateSpace) {
        SensitiveCoordinateSpace.PIXEL -> PixelBounds(
            x = (x * outputDimensions.width / sourceDimensions.width).roundToInt(),
            y = (y * outputDimensions.height / sourceDimensions.height).roundToInt(),
            width = (width * outputDimensions.width / sourceDimensions.width).roundToInt(),
            height = (height * outputDimensions.height / sourceDimensions.height).roundToInt(),
        )

        SensitiveCoordinateSpace.NORMALIZED -> PixelBounds(
            x = (x * outputDimensions.width).roundToInt(),
            y = (y * outputDimensions.height).roundToInt(),
            width = (width * outputDimensions.width).roundToInt(),
            height = (height * outputDimensions.height).roundToInt(),
        )
    }.coerceWithin(outputDimensions)

    return AppliedPrivacyRedaction(
        regionId = redaction.region.id,
        mode = redaction.mode,
        x = bounds.x,
        y = bounds.y,
        width = bounds.width,
        height = bounds.height,
    )
}

private data class PrivacyDerivativeTransformResult(
    val image: ImmutableImage,
    val redactions: List<AppliedPrivacyRedaction>,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = -8691864136817785402L
    }
}

private data class RenderablePrivacyRedaction(
    val request: PrivacyRedaction,
    val applied: AppliedPrivacyRedaction,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 3452957474318152798L
    }
}

private data class PixelBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) : Serializable {
    fun coerceWithin(dimensions: PrivacyImageDimensions): PixelBounds {
        val safeX = x.coerceIn(0, dimensions.width - 1)
        val safeY = y.coerceIn(0, dimensions.height - 1)
        val safeWidth = width.coerceAtMost(dimensions.width - safeX).coerceAtLeast(1)
        val safeHeight = height.coerceAtMost(dimensions.height - safeY).coerceAtLeast(1)
        return PixelBounds(safeX, safeY, safeWidth, safeHeight)
    }

    companion object {
        private const val serialVersionUID: Long = 1015043856143229369L
    }
}

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

private fun ExifData.strippedMetadataCategories(options: PrivacyDerivativeOptions): Set<PrivacyMetadataCategory> =
    buildSet {
        if (options.removeGps && hasGps) {
            add(PrivacyMetadataCategory.GPS)
        }
        if (options.stripMetadata && hasExifMetadata()) {
            add(PrivacyMetadataCategory.EXIF)
        }
        if (options.normalizeOrientation && orientation != null) {
            add(PrivacyMetadataCategory.ORIENTATION)
        }
    }

private fun ExifData.hasExifMetadata(): Boolean =
    listOf(
        gpsLatitude,
        gpsLongitude,
        gpsAltitude,
        dateTimeOriginal,
        cameraMake,
        cameraModel,
        lensModel,
        iso,
        shutterSpeed,
        aperture,
        focalLength,
        focalLength35mm,
        orientation,
        width,
        height,
        flashFired,
        whiteBalance,
    ).any { it != null }

private fun Int?.needsOrientationNormalization(): Boolean =
    this != null && this != 1

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
