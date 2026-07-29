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
 * sensitive-content region과 공유하는 detector용 coordinate space alias입니다.
 */
typealias DetectionCoordinateSpace = SensitiveCoordinateSpace

/**
 * sensitive-content polygon 및 polyline region과 공유하는 detector용 point alias입니다.
 */
typealias DetectionPoint = SensitivePoint

/**
 * sensitive-content region과 공유하는 detector용 raster mask metadata alias입니다.
 */
typealias DetectionRasterMask = SensitiveRasterMask

/**
 * sensitive-content region과 공유하는 detector용 region geometry alias입니다.
 */
typealias DetectionRegionGeometry = SensitiveRegionGeometry

/**
 * sensitive-content region과 공유하는 detector용 rectangle geometry alias입니다.
 */
typealias DetectionRectangleRegion = SensitiveRegionGeometry.Rectangle

/**
 * sensitive-content region과 공유하는 detector용 polygon geometry alias입니다.
 */
typealias DetectionPolygonRegion = SensitiveRegionGeometry.Polygon

/**
 * sensitive-content region과 공유하는 detector용 polyline geometry alias입니다.
 */
typealias DetectionPolylineRegion = SensitiveRegionGeometry.Polyline

/**
 * sensitive-content region과 공유하는 detector용 raster-mask geometry alias입니다.
 */
typealias DetectionRasterMaskRegion = SensitiveRegionGeometry.RasterMask

/**
 * sensitive-content moderation model과 공유하는 detector용 region alias입니다.
 */
typealias DetectionRegion = SensitiveRegion

/**
 * 이미지 detector 결과에 사용하는 안정적인 backend-neutral category입니다.
 */
enum class DetectionCategory {
    /** 사람 얼굴 또는 얼굴과 유사한 region입니다. */
    FACE,

    /** 사람 신체 또는 인물 region입니다. */
    PERSON,

    /** 일반 object category입니다. */
    OBJECT,

    /** text처럼 보이는 이미지 region입니다. */
    TEXT,

    /** logo, mark, brand처럼 보이는 region입니다. */
    LOGO,

    /** landmark 또는 scene-level region입니다. */
    LANDMARK,

    /** moderation policy로 전달되는 sensitive-content region입니다. */
    SENSITIVE_REGION,

    /** 이 version에서 모델링하지 않는 backend-specific category입니다. */
    OTHER,
}

/**
 * 모든 detection result에 보존되는 detector identity입니다.
 *
 * ## 동작/계약
 * - [name]은 detector adapter 또는 model family를 식별합니다.
 * - fake detector, remote service, local ML runtime마다 metadata 노출 방식이 달라
 *   [version]과 [backend]는 선택값입니다.
 * - core image module이 특정 ML runtime이나 model manifest 형식에 의존하지 않도록
 *   [metadata]는 string-only map입니다.
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
 * 원본 이미지 좌표계의 pixel-space bounding box입니다.
 *
 * ## 동작/계약
 * - [x]와 [y]는 0 기준 좌상단 pixel coordinate입니다.
 * - [width]와 [height]는 양수여야 합니다.
 * - 구체적인 이미지에서 유효한 box로 다루기 전에 [requireWithin]으로 검증합니다.
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

    /** 이 box가 [imageDimensions] 안에 들어가는지 확인합니다. */
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
 * 얼굴, object, text, sensitive region에 대한 backend-neutral detector 출력입니다.
 *
 * ## 동작/계약
 * - [label]은 호출자에게 노출되는 안정적인 label입니다.
 * - [rawBackendLabel]은 호출자가 backend metadata를 parsing하지 않아도 되도록
 *   model-specific label을 보존합니다.
 * - [region]은 sensitive-content geometry model을 재사용하므로 moderation policy와
 *   detector adapter가 rectangle, polygon, polyline, raster-mask 의미를 공유합니다.
 * - 이 model은 사실만 담습니다. blur, mosaic, reject, quarantine, manual review 같은
 *   policy action은 선택하지 않습니다.
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

    /** 선택 region이 [imageDimensions]에서 유효한지 확인합니다. */
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
 * core detector entry point에 적용되는 detector query option입니다.
 *
 * ## 동작/계약
 * - [minimumConfidence]보다 낮은 confidence의 결과는 걸러냅니다.
 * - [categories] 또는 [labels]가 비어 있으면 "allow all" 의미입니다.
 * - filtering은 deterministic하며 runtime dependency가 없습니다. concrete detector도
 *   불필요한 backend 작업을 피하기 위해 같은 option을 사용할 수 있습니다.
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

    /** [result]가 이 option set을 만족하면 `true`를 반환합니다. */
    fun accepts(result: DetectionResult): Boolean =
        result.confidence >= minimumConfidence &&
            (categories.isEmpty() || result.category in categories) &&
            (labels.isEmpty() || result.label in labels || result.rawBackendLabel in labels)

    /** 이 option set에 따라 [results]를 필터링합니다. */
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
 * [ImmutableImage]에 대한 pluggable detector boundary입니다.
 *
 * ## 동작/계약
 * 구현체는 deterministic fake, native runtime, remote service, model-backed adapter일 수
 * 있습니다. production adapter는 model download, native library, GPU requirement,
 * 큰 fixture를 core `bluetape4k-images` artifact 밖에 둡니다.
 */
fun interface ImageDetector {

    /** [options]를 사용해 [image] 안의 region 또는 object를 탐지합니다. */
    fun detect(image: ImmutableImage, options: DetectionOptions): List<DetectionResult>
}

/**
 * [detector]로 이미지 region을 탐지하고 선택된 결과를 이 이미지 크기에 대해 검증합니다.
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
 * [dispatcher] 위에서 이미지 region을 탐지합니다.
 *
 * ## 동작/계약
 * - local detector adapter는 대체로 CPU-bound이므로 기본값은 [Dispatchers.Default]입니다.
 * - external service adapter는 [Dispatchers.IO]를 전달할 수 있습니다.
 * - dispatch 전에 취소되면 [detector]가 시작되지 않습니다.
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
 * rectangle geometry를 [imageDimensions] 기준 pixel bounding box로 변환합니다.
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
 * 이 결과가 rectangle geometry를 담고 있으면 pixel bounding box를 반환합니다.
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
