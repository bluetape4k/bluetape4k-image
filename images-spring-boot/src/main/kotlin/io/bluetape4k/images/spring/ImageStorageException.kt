package io.bluetape4k.images.spring

import java.io.Serializable

/**
 * image storage operation의 root exception입니다.
 *
 * ## 동작
 * - sealed class이므로 exhaustive `when` 처리가 가능합니다.
 * - 각 subclass는 실패한 [key]와 선택적 [cause]를 담습니다.
 * - [key] property는 단일 key가 특정되지 않는 list/bulk operation을 지원하기 위해 nullable입니다.
 * - exception message에 PEM 값이나 signed URL content를 절대 echo하지 않습니다.
 *   안전하게 노출할 수 있는 값은 [ImageObjectKey.fullKey]뿐입니다.
 */
sealed class ImageStorageException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause), Serializable {

    /** failure와 관련된 image key입니다. list operation에서는 null일 수 있습니다. */
    abstract val key: ImageObjectKey?

    companion object {
        private const val serialVersionUID: Long = 1L
    }

    /** 지정한 key에 object가 없습니다. */
    class NotFoundException(
        override val key: ImageObjectKey,
        message: String = "Image not found: ${key.fullKey}",
        cause: Throwable? = null,
    ) : ImageStorageException(message, cause) {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    /** caller에게 key 접근 권한이 없습니다. */
    class AccessDeniedException(
        override val key: ImageObjectKey,
        message: String = "Access denied: ${key.fullKey}",
        cause: Throwable? = null,
    ) : ImageStorageException(message, cause) {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    /** object가 이미 있고 overwrite가 허용되지 않습니다. */
    class ConflictException(
        override val key: ImageObjectKey,
        message: String = "Conflict: ${key.fullKey}",
        cause: Throwable? = null,
    ) : ImageStorageException(message, cause) {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    /** transient failure입니다. operation은 retry될 수 있습니다. */
    class TransientException(
        override val key: ImageObjectKey? = null,
        message: String = "Transient storage error${key?.let { ": ${it.fullKey}" } ?: ""}",
        cause: Throwable? = null,
    ) : ImageStorageException(message, cause) {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    /** input validation failure입니다(size exceeded, disallowed content type 등). */
    class ValidationException(
        override val key: ImageObjectKey? = null,
        message: String,
        cause: Throwable? = null,
    ) : ImageStorageException(message, cause) {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }
}
