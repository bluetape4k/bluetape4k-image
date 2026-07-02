package io.bluetape4k.images.detection

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.images.ImageDimensions
import io.bluetape4k.images.moderation.SensitiveCoordinateSpace
import io.bluetape4k.images.moderation.SensitivePoint
import io.bluetape4k.images.moderation.SensitiveRasterMask
import io.bluetape4k.images.moderation.SensitiveRegion
import io.bluetape4k.images.moderation.SensitiveRegionGeometry
import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requirePositiveNumber
import java.io.Serializable
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Detector-facing coordinate space alias shared with sensitive-content regions.
 */
typealias DetectionCoordinateSpace = SensitiveCoordinateSpace

/**
 * Detector-facing point alias shared with sensitive-content polygon and polyline regions.
 */
typealias DetectionPoint = SensitivePoint

/**
 * Detector-facing raster mask metadata alias shared with sensitive-content regions.
 */
typealias DetectionRasterMask = SensitiveRasterMask

/**
 * Detector-facing region geometry alias shared with sensitive-content regions.
 */
typealias DetectionRegionGeometry = SensitiveRegionGeometry

/**
 * Detector-facing rectangle geometry alias shared with sensitive-content regions.
 */
typealias DetectionRectangleRegion = SensitiveRegionGeometry.Rectangle

/**
 * Detector-facing polygon geometry alias shared with sensitive-content regions.
 */
typealias DetectionPolygonRegion = SensitiveRegionGeometry.Polygon

/**
 * Detector-facing polyline geometry alias shared with sensitive-content regions.
 */
typealias DetectionPolylineRegion = SensitiveRegionGeometry.Polyline

/**
 * Detector-facing raster-mask geometry alias shared with sensitive-content regions.
 */
typealias DetectionRasterMaskRegion = SensitiveRegionGeometry.RasterMask

/**
 * Detector-facing region alias shared with sensitive-content moderation models.
 */
typealias DetectionRegion = SensitiveRegion

/**
 * Stable backend-neutral category for image detector results.
 */
enum class DetectionCategory {
    /** Human face or face-like region. */
    FACE,

    /** Human body or person region. */
    PERSON,

    /** General object category. */
    OBJECT,

    /** Text-like image region. */
    TEXT,

    /** Logo, mark, or brand-like region. */
    LOGO,

    /** Landmark or scene-level region. */
    LANDMARK,

    /** Sensitive-content region forwarded to moderation policy. */
    SENSITIVE_REGION,

    /** Backend-specific category not modeled by this version. */
    OTHER,
}

/**
 * Detector identity preserved with every detection result.
 *
 * ## Contract
 * - [name] identifies the detector adapter or model family.
 * - [version] and [backend] are optional because fake detectors, remote
 *   services, and local ML runtimes expose different metadata.
 * - [metadata] is string-only so the core image module does not depend on a
 *   specific ML runtime or model manifest format.
 *
 * ```kotlin
 * val detector = DetectorIdentity(name = "fake-face-detector", version = "test")
 * ```
 */
@ConsistentCopyVisibility
data class DetectorIdentity private constructor(
    val name: String,
    val version: String?,
    val backend: String?,
    val metadata: Map<String, String>,
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = -5782049832109058371L

        operator fun invoke(
            name: String,
            version: String? = null,
            backend: String? = null,
            metadata: Map<String, String> = emptyMap(),
        ): DetectorIdentity {
            name.requireNotBlank("name")
            version.requireNotBlankIfPresent("version")
            backend.requireNotBlankIfPresent("backend")
            metadata.requireValidStringMetadata("metadata")
            return DetectorIdentity(name, version, backend, metadata)
        }
    }
}

/**
 * Pixel-space bounding box in original image coordinates.
 *
 * ## Contract
 * - [x] and [y] are zero-based top-left pixel coordinates.
 * - [width] and [height] must be positive.
 * - Use [requireWithin] before treating the box as valid for a concrete image.
 */
@ConsistentCopyVisibility
data class DetectionBoundingBox private constructor(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) : Serializable {

