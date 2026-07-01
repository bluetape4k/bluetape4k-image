package io.bluetape4k.images.moderation

import io.bluetape4k.images.ImageDimensions
import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requirePositiveNumber
import java.io.Serializable

/**
 * Stable sensitive-content category used by bluetape4k moderation callers.
 *
 * Detector adapters should map backend-specific labels into one of these
 * categories while keeping the raw backend label on [SensitiveContentDetection].
 */
enum class SensitiveContentCategory {
    /** Explicit nudity or sexual content. */
    EXPLICIT_NUDITY,

    /** Suggestive but not explicitly sexual content. */
    SUGGESTIVE,

    /** Graphic or non-graphic violent content. */
    VIOLENCE,

    /** Self-harm or suicide-related content. */
    SELF_HARM,

    /** Hate, extremist, or discriminatory symbols. */
    HATE_SYMBOL,

    /** Weapons or weapon-like objects. */
    WEAPON,

    /** Regulated substances or drug-related content. */
    DRUG,

    /** Child/minor safety category. */
    MINOR_SAFETY,

    /** Sensitive text visible in the image. */
    SENSITIVE_TEXT,

    /** Category that is intentionally not classified by this version. */
    OTHER,
}

/**
 * Policy severity assigned to a sensitive-content detection.
 */
enum class SensitiveContentSeverity {
    /** Low-risk content that usually needs no automatic treatment. */
    LOW,

    /** Medium-risk content that may need caller policy evaluation. */
    MEDIUM,

    /** High-risk content that commonly triggers redaction or review. */
    HIGH,

    /** Critical content that commonly triggers rejection or quarantine. */
    CRITICAL,
}

/**
 * Coordinate system used by a sensitive region geometry.
 */
enum class SensitiveCoordinateSpace {
    /** Pixel coordinates in the original image coordinate space. */
    PIXEL,

    /** Normalized coordinates where both axes are in the inclusive `0.0..1.0` range. */
    NORMALIZED,
}

/**
 * Point used by polygon and polyline sensitive regions.
 *
 * @property x horizontal coordinate in the region coordinate space
 * @property y vertical coordinate in the region coordinate space
 */
data class SensitivePoint(
    val x: Double,
    val y: Double,
): Serializable {

    init {
        x.requireFiniteCoordinate("x")
        y.requireFiniteCoordinate("y")
    }

    internal fun requireValidFor(coordinateSpace: SensitiveCoordinateSpace) {
        when (coordinateSpace) {
            SensitiveCoordinateSpace.PIXEL -> {
                require(x >= 0.0) { "pixel x must be >= 0, but was $x" }
                require(y >= 0.0) { "pixel y must be >= 0, but was $y" }
            }

            SensitiveCoordinateSpace.NORMALIZED -> {
                require(x in NORMALIZED_MIN..NORMALIZED_MAX) {
                    "normalized x must be in 0.0..1.0, but was $x"
                }
                require(y in NORMALIZED_MIN..NORMALIZED_MAX) {
                    "normalized y must be in 0.0..1.0, but was $y"
                }
            }
        }
    }

    internal fun requireWithin(imageDimensions: ImageDimensions) {
        require(x <= imageDimensions.width.toDouble() && y <= imageDimensions.height.toDouble()) {
            "point is outside imageBounds=${imageDimensions.width}x${imageDimensions.height}: x=$x, y=$y"
        }
    }

    companion object {
        private const val serialVersionUID: Long = -286128136812377680L
    }
}

/**
 * Raster mask metadata for a sensitive region.
 *
 * Mask bytes are intentionally not embedded in the core model. Store a caller
 * reference, dimensions, and optional media/checksum metadata so detector
 * adapters can hand off masks without adding ML or storage dependencies.
 */
data class SensitiveRasterMask(
    val width: Int,
    val height: Int,
    val reference: String? = null,
    val mediaType: String? = null,
    val checksum: String? = null,
): Serializable {

    init {
        width.requirePositiveNumber("width")
        height.requirePositiveNumber("height")
        reference.requireNotBlankIfPresent("reference")
        mediaType.requireNotBlankIfPresent("mediaType")
        checksum.requireNotBlankIfPresent("checksum")
    }

    companion object {
        private const val serialVersionUID: Long = -236527269489076380L
    }
}

