package io.bluetape4k.images.barcode

import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requireNotEmpty
import java.io.Serializable
import java.util.Collections

/**
 * bluetape4k barcode provider가 이해하는 안정적인 barcode symbology입니다.
 *
 * ## 동작/계약
 * provider는 backend-specific format name을 이 enum으로 매핑하고, 필요하면 원본 backend
 * format을 [BarcodeResult.rawBackendFormat]에 보존해야 합니다. 알 수 없거나 provider
 * 전용 format은 [UNKNOWN]을 사용합니다.
 */
enum class BarcodeFormat {
    /** QR Code 2차원 barcode입니다. */
    QR_CODE,

    /** Code 128 1차원 barcode입니다. */
    CODE_128,

    /** Code 39 1차원 barcode입니다. */
    CODE_39,

    /** EAN-13 retail barcode입니다. */
    EAN_13,

    /** EAN-8 retail barcode입니다. */
    EAN_8,

    /** UPC-A retail barcode입니다. */
    UPC_A,

    /** UPC-E retail barcode입니다. */
    UPC_E,

    /** Data Matrix 2차원 barcode입니다. */
    DATA_MATRIX,

    /** Aztec 2차원 barcode입니다. */
    AZTEC,

    /** PDF417 stacked barcode입니다. */
    PDF_417,

    /** Codabar 1차원 barcode입니다. */
    CODABAR,

    /** Interleaved 2 of 5 1차원 barcode입니다. */
    ITF,

    /** backend-specific 또는 알 수 없는 format입니다. */
    UNKNOWN,
}

/**
 * barcode localization data에 사용하는 coordinate system입니다.
 */
enum class BarcodeCoordinateSpace {
    /** 원본 이미지 coordinate space의 pixel coordinate입니다. */
    PIXEL,

    /** 두 축이 모두 `0.0..1.0` 범위인 normalized coordinate입니다. */
    NORMALIZED,
}

/**
 * 모든 barcode result에 복사되는 provider identity입니다.
 *
 * ## 동작/계약
 * [name]은 `ZXing` 또는 `BoofCV` 같은 안정적인 provider name입니다. [version],
 * [backend], [metadata]는 선택적 string-only diagnostic이며 provider-specific mutable
 * object를 노출하면 안 됩니다.
 *
 * ```kotlin
 * val provider = BarcodeProviderIdentity(name = "ZXing", version = "3.5.4")
 * ```
 */
@ConsistentCopyVisibility
data class BarcodeProviderIdentity private constructor(
    val name: String,
    val version: String? = null,
    val backend: String? = null,
    val metadata: Map<String, String> = emptyMap(),
): Serializable {

    init {
        name.requireNotBlank("name")
        version?.requireNotBlank("version")
        backend?.requireNotBlank("backend")
        metadata.requireStringMetadata("metadata")
    }

    companion object {
        private const val serialVersionUID: Long = -2406481341850868930L

        operator fun invoke(
            name: String,
            version: String? = null,
            backend: String? = null,
            metadata: Map<String, String> = emptyMap(),
        ): BarcodeProviderIdentity =
            BarcodeProviderIdentity(name, version, backend, metadata.immutableMapSnapshot())
    }
}

/**
 * barcode localization result에서 사용하는 point입니다.
 *
 * ## 동작/계약
 * 값은 finite여야 합니다. coordinate-space-specific bound는 point가 [BarcodeRegion]에
 * 배치될 때 확인합니다.
 */
@ConsistentCopyVisibility
data class BarcodePoint private constructor(
    val x: Double,
    val y: Double,
): Serializable {

    init {
        x.requireFinite("x")
        y.requireFinite("y")
    }

    internal fun requireValidFor(coordinateSpace: BarcodeCoordinateSpace) {
        when (coordinateSpace) {
            BarcodeCoordinateSpace.PIXEL -> {
                require(x >= 0.0) { "pixel x must be >= 0, but was $x" }
                require(y >= 0.0) { "pixel y must be >= 0, but was $y" }
            }

            BarcodeCoordinateSpace.NORMALIZED -> {
                x.requireNormalized("x")
                y.requireNormalized("y")
            }
        }
    }

    companion object {
        private const val serialVersionUID: Long = 4524615516973404144L

        operator fun invoke(x: Double, y: Double): BarcodePoint =
            BarcodePoint(x, y)
    }
}

/**
 * 축에 정렬된 barcode bounding box입니다.
 *
 * ## 동작/계약
 * pixel box는 non-negative origin과 양수 dimension이 필요합니다. normalized box는
 * source image plane의 `0.0..1.0` inclusive 범위 안에 들어가야 합니다.
 */
