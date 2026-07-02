package io.bluetape4k.images.vips

import io.bluetape4k.images.IncubatingImageApi
import io.bluetape4k.support.requireNotBlank
import java.io.Serializable

/**
 * Native libvips codec support state for one direction.
 */
enum class VipsCodecSupport {
    /** The backend proved that the codec operation is available. */
    AVAILABLE,

    /** The backend proved that the codec operation is unavailable. */
    UNAVAILABLE,

    /** The backend cannot inspect the codec operation directly. */
    UNKNOWN,
}

/**
 * Codec operation direction.
 */
enum class VipsCodecDirection {
    /** Decode image bytes into a libvips image. */
    DECODE,

    /** Encode a libvips image into image bytes. */
    ENCODE,
}

/**
 * Capability for one codec operation direction.
 *
 * @property direction decode or encode direction.
 * @property support observed support state.
 * @property operationName native libvips operation name when the backend can inspect it.
 * @property reason safe diagnostic detail. Do not include raw native error text.
 */
data class VipsCodecOperationCapability(
    val direction: VipsCodecDirection,
    val support: VipsCodecSupport,
    val operationName: String? = null,
    val reason: String? = null,
) : Serializable {

    init {
        operationName?.requireNotBlank("operationName")
        reason?.requireNotBlank("reason")
    }

    companion object {
        private const val serialVersionUID: Long = 8471687250112502126L

        /**
         * Creates an available operation capability.
         */
        fun available(
            direction: VipsCodecDirection,
            operationName: String? = null,
            reason: String? = null,
        ): VipsCodecOperationCapability =
            VipsCodecOperationCapability(direction, VipsCodecSupport.AVAILABLE, operationName, reason)

        /**
         * Creates an unavailable operation capability.
         */
        fun unavailable(
            direction: VipsCodecDirection,
            operationName: String? = null,
            reason: String,
        ): VipsCodecOperationCapability =
            VipsCodecOperationCapability(direction, VipsCodecSupport.UNAVAILABLE, operationName, reason)

        /**
         * Creates an unknown operation capability.
         */
        fun unknown(
            direction: VipsCodecDirection,
            operationName: String? = null,
            reason: String,
        ): VipsCodecOperationCapability =
            VipsCodecOperationCapability(direction, VipsCodecSupport.UNKNOWN, operationName, reason)
    }
}

/**
 * Capability report for one HEIF-family image format.
 *
 * Stable formats (`JPEG`, `PNG`, and `WEBP`) are reported by
 * [VipsCodecCapabilityReport.stableFormats] because they do not require
 * optional HEIF-family native codecs.
 */
@OptIn(IncubatingImageApi::class)
data class VipsCodecCapability(
    val format: VipsImageFormat,
    val decode: VipsCodecOperationCapability,
    val encode: VipsCodecOperationCapability,
    val nativeDependencies: List<String> = emptyList(),
) : Serializable {

    init {
        nativeDependencies.forEachIndexed { index, dependency ->
            dependency.requireNotBlank("nativeDependencies[$index]")
        }
    }

    companion object {
        private const val serialVersionUID: Long = -4414825673788643066L

        /**
         * Creates a capability entry for an AVIF or HEIC format.
         */
        fun heifFamily(
            format: VipsImageFormat,
            decode: VipsCodecOperationCapability,
            encode: VipsCodecOperationCapability,
            nativeDependencies: List<String>,
        ): VipsCodecCapability {
            require(format == VipsImageFormat.AVIF || format == VipsImageFormat.HEIC) {
                "format must be AVIF or HEIC: $format"
            }
            return VipsCodecCapability(format, decode, encode, nativeDependencies)
        }
    }
}

/**
 * Backend-level codec capability report.
 *
 * @property backendName human-readable backend name such as `JVips/JNI` or `vips-ffm`.
 * @property libvipsVersion native libvips version when the backend can expose it.
 * @property stableFormats formats that are supported without optional HEIF-family codecs.
 * @property codecs HEIF-family codec capabilities.
 * @property inspectedOperations native libvips operation names inspected for this report.
 */
@OptIn(IncubatingImageApi::class)
data class VipsCodecCapabilityReport(
    val backendName: String,
    val libvipsVersion: String? = null,
    val stableFormats: Set<VipsImageFormat> = DEFAULT_STABLE_FORMATS,
    val codecs: List<VipsCodecCapability>,
    val inspectedOperations: Set<String> = emptySet(),
) : Serializable {

    init {
        backendName.requireNotBlank("backendName")
        libvipsVersion?.requireNotBlank("libvipsVersion")
        require(stableFormats.containsAll(DEFAULT_STABLE_FORMATS)) {
            "stableFormats must include JPEG, PNG, and WEBP"
        }
        inspectedOperations.forEachIndexed { index, operation ->
            operation.requireNotBlank("inspectedOperations[$index]")
        }
        require(codecs.map { it.format }.toSet().size == codecs.size) {
            "codecs must not contain duplicate formats"
        }
    }

    /**
     * Returns `true` when [format] is one of the stable unconditional formats.
     */
    fun isStableFormat(format: VipsImageFormat): Boolean =
        format in stableFormats

    /**
     * Finds a HEIF-family codec capability by [format].
     *
     * @throws IllegalArgumentException when no capability is present for [format].
     */
    fun codec(format: VipsImageFormat): VipsCodecCapability =
        codecs.firstOrNull { it.format == format }
            ?: throw IllegalArgumentException("No codec capability for $format")

    companion object {
        private const val serialVersionUID: Long = -6234628369549391519L

        /** Stable libvips formats that do not depend on optional HEIF-family codecs. */
        val DEFAULT_STABLE_FORMATS = setOf(VipsImageFormat.JPEG, VipsImageFormat.PNG, VipsImageFormat.WEBP)
    }
}

/**
 * Result of an opt-in codec smoke test against caller-provided sample bytes.
 */
data class VipsCodecSmokeResult(
    val backendName: String,
    val format: VipsImageFormat,
    val decoded: Boolean,
    val encoded: Boolean,
    val failureStage: VipsCodecDirection? = null,
    val failureReason: String? = null,
) : Serializable {

    init {
        backendName.requireNotBlank("backendName")
        failureReason?.requireNotBlank("failureReason")
        if (failureStage == null) {
            require(failureReason == null) { "failureReason requires failureStage" }
            require(decoded && encoded) { "successful smoke result must decode and encode" }
        } else {
            require(failureReason != null) { "failureStage requires failureReason" }
        }
    }

    /** `true` when both decode and encode stages completed. */
    val succeeded: Boolean
        get() = decoded && encoded && failureStage == null

    companion object {
        private const val serialVersionUID: Long = -3548368494090999922L

        /**
         * Creates a successful smoke-test result.
         */
        fun success(backendName: String, format: VipsImageFormat): VipsCodecSmokeResult =
            VipsCodecSmokeResult(
                backendName = backendName,
                format = format,
                decoded = true,
                encoded = true,
            )

        /**
         * Creates a failed smoke-test result with safe diagnostic text.
         */
        fun failure(
            backendName: String,
            format: VipsImageFormat,
            stage: VipsCodecDirection,
            reason: String,
        ): VipsCodecSmokeResult =
            VipsCodecSmokeResult(
                backendName = backendName,
                format = format,
                decoded = stage != VipsCodecDirection.DECODE,
                encoded = false,
                failureStage = stage,
                failureReason = reason,
            )
    }
}