/**
 * Geometry variants that can localize sensitive content in an image.
 *
 * `Rectangle`, `Polygon`, and `Polyline` support pixel and normalized
 * coordinate systems. `RasterMask` carries mask metadata and is validated by
 * mask dimensions instead of vector coordinates.
 */
sealed interface SensitiveRegionGeometry: Serializable {

    /**
     * Ensures this geometry fits within [imageDimensions].
     *
     * Normalized vector geometries are already bounded by construction. Pixel
     * vector geometries are checked against the original image dimensions.
     */
    fun requireWithin(imageDimensions: ImageDimensions): SensitiveRegionGeometry

    /**
     * Axis-aligned rectangle geometry.
     */
    data class Rectangle(
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double,
        val coordinateSpace: SensitiveCoordinateSpace,
    ): SensitiveRegionGeometry {

        init {
            x.requireFiniteCoordinate("x")
            y.requireFiniteCoordinate("y")
            width.requirePositiveFiniteCoordinate("width")
            height.requirePositiveFiniteCoordinate("height")

            when (coordinateSpace) {
                SensitiveCoordinateSpace.PIXEL -> {
                    require(x >= 0.0) { "pixel x must be >= 0, but was $x" }
                    require(y >= 0.0) { "pixel y must be >= 0, but was $y" }
                }

                SensitiveCoordinateSpace.NORMALIZED -> {
                    require(x in NORMALIZED_MIN..NORMALIZED_MAX) {
                        "normalized x must be in 0.0..1.0, but was $x"
                    }
                    require(y in NORMALIZED_MIN..NORMALIZED_MAX) {
                        "normalized y must be in 0.0..1.0, but was $y"
                    }
                    require(x + width <= NORMALIZED_MAX && y + height <= NORMALIZED_MAX) {
                        "normalized rectangle must fit in 0.0..1.0, but was x=$x, y=$y, width=$width, height=$height"
                    }
                }
            }
        }

        override fun requireWithin(imageDimensions: ImageDimensions): SensitiveRegionGeometry {
            if (coordinateSpace == SensitiveCoordinateSpace.PIXEL) {
                require(x + width <= imageDimensions.width.toDouble() && y + height <= imageDimensions.height.toDouble()) {
                    "rectangle is outside imageBounds=${imageDimensions.width}x${imageDimensions.height}: " +
                        "x=$x, y=$y, width=$width, height=$height"
                }
            }
            return this
        }

        companion object {
            private const val serialVersionUID: Long = 115761411896186002L
        }
    }

    /**
     * Closed polygon geometry.
     *
     * The first and last points must be equal. Use [Polyline] for open paths.
     */
    data class Polygon(
        val points: List<SensitivePoint>,
        val coordinateSpace: SensitiveCoordinateSpace,
    ): SensitiveRegionGeometry {

        init {
            require(points.size >= MIN_POLYGON_POINTS) {
                "polygon must contain at least $MIN_POLYGON_POINTS points, including the closing point"
            }
            points.forEach { it.requireValidFor(coordinateSpace) }
            require(points.first() == points.last()) { "polygon must be closed by repeating the first point" }
        }

        override fun requireWithin(imageDimensions: ImageDimensions): SensitiveRegionGeometry {
            if (coordinateSpace == SensitiveCoordinateSpace.PIXEL) {
                points.forEach { it.requireWithin(imageDimensions) }
            }
            return this
        }

        companion object {
            private const val serialVersionUID: Long = 350553443913554107L
        }
    }

    /**
     * Open polyline geometry.
     *
     * A polyline describes a path or contour and must not repeat the first point
     * as the last point. Use [Polygon] for closed areas.
     */
    data class Polyline(
        val points: List<SensitivePoint>,
        val coordinateSpace: SensitiveCoordinateSpace,
    ): SensitiveRegionGeometry {

        init {
            require(points.size >= MIN_POLYLINE_POINTS) { "polyline must contain at least two points" }
            points.forEach { it.requireValidFor(coordinateSpace) }
            require(points.first() != points.last()) { "polyline must remain open; use Polygon for closed areas" }
        }

        override fun requireWithin(imageDimensions: ImageDimensions): SensitiveRegionGeometry {
            if (coordinateSpace == SensitiveCoordinateSpace.PIXEL) {
                points.forEach { it.requireWithin(imageDimensions) }
            }
            return this
        }

        companion object {
            private const val serialVersionUID: Long = -628498279774408037L
        }
    }