@ConsistentCopyVisibility
data class BarcodeBoundingBox private constructor(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val coordinateSpace: BarcodeCoordinateSpace,
): Serializable {

    init {
        x.requireFinite("x")
        y.requireFinite("y")
        width.requirePositiveFinite("width")
        height.requirePositiveFinite("height")

        when (coordinateSpace) {
            BarcodeCoordinateSpace.PIXEL -> {
                require(x >= 0.0) { "pixel x must be >= 0, but was $x" }
                require(y >= 0.0) { "pixel y must be >= 0, but was $y" }
            }

            BarcodeCoordinateSpace.NORMALIZED -> {
                x.requireNormalized("x")
                y.requireNormalized("y")
                require(x + width <= NORMALIZED_MAX && y + height <= NORMALIZED_MAX) {
                    "normalized box must fit in 0.0..1.0, but was x=$x, y=$y, width=$width, height=$height"
                }
            }
        }
    }

    companion object {
        private const val serialVersionUID: Long = -6417684676076755248L

        operator fun invoke(
            x: Double,
            y: Double,
            width: Double,
            height: Double,
            coordinateSpace: BarcodeCoordinateSpace = BarcodeCoordinateSpace.PIXEL,
        ): BarcodeBoundingBox =
            BarcodeBoundingBox(x, y, width, height, coordinateSpace)
    }
}

/**
 * provider가 반환하는 barcode localization data입니다.
 *
 * ## 동작/계약
 * [points]는 provider의 finder/result point를 담을 수 있으며 닫힌 polygon일 필요는
 * 없습니다. 일부 provider는 point만 반환하므로 [boundingBox]는 선택값입니다.
 */
@ConsistentCopyVisibility
data class BarcodeRegion private constructor(
    val points: List<BarcodePoint>,
    val coordinateSpace: BarcodeCoordinateSpace,
    val boundingBox: BarcodeBoundingBox? = null,
): Serializable {

    init {
        points.requireNotEmpty("points")
        points.forEach { it.requireValidFor(coordinateSpace) }
        require(boundingBox == null || boundingBox.coordinateSpace == coordinateSpace) {
            "boundingBox coordinateSpace must match region coordinateSpace"
        }
    }

    companion object {
        private const val serialVersionUID: Long = -910069449498651984L

        operator fun invoke(
            points: List<BarcodePoint>,
            coordinateSpace: BarcodeCoordinateSpace,
            boundingBox: BarcodeBoundingBox? = null,
        ): BarcodeRegion =
            BarcodeRegion(points.immutableListSnapshot(), coordinateSpace, boundingBox)
    }
}

/**
 * provider-neutral barcode 추출 option입니다.
 *
 * ## 동작/계약
 * [formats]가 비어 있으면 provider가 지원하는 모든 format을 의미합니다.
 * [minimumConfidence]는 confidence가 있는 결과만 필터링합니다. 많은 barcode library가
 * score data를 노출하지 않으므로 confidence가 `null`인 결과는 유지합니다.
 *
 * ```kotlin
 * val options = BarcodeOptions(formats = setOf(BarcodeFormat.QR_CODE), tryHarder = true)
 * ```
 */
@ConsistentCopyVisibility
data class BarcodeOptions private constructor(
    val formats: Set<BarcodeFormat> = emptySet(),
    val tryHarder: Boolean = false,
    val includeRawBytes: Boolean = false,
    val minimumConfidence: Double? = null,
    val metadata: Map<String, String> = emptyMap(),
): Serializable {

    init {
        minimumConfidence?.requireProbability("minimumConfidence")
        metadata.requireStringMetadata("metadata")
    }

    /**
     * [result]가 이 format 및 confidence filter와 match되면 `true`를 반환합니다.
     */
    fun accepts(result: BarcodeResult): Boolean =
        (formats.isEmpty() || result.format in formats) &&
            (minimumConfidence == null || result.confidence == null || result.confidence >= minimumConfidence)

    /**
     * provider order를 보존하면서 [results]에 [accepts]를 적용합니다.
     */
    fun filter(results: List<BarcodeResult>): List<BarcodeResult> =
        results.filter(::accepts)

    companion object {
        private const val serialVersionUID: Long = 5859961292253423227L

        operator fun invoke(
            formats: Set<BarcodeFormat> = emptySet(),
            tryHarder: Boolean = false,
            includeRawBytes: Boolean = false,
            minimumConfidence: Double? = null,
            metadata: Map<String, String> = emptyMap(),
        ): BarcodeOptions =
            BarcodeOptions(
                formats.immutableSetSnapshot(),
                tryHarder,
                includeRawBytes,
                minimumConfidence,
                metadata.immutableMapSnapshot(),
            )
    }
}

/**
 * 디코딩된 barcode result입니다.
 *
 * ## 동작/계약
 * [text]는 디코딩된 payload입니다. [format]은 bluetape4k normalized format이고,
 * [rawBackendFormat]은 provider-native format string을 담을 수 있습니다. [rawBytes]는
 * 선택값이며 요청되었고 사용 가능한 경우에만 제공해야 합니다. 입력 배열은 생성 시
 * snapshot하고 조회 시 새 배열을 반환하므로 결과의 equality/hash가 외부 mutation에
 * 영향을 받지 않습니다.
 */
