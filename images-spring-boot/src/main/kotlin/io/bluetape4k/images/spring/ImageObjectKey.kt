package io.bluetape4k.images.spring

import io.bluetape4k.support.requireNotBlank
import java.io.Serializable

/**
 * storage 안의 image object를 식별하는 immutable key입니다.
 *
 * ## 동작
 * - [prefix]와 [name]은 `^[A-Za-z0-9._/-]+$`에 match되어야 하며 `..` segment를 포함하면 안 됩니다.
 * - [fullKey]는 `prefix/name`입니다. prefix가 `/`로 끝나도 double slash를 만들지 않습니다.
 * - validation은 companion factory에서 실행됩니다. `copy()`는 constructor visibility를 유지합니다.
 * - 생성은 [of] factory를 통해 수행합니다.
 */
@ConsistentCopyVisibility
data class ImageObjectKey private constructor(
    val prefix: String,
    val name: String,
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
        private val VALID_SEGMENT = Regex("^[A-Za-z0-9._/-]+$")

        operator fun invoke(prefix: String, name: String): ImageObjectKey {
            prefix.requireNotBlank("prefix")
            name.requireNotBlank("name")
            require(!prefix.contains("..") && !name.contains("..")) {
                "prefix and name must not contain '..' segments"
            }
            require(VALID_SEGMENT.matches(prefix) && VALID_SEGMENT.matches(name)) {
                "prefix and name must match [A-Za-z0-9._/-]+"
            }

            return ImageObjectKey(prefix, name)
        }

        /** 검증된 [ImageObjectKey]를 생성합니다. */
        fun of(prefix: String, name: String): ImageObjectKey = invoke(prefix, name)
    }

    /** separator를 정규화해 `prefix/name`을 반환합니다. */
    val fullKey: String
        get() {
            val p = if (prefix.endsWith("/")) prefix else "$prefix/"
            return "$p$name"
        }
}
