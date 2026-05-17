package io.bluetape4k.images.spring

import java.io.Serializable
import java.time.Instant

/**
 * Result of a successful image upload operation.
 *
 * ## Behavior
 * - [key] identifies where the image was stored.
 * - [etag] is the entity tag returned by the backend (e.g., MD5 hash for S3).
 * - [sizeBytes] is the number of bytes stored.
 * - [contentType] is the MIME type of the stored image.
 * - [uploadedAt] records when the upload completed; defaults to [Instant.now].
 */
data class ImageUploadResult(
    val key: ImageObjectKey,
    val etag: String,
    val sizeBytes: Long,
    val contentType: String,
    val uploadedAt: Instant = Instant.now(),
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