class BarcodeResult private constructor(
    val text: String,
    val format: BarcodeFormat,
    val provider: BarcodeProviderIdentity,
    val region: BarcodeRegion? = null,
    val confidence: Double? = null,
    val quality: Double? = null,
    rawBytes: ByteArray? = null,
    rawBackendFormat: String? = null,
    metadata: Map<String, String> = emptyMap(),
): Serializable {

    var rawBytes: ByteArray? = rawBytes?.copyOf()
        get() = field?.copyOf()
        private set(value) {
            field = value?.copyOf()
        }
    val rawBackendFormat: String? = rawBackendFormat
    val metadata: Map<String, String> = metadata.immutableMapSnapshot()

    init {
        text.requireNotBlank("text")
        confidence?.requireProbability("confidence")
        quality?.requireProbability("quality")
        require(rawBytes == null || rawBytes.isNotEmpty()) { "rawBytes must not be empty when present" }
        rawBackendFormat?.requireNotBlank("rawBackendFormat")
        metadata.requireStringMetadata("metadata")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is BarcodeResult) {
            return false
        }

        return text == other.text &&
            format == other.format &&
            provider == other.provider &&
            region == other.region &&
            confidence == other.confidence &&
            quality == other.quality &&
            rawBytes.contentEqualsNullable(other.rawBytes) &&
            rawBackendFormat == other.rawBackendFormat &&
            metadata == other.metadata
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + format.hashCode()
        result = 31 * result + provider.hashCode()
        result = 31 * result + (region?.hashCode() ?: 0)
        result = 31 * result + (confidence?.hashCode() ?: 0)
        result = 31 * result + (quality?.hashCode() ?: 0)
        result = 31 * result + (rawBytes?.contentHashCode() ?: 0)
        result = 31 * result + (rawBackendFormat?.hashCode() ?: 0)
        result = 31 * result + metadata.hashCode()
        return result
    }

    operator fun component1(): String = text

    operator fun component2(): BarcodeFormat = format

    operator fun component3(): BarcodeProviderIdentity = provider

    operator fun component4(): BarcodeRegion? = region

    operator fun component5(): Double? = confidence

    operator fun component6(): Double? = quality

    operator fun component7(): ByteArray? = rawBytes

    operator fun component8(): String? = rawBackendFormat

    operator fun component9(): Map<String, String> = metadata

    override fun toString(): String =
        "BarcodeResult(text=$text, format=$format, provider=$provider, region=$region, " +
            "confidence=$confidence, quality=$quality, rawBytes=${rawBytes?.contentToString()}, " +
            "rawBackendFormat=$rawBackendFormat, metadata=$metadata)"

    companion object {
        private const val serialVersionUID: Long = 8448839205622304997L

        operator fun invoke(
            text: String,
            format: BarcodeFormat,
            provider: BarcodeProviderIdentity,
            region: BarcodeRegion? = null,
            confidence: Double? = null,
            quality: Double? = null,
            rawBytes: ByteArray? = null,
            rawBackendFormat: String? = null,
            metadata: Map<String, String> = emptyMap(),
        ): BarcodeResult =
            BarcodeResult(text, format, provider, region, confidence, quality, rawBytes, rawBackendFormat, metadata)
    }
}

private fun <K, V> Map<K, V>.immutableMapSnapshot(): Map<K, V> =
    Collections.unmodifiableMap(toMap())

private fun <T> List<T>.immutableListSnapshot(): List<T> =
    Collections.unmodifiableList(toList())

private fun <T> Set<T>.immutableSetSnapshot(): Set<T> =
    Collections.unmodifiableSet(toSet())

private const val NORMALIZED_MIN: Double = 0.0
private const val NORMALIZED_MAX: Double = 1.0

private fun Double.requireFinite(name: String) {
    require(isFinite()) { "$name must be finite, but was $this" }
}

private fun Double.requirePositiveFinite(name: String) {
    requireFinite(name)
    require(this > 0.0) { "$name must be > 0.0, but was $this" }
}

private fun Double.requireNormalized(name: String) {
    require(this in NORMALIZED_MIN..NORMALIZED_MAX) {
        "normalized $name must be in 0.0..1.0, but was $this"
    }
}

private fun Double.requireProbability(name: String) {
    requireFinite(name)
    require(this in NORMALIZED_MIN..NORMALIZED_MAX) {
        "$name must be in 0.0..1.0, but was $this"
    }
}

private fun Map<String, String>.requireStringMetadata(name: String) {
    keys.forEach { it.requireNotBlank("$name key") }
    values.forEach { it.requireNotBlank("$name value") }
}

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean =
    when {
        this === other -> true
        this == null || other == null -> false
        else -> contentEquals(other)
    }