    init {
        require(x >= 0) { "x must be >= 0, but was $x" }
        require(y >= 0) { "y must be >= 0, but was $y" }
        width.requirePositiveNumber("width")
        height.requirePositiveNumber("height")
    }

    /** Ensures this box fits within [imageDimensions]. */
    fun requireWithin(imageDimensions: ImageDimensions): DetectionBoundingBox {
        require(x + width <= imageDimensions.width && y + height <= imageDimensions.height) {
            "bounding box is outside imageBounds=${imageDimensions.width}x${imageDimensions.height}: " +
                "x=$x, y=$y, width=$width, height=$height"
        }
        return this
    }

    companion object {
        private const val serialVersionUID: Long = 6131382378645623427L

        operator fun invoke(x: Int, y: Int, width: Int, height: Int): DetectionBoundingBox =
            DetectionBoundingBox(x, y, width, height)
    }
}

/**
 * Backend-neutral detector output for faces, objects, text, or sensitive regions.
 *
 * ## Contract
 * - [label] is the stable caller-facing label.
 * - [rawBackendLabel] preserves model-specific labels without forcing callers
 *   to parse backend metadata.
 * - [region] reuses the sensitive-content geometry model, so moderation policy
 *   and detector adapters share rectangle, polygon, polyline, and raster-mask
 *   semantics.
 * - This model carries facts only. It does not choose policy actions such as
 *   blur, mosaic, reject, quarantine, or manual review.
 *
 * ```kotlin
 * val result = DetectionResult(
 *     label = "face",
 *     category = DetectionCategory.FACE,
 *     confidence = 0.96,
 *     detector = DetectorIdentity(name = "unit-detector"),
 * )
 * ```
 */
@ConsistentCopyVisibility
data class DetectionResult private constructor(
    val label: String,
    val category: DetectionCategory,
    val confidence: Double,
    val detector: DetectorIdentity,
    val region: DetectionRegion?,
    val rawBackendLabel: String?,
    val classIndex: Int?,
    val metadata: Map<String, String>,
) : Serializable {

    init {
        label.requireNotBlank("label")
        rawBackendLabel.requireNotBlankIfPresent("rawBackendLabel")
        classIndex?.let {
            require(it >= 0) { "classIndex must be >= 0, but was $it" }
        }
        confidence.requireFiniteProbability("confidence")
        require(confidence in CONFIDENCE_MIN..CONFIDENCE_MAX) {
            "confidence must be in 0.0..1.0, but was $confidence"
        }
        metadata.requireValidStringMetadata("metadata")
    }

    /** Ensures the optional region is valid for [imageDimensions]. */
    fun requireWithin(imageDimensions: ImageDimensions): DetectionResult {
        region?.geometry?.requireWithin(imageDimensions)
        return this
    }

    companion object {
        private const val serialVersionUID: Long = -6994715398995598707L

        operator fun invoke(
            label: String,
            category: DetectionCategory,
            confidence: Double,
            detector: DetectorIdentity,
            region: DetectionRegion? = null,
            rawBackendLabel: String? = null,
            classIndex: Int? = null,
            metadata: Map<String, String> = emptyMap(),
        ): DetectionResult =
            DetectionResult(
                label = label,
                category = category,
                confidence = confidence,
                detector = detector,
                region = region,
                rawBackendLabel = rawBackendLabel,
                classIndex = classIndex,
                metadata = metadata,
            )
    }
}

/**
 * Detector query options applied by core detector entry points.
 *
 * ## Contract
 * - [minimumConfidence] filters results below the requested confidence.
 * - Empty [categories] or [labels] means "allow all".
 * - Filtering is deterministic and runtime-free; concrete detectors may also
 *   use the same options to avoid unnecessary backend work.
 */
