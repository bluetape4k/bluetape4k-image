package io.bluetape4k.images.spring

import io.bluetape4k.support.requireNotBlank
import java.io.Serializable

/**
 * Immutable key identifying an image object in storage.
 *
 * ## Behavior
 * - [prefix] and [name] must match `^[A-Za-z0-9._/-]+$` and must not contain `..` segments.
 * - [fullKey] is `prefix/name` (no double slash even when prefix ends with `/`).
 * - Validation runs in the companion factory; `copy()` keeps constructor visibility.
 * - Construct via [of] factory.
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

        /** Creates a validated [ImageObjectKey]. */
        fun of(prefix: String, name: String): ImageObjectKey = invoke(prefix, name)
    }

    /** Returns `prefix/name`, normalizing the separator. */
    val fullKey: String
        get() {
            val p = if (prefix.endsWith("/")) prefix else "$prefix/"
            return "$p$name"
        }
}
