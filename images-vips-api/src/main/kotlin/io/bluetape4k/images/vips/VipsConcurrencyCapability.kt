package io.bluetape4k.images.vips

import io.bluetape4k.support.requireNotBlank
import java.io.Serializable

/**
 * backend가 초기화 후 concurrency 값을 조정할 수 있는지 나타냅니다.
 */
enum class VipsConcurrencySupport {
    /** 요청한 값이 native backend에 적용됩니다. */
    CONFIGURABLE,

    /** backend가 고정 기본값만 사용하며 concurrency 조정을 지원하지 않습니다. */
    UNSUPPORTED,

    /** backend가 실제 적용값을 확인할 수 없습니다. */
    UNKNOWN,
}

/**
 * 런타임 초기화에서 관측한 concurrency 계약입니다.
 *
 * @property support backend가 요청값을 조정할 수 있는지 여부입니다.
 * @property requested 초기화에 사용한 값입니다. 아직 초기화하지 않았으면 `null`입니다.
 * @property effective native backend에 적용된 값입니다. backend가 확인할 수 없으면 `null`입니다.
 * @property reason 지원하지 않거나 확인할 수 없는 이유에 대한 안전한 설명입니다.
 */
data class VipsConcurrencyCapability(
    val support: VipsConcurrencySupport,
    val requested: Int? = null,
    val effective: Int? = null,
    val reason: String? = null,
) : Serializable {

    init {
        requested?.let { require(it > 0) { "requested concurrency must be positive: $it" } }
        effective?.let { require(it > 0) { "effective concurrency must be positive: $it" } }
        reason?.requireNotBlank("reason")
        if (support == VipsConcurrencySupport.CONFIGURABLE && requested != null && effective != null) {
            require(requested == effective) {
                "configurable backend must report the requested value as effective"
            }
        }
    }

    companion object {
        private const val serialVersionUID: Long = 2516882311031290090L

        /** 아직 초기화되지 않아 capability를 관측할 수 없는 상태입니다. */
        fun unknown(reason: String): VipsConcurrencyCapability =
            VipsConcurrencyCapability(VipsConcurrencySupport.UNKNOWN, reason = reason)

        /** 요청값과 native 적용값이 동일한 backend 상태입니다. */
        fun configurable(requested: Int, effective: Int = requested): VipsConcurrencyCapability =
            VipsConcurrencyCapability(
                support = VipsConcurrencySupport.CONFIGURABLE,
                requested = requested,
                effective = effective,
            )

        /** 고정 기본값만 사용하며 native 적용값은 binding에서 확인할 수 없는 상태입니다. */
        fun unsupported(requested: Int?, reason: String): VipsConcurrencyCapability =
            VipsConcurrencyCapability(
                support = VipsConcurrencySupport.UNSUPPORTED,
                requested = requested,
                reason = reason,
            )
    }
}
