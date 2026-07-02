package io.bluetape4k.images.barcode

import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requireNotEmpty
import java.io.Serializable

/**
 * Stable barcode symbology understood by bluetape4k barcode providers.
 *
 * ## Contract
 * Providers should map backend-specific format names into this enum and keep
 * the original backend format on [BarcodeResult.rawBackendFormat] when useful.
 * Unknown or provider-specific formats should use [UNKNOWN].
 */
enum class BarcodeFormat {
    /** QR Code two-dimensional barcode. */
    QR_CODE,

    /** Code 128 one-dimensional barcode. */
    CODE_128,

    /** Code 39 one-dimensional barcode. */
    CODE_39,

    /** EAN-13 retail barcode. */
    EAN_13,

    /** EAN-8 retail barcode. */
    EAN_8,

    /** UPC-A retail barcode. */
    UPC_A,

    /** UPC-E retail barcode. */
    UPC_E,

    /** Data Matrix two-dimensional barcode. */
    DATA_MATRIX,

    /** Aztec two-dimensional barcode. */
    AZTEC,

    /** PDF417 stacked barcode. */
    PDF_417,

    /** Codabar one-dimensional barcode. */
    CODABAR,

    /** Interleaved 2 of 5 one-dimensional barcode. */
    ITF,

    /** Backend-specific or unknown format. */
    UNKNOWN,
}

/**
 * Coordinate system for barcode localization data.
 */
enum class BarcodeCoordinateSpace {
    /** Pixel coordinates in the original image coordinate space. */
    PIXEL,

    /** Normalized coordinates where both axes are in `0.0..1.0`. */
    NORMALIZED,
}

/**
 * Provider identity copied into every barcode result.
 *
 * ## Contract
 * [name] is the stable provider name, such as `ZXing` or `BoofCV`. [version],
 * [backend], and [metadata] are optional string-only diagnostics and must not
 * expose provider-specific mutable objects.
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
            BarcodeProviderIdentity(name, version, backend, metadata)
    }
}

/**
 * Point used by barcode localization results.
 *
 * ## Contract
 * Values must be finite. Coordinate-space-specific bounds are checked when the
 * point is placed inside a [BarcodeRegion].
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
 * Axis-aligned barcode bounding box.
 *
 * ## Contract
 * Pixel boxes require non-negative origin and positive dimensions. Normalized
 * boxes must fit in the inclusive `0.0..1.0` source image plane.
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
 * Barcode localization data returned by a provider.
 *
 * ## Contract
 * [points] may contain the provider's finder/result points and does not need to
 * be a closed polygon. [boundingBox] is optional because some providers return
 * points only.
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
            BarcodeRegion(points, coordinateSpace, boundingBox)
    }
}

/**
 * Provider-neutral barcode extraction options.
 *
 * ## Contract
 * Empty [formats] means all provider-supported formats. [minimumConfidence]
 * filters only results that carry confidence; results with `null` confidence
 * are retained because many barcode libraries do not expose score data.
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
     * Returns true when [result] matches this format and confidence filter.
     */
    fun accepts(result: BarcodeResult): Boolean =
        (formats.isEmpty() || result.format in formats) &&
            (minimumConfidence == null || result.confidence == null || result.confidence >= minimumConfidence)

    /**
     * Applies [accepts] to [results] while preserving provider order.
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
            BarcodeOptions(formats, tryHarder, includeRawBytes, minimumConfidence, metadata)
    }
}

/**
 * Decoded barcode result.
 *
 * ## Contract
 * [text] is the decoded payload. [format] is the bluetape4k normalized format
 * and [rawBackendFormat] may carry the provider-native format string. [rawBytes]
 * is optional and must be provided only when requested and available.
 */
@ConsistentCopyVisibility
data class BarcodeResult private constructor(
    val text: String,
    val format: BarcodeFormat,
    val provider: BarcodeProviderIdentity,
    val region: BarcodeRegion? = null,
    val confidence: Double? = null,
    val quality: Double? = null,
    val rawBytes: ByteArray? = null,
    val rawBackendFormat: String? = null,
    val metadata: Map<String, String> = emptyMap(),
): Serializable {

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
