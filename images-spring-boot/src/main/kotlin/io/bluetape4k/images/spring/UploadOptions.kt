package io.bluetape4k.images.spring

import io.bluetape4k.support.requireNotBlank
import java.io.Serializable

/**
 * Options controlling an image upload operation.
 *
 * ## Behavior
 * - [contentType] must be one of [ALLOWED_CONTENT_TYPES]. SVG is excluded for XSS risk.
 * - [cacheControl] and [metadata] express caller intent for storage or CDN implementations that
 *   support those headers. Implementations that cannot forward them must document that boundary.
 * - Validation runs in `init` block so invalid options are rejected at construction time.
 */
data class UploadOptions(
    val contentType: String = "image/jpeg",
    val cacheControl: String = "public, max-age=31536000",
    val metadata: Map<String, String> = emptyMap(),
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L

        /**
         * Set of MIME types allowed for upload.
         *
         * SVG is intentionally excluded because serving SVG through a CDN creates
         * stored XSS vectors when the CDN returns it with a permissive `Content-Type`.
         */
        val ALLOWED_CONTENT_TYPES: Set<String> = setOf(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif",
            "image/avif",
            "image/heic",
        )
    }

    init {
        contentType.requireNotBlank("contentType")
        require(contentType in ALLOWED_CONTENT_TYPES) {
            "contentType '$contentType' is not allowed. Allowed: $ALLOWED_CONTENT_TYPES"
        }
    }
}
