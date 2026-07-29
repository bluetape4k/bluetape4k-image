package io.bluetape4k.images.vips

import io.bluetape4k.support.requireNotBlank
import java.io.Serializable

/**
 * 한 방향에 대한 native libvips codec 지원 상태입니다.
 */
enum class VipsCodecSupport {
    /** backend가 codec operation을 사용할 수 있음을 확인했습니다. */
    AVAILABLE,

    /** backend가 codec operation을 사용할 수 없음을 확인했습니다. */
    UNAVAILABLE,

    /** backend가 codec operation을 직접 inspect할 수 없습니다. */
    UNKNOWN,
}

/**
 * codec operation direction입니다.
 */
enum class VipsCodecDirection {
    /** image bytes를 libvips image로 decode합니다. */
    DECODE,

    /** libvips image를 image bytes로 encode합니다. */
    ENCODE,
}

/**
 * codec operation direction 하나의 capability입니다.
 *
 * @property direction decode 또는 encode direction입니다.
 * @property support 관측된 support state입니다.
 * @property operationName backend가 inspect할 수 있을 때의 native libvips operation name입니다.
 * @property reason 안전한 diagnostic detail입니다. raw native error text를 포함하면 안 됩니다.
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
         * available operation capability를 생성합니다.
         */
        fun available(
            direction: VipsCodecDirection,
            operationName: String? = null,
            reason: String? = null,
        ): VipsCodecOperationCapability =
            VipsCodecOperationCapability(direction, VipsCodecSupport.AVAILABLE, operationName, reason)

        /**
         * unavailable operation capability를 생성합니다.
         */
        fun unavailable(
            direction: VipsCodecDirection,
            operationName: String? = null,
            reason: String,
        ): VipsCodecOperationCapability =
            VipsCodecOperationCapability(direction, VipsCodecSupport.UNAVAILABLE, operationName, reason)

        /**
         * unknown operation capability를 생성합니다.
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
 * HEIF-family image format 하나에 대한 capability report입니다.
 *
 * stable format(`JPEG`, `PNG`, `WEBP`)은 optional HEIF-family native codec이 필요하지 않으므로
 * [VipsCodecCapabilityReport.stableFormats]로 보고합니다.
 */
@OptIn(VipsIncubatingApi::class)
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
         * AVIF 또는 HEIC format의 capability entry를 생성합니다.
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
 * backend-level codec capability report입니다.
 *
 * @property backendName `JVips/JNI` 또는 `vips-ffm` 같은 사람이 읽을 수 있는 backend name입니다.
 * @property libvipsVersion backend가 노출할 수 있을 때의 native libvips version입니다.
 * @property stableFormats optional HEIF-family codec 없이 지원되는 format입니다.
 * @property codecs HEIF-family codec capability 목록입니다.
 * @property inspectedOperations 이 report를 위해 inspect한 native libvips operation name입니다.
 */
@OptIn(VipsIncubatingApi::class)
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
     * [format]이 stable unconditional format 중 하나이면 `true`를 반환합니다.
     */
    fun isStableFormat(format: VipsImageFormat): Boolean =
        format in stableFormats

    /**
     * [format]에 해당하는 HEIF-family codec capability를 찾습니다.
     *
     * @throws IllegalArgumentException [format]에 대한 capability가 없으면 던집니다.
     */
    fun codec(format: VipsImageFormat): VipsCodecCapability =
        codecs.firstOrNull { it.format == format }
            ?: throw IllegalArgumentException("No codec capability for $format")

    companion object {
        private const val serialVersionUID: Long = -6234628369549391519L

        /** optional HEIF-family codec에 의존하지 않는 stable libvips format입니다. */
        val DEFAULT_STABLE_FORMATS = setOf(VipsImageFormat.JPEG, VipsImageFormat.PNG, VipsImageFormat.WEBP)
    }
}

/**
 * caller가 제공한 sample bytes로 실행한 opt-in codec smoke test 결과입니다.
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

    /** decode와 encode stage가 모두 완료되면 `true`입니다. */
    val succeeded: Boolean
        get() = decoded && encoded && failureStage == null

    companion object {
        private const val serialVersionUID: Long = -3548368494090999922L

        /**
         * 성공한 smoke-test result를 생성합니다.
         */
        fun success(backendName: String, format: VipsImageFormat): VipsCodecSmokeResult =
            VipsCodecSmokeResult(
                backendName = backendName,
                format = format,
                decoded = true,
                encoded = true,
            )

        /**
         * 안전한 diagnostic text를 담은 failed smoke-test result를 생성합니다.
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
