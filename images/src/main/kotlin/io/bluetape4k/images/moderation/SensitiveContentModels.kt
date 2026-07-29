package io.bluetape4k.images.moderation

import io.bluetape4k.images.ImageDimensions
import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requirePositiveNumber
import java.io.Serializable

/**
 * bluetape4k moderation caller가 사용하는 안정적인 sensitive-content category입니다.
 *
 * detector adapter는 backend-specific label을 이 category 중 하나로 매핑하되,
 * 원시 backend label은 [SensitiveContentDetection]에 보존해야 합니다.
 */
enum class SensitiveContentCategory {
    /** 명시적인 노출 또는 성적 content입니다. */
    EXPLICIT_NUDITY,

    /** 명시적이지는 않지만 선정적인 content입니다. */
    SUGGESTIVE,

    /** graphic 또는 non-graphic 폭력 content입니다. */
    VIOLENCE,

    /** 자해 또는 자살 관련 content입니다. */
    SELF_HARM,

    /** 혐오, 극단주의, 차별 상징입니다. */
    HATE_SYMBOL,

    /** 무기 또는 무기처럼 보이는 object입니다. */
    WEAPON,

    /** 규제 물질 또는 약물 관련 content입니다. */
    DRUG,

    /** 아동/미성년자 안전 category입니다. */
    MINOR_SAFETY,

    /** 이미지에 보이는 민감한 text입니다. */
    SENSITIVE_TEXT,

    /** 이 version에서 의도적으로 분류하지 않는 category입니다. */
    OTHER,
}

/**
 * sensitive-content detection에 부여되는 policy severity입니다.
 */
enum class SensitiveContentSeverity {
    /** 일반적으로 자동 treatment가 필요 없는 low-risk content입니다. */
    LOW,

    /** caller policy evaluation이 필요할 수 있는 medium-risk content입니다. */
    MEDIUM,

    /** 보통 redaction 또는 review를 유발하는 high-risk content입니다. */
    HIGH,

    /** 보통 rejection 또는 quarantine을 유발하는 critical content입니다. */
    CRITICAL,
}

/**
 * sensitive region geometry가 사용하는 coordinate system입니다.
 */
enum class SensitiveCoordinateSpace {
    /** 원본 이미지 좌표계의 pixel coordinate입니다. */
    PIXEL,

    /** 두 축이 모두 `0.0..1.0` inclusive 범위인 normalized coordinate입니다. */
    NORMALIZED,
}

/**
 * polygon 및 polyline sensitive region이 사용하는 point입니다.
 *
 * @property x region coordinate space에서의 수평 coordinate입니다.
 * @property y region coordinate space에서의 수직 coordinate입니다.
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
 * sensitive region에 대한 raster mask metadata입니다.
 *
 * mask byte는 core model에 의도적으로 넣지 않습니다. detector adapter가 ML 또는 storage
 * dependency를 추가하지 않고 mask를 넘길 수 있도록 caller reference, 크기, 선택적
 * media/checksum metadata만 저장합니다.
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
 * 이미지 안의 sensitive content 위치를 표현하는 geometry variant입니다.
 *
 * `Rectangle`, `Polygon`, `Polyline`은 pixel 및 normalized coordinate system을 지원합니다.
 * `RasterMask`는 mask metadata를 담고 vector coordinate 대신 mask 크기로 검증됩니다.
 */
sealed interface SensitiveRegionGeometry: Serializable {

    /**
     * 이 geometry가 [imageDimensions] 안에 들어가는지 확인합니다.
     *
     * normalized vector geometry는 생성 시 이미 bounded 상태입니다. pixel vector geometry는
     * 원본 이미지 크기에 대해 확인합니다.
     */
    fun requireWithin(imageDimensions: ImageDimensions): SensitiveRegionGeometry

    /**
     * 축에 정렬된 rectangle geometry입니다.
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
     * 닫힌 polygon geometry입니다.
     *
     * 첫 번째 point와 마지막 point는 같아야 합니다. 열린 path에는 [Polyline]을 사용합니다.
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
     * 열린 polyline geometry입니다.
     *
     * polyline은 path 또는 contour를 표현하며 첫 번째 point를 마지막 point로 반복하면
     * 안 됩니다. 닫힌 area에는 [Polygon]을 사용합니다.
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
     * raster mask geometry입니다.
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
 * 위치가 지정된 sensitive image region입니다.
 *
 * region metadata는 의도적으로 string-only입니다. 따라서 detector adapter가 core image
 * module을 특정 runtime에 결합하지 않고 backend hint를 전달할 수 있습니다.
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
 * backend-neutral sensitive-content detection result입니다.
 *
 * 이 model은 detection fact와 blur, mosaic, reject, quarantine, manual review 같은
 * treatment action을 분리합니다. detector adapter는 원시 model label을 안정적인
 * [category] 값으로 매핑하면서 [rawBackendLabel]을 보존해야 합니다.
 *
 * 예:
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