    /**
     * Raster mask geometry.
     */
    data class RasterMask(
        val mask: SensitiveRasterMask,
    ): SensitiveRegionGeometry {

        override fun requireWithin(imageDimensions: ImageDimensions): SensitiveRegionGeometry {
            require(mask.width <= imageDimensions.width && mask.height <= imageDimensions.height) {
                "mask is outside imageBounds=${imageDimensions.width}x${imageDimensions.height}: " +
                    "mask=${mask.width}x${mask.height}"
            }
            return this
        }

        companion object {
            private const val serialVersionUID: Long = -735874609657661360L
        }
    }
}

/**
 * Localized sensitive image region.
 *
 * Region metadata is intentionally string-only so detector adapters can carry
 * backend hints without coupling the core image module to a specific runtime.
 */
data class SensitiveRegion(
    val geometry: SensitiveRegionGeometry,
    val id: String? = null,
    val metadata: Map<String, String> = emptyMap(),
): Serializable {

    init {
        id.requireNotBlankIfPresent("id")
        metadata.requireValidMetadata("metadata")
    }

    companion object {
        private const val serialVersionUID: Long = -667440231052868506L
    }
}

/**
 * Backend-neutral sensitive-content detection result.
 *
 * The model separates detection facts from treatment actions such as blur,
 * mosaic, reject, quarantine, or manual review. Detector adapters should map
 * raw model labels to stable [category] values while preserving
 * [rawBackendLabel].
 *
 * Example:
 * ```kotlin
 * val detection = SensitiveContentDetection(
 *     label = "explicit-nudity",
 *     category = SensitiveContentCategory.EXPLICIT_NUDITY,
 *     severity = SensitiveContentSeverity.HIGH,
 *     confidence = 0.94,
 *     sourceBackend = "custom-detector",
 *     rawBackendLabel = "nsfw_explicit",
 * )
 * ```
 */
data class SensitiveContentDetection(
    val label: String,
    val category: SensitiveContentCategory,
    val severity: SensitiveContentSeverity,
    val confidence: Double,
    val sourceBackend: String,
    val rawBackendLabel: String,
    val policyReason: String? = null,
    val region: SensitiveRegion? = null,
    val metadata: Map<String, String> = emptyMap(),
): Serializable {

    init {
        label.requireNotBlank("label")
        sourceBackend.requireNotBlank("sourceBackend")
        rawBackendLabel.requireNotBlank("rawBackendLabel")
        policyReason.requireNotBlankIfPresent("policyReason")
        confidence.requireFiniteCoordinate("confidence")
        require(confidence in CONFIDENCE_MIN..CONFIDENCE_MAX) {
            "confidence must be in 0.0..1.0, but was $confidence"
        }
        metadata.requireValidMetadata("metadata")
    }

    companion object {
        private const val serialVersionUID: Long = 466956545308614167L
    }
}

private const val NORMALIZED_MIN = 0.0
private const val NORMALIZED_MAX = 1.0
private const val CONFIDENCE_MIN = 0.0
private const val CONFIDENCE_MAX = 1.0
private const val MIN_POLYGON_POINTS = 4
private const val MIN_POLYLINE_POINTS = 2

private fun Double.requireFiniteCoordinate(name: String) {
    require(isFinite()) { "$name must be finite, but was $this" }
}

private fun Double.requirePositiveFiniteCoordinate(name: String) {
    requireFiniteCoordinate(name)
    require(this > 0.0) { "$name must be > 0, but was $this" }
}

private fun String?.requireNotBlankIfPresent(name: String) {
    if (this != null) {
        requireNotBlank(name)
    }
}

private fun Map<String, String>.requireValidMetadata(name: String) {
    forEach { (key, value) ->
        key.requireNotBlank("$name key")
        value.requireNotBlank("$name[$key]")
    }
}