@ConsistentCopyVisibility
data class DetectionOptions private constructor(
    val minimumConfidence: Double,
    val categories: Set<DetectionCategory>,
    val labels: Set<String>,
) : Serializable {

    init {
        minimumConfidence.requireFiniteProbability("minimumConfidence")
        require(minimumConfidence in CONFIDENCE_MIN..CONFIDENCE_MAX) {
            "minimumConfidence must be in 0.0..1.0, but was $minimumConfidence"
        }
        labels.forEach { it.requireNotBlank("labels") }
    }

    /** Returns true when [result] satisfies this option set. */
    fun accepts(result: DetectionResult): Boolean =
        result.confidence >= minimumConfidence &&
            (categories.isEmpty() || result.category in categories) &&
            (labels.isEmpty() || result.label in labels || result.rawBackendLabel in labels)

    /** Filters [results] according to this option set. */
    fun filter(results: Iterable<DetectionResult>): List<DetectionResult> =
        results.filter(::accepts)

    companion object {
        private const val serialVersionUID: Long = 1682887322970114714L

        operator fun invoke(
            minimumConfidence: Double = CONFIDENCE_MIN,
            categories: Set<DetectionCategory> = emptySet(),
            labels: Set<String> = emptySet(),
        ): DetectionOptions =
            DetectionOptions(minimumConfidence, categories, labels)
    }
}

/**
 * Pluggable detector boundary for [ImmutableImage].
 *
 * ## Contract
 * Implementations may be deterministic fakes, native runtimes, remote services,
 * or model-backed adapters. Production adapters should keep model downloads,
 * native libraries, GPU requirements, and large fixtures outside the core
 * `bluetape4k-images` artifact.
 */
fun interface ImageDetector {

    /** Detects regions or objects in [image] using [options]. */
    fun detect(image: ImmutableImage, options: DetectionOptions): List<DetectionResult>
}

/**
 * Detects image regions with [detector] and validates the selected results
 * against this image's dimensions.
 */
fun ImmutableImage.detectRegions(
    detector: ImageDetector,
    options: DetectionOptions = DetectionOptions(),
): List<DetectionResult> {
    val imageDimensions = ImageDimensions(width = width, height = height)
    return options
        .filter(detector.detect(this, options))
        .map { it.requireWithin(imageDimensions) }
}

/**
 * Detects image regions on [dispatcher].
 *
 * ## Contract
 * - Uses [Dispatchers.Default] by default because local detector adapters are
 *   commonly CPU-bound.
 * - External service adapters can pass [Dispatchers.IO].
 * - Cancellation before dispatch prevents [detector] from starting.
 */
suspend fun ImmutableImage.suspendDetectRegions(
    detector: ImageDetector,
    options: DetectionOptions = DetectionOptions(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): List<DetectionResult> =
    withContext(dispatcher) {
        detectRegions(detector, options)
    }

/**
 * Converts a rectangle geometry to a pixel bounding box for [imageDimensions].
 */
fun DetectionRectangleRegion.toPixelBoundingBox(
    imageDimensions: ImageDimensions,
): DetectionBoundingBox {
    val box = when (coordinateSpace) {
        DetectionCoordinateSpace.PIXEL ->
            DetectionBoundingBox(
                x = x.roundToInt(),
                y = y.roundToInt(),
                width = width.roundToInt(),
                height = height.roundToInt(),
            )

        DetectionCoordinateSpace.NORMALIZED ->
            DetectionBoundingBox(
                x = (x * imageDimensions.width).roundToInt(),
                y = (y * imageDimensions.height).roundToInt(),
                width = (width * imageDimensions.width).roundToInt(),
                height = (height * imageDimensions.height).roundToInt(),
            )
    }

    return box.requireWithin(imageDimensions)
}

/**
 * Returns a pixel bounding box when this result carries rectangle geometry.
 */
fun DetectionResult.pixelBoundingBox(imageDimensions: ImageDimensions): DetectionBoundingBox? =
    (region?.geometry as? DetectionRectangleRegion)?.toPixelBoundingBox(imageDimensions)

private const val CONFIDENCE_MIN = 0.0
private const val CONFIDENCE_MAX = 1.0

private fun Double.requireFiniteProbability(name: String) {
    require(isFinite()) { "$name must be finite, but was $this" }
}

private fun String?.requireNotBlankIfPresent(name: String) {
    if (this != null) {
        requireNotBlank(name)
    }
}

private fun Map<String, String>.requireValidStringMetadata(name: String) {
    forEach { (key, value) ->
        key.requireNotBlank("$name key")
        value.requireNotBlank("$name[$key]")
    }
}
