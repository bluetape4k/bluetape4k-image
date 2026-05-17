package io.bluetape4k.images.spring

import java.io.Serializable

/**
 * Root exception for image storage operations.
 *
 * ## Behavior
 * - Sealed — exhaustive `when` handling is possible.
 * - Each subclass carries the [key] that failed plus optional [cause].
 * - The [key] property is nullable to support list/bulk operations where no single key is implicated.
 * - Never echo PEM values or signed URL content in exception messages — only [ImageObjectKey.fullKey] is safe.
 */
sealed class ImageStorageException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause), Serializable {

    /** The image key involved in the failure. May be null for list operations. */
    abstract val key: ImageObjectKey?

    companion object {
        private const val serialVersionUID: Long = 1L
    }

    /** Object does not exist at the given key. */
    class NotFoundException(
        override val key: ImageObjectKey,
        message: String = "Image not found: ${key.fullKey}",
        cause: Throwable? = null,
    ) : ImageStorageException(message, cause) {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    /** Caller lacks permission to access the key. */
    class AccessDeniedException(
        override val key: ImageObjectKey,
        message: String = "Access denied: ${key.fullKey}",
        cause: Throwable? = null,
    ) : ImageStorageException(message, cause) {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    /** Object already exists and overwrite is not permitted. */
    class ConflictException(
        override val key: ImageObjectKey,
        message: String = "Conflict: ${key.fullKey}",
        cause: Throwable? = null,
    ) : ImageStorageException(message, cause) {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    /** Transient failure — operation may be retried. */
    class TransientException(
        override val key: ImageObjectKey? = null,
        message: String = "Transient storage error${key?.let { ": ${it.fullKey}" } ?: ""}",
        cause: Throwable? = null,
    ) : ImageStorageException(message, cause) {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    /** Input validation failure (size exceeded, disallowed content type, etc.). */
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
