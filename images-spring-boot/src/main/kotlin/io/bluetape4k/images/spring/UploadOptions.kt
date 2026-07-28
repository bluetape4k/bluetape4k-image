package io.bluetape4k.images.spring

import io.bluetape4k.support.requireNotBlank
import java.io.Serializable

/**
 * image upload 작업을 제어하는 option입니다.
 *
 * ## 동작
 * - [contentType]은 [ALLOWED_CONTENT_TYPES] 중 하나여야 합니다. SVG는 XSS risk 때문에 제외합니다.
 * - [cacheControl]과 [metadata]는 해당 header를 지원하는 storage 또는 CDN 구현체에 대한
 *   caller intent를 표현합니다. 이를 전달할 수 없는 구현체는 그 boundary를 문서화해야 합니다.
 * - validation은 `init` block에서 실행되므로 invalid option은 생성 시점에 거부됩니다.
 */
data class UploadOptions(
    val contentType: String = "image/jpeg",
    val cacheControl: String = "public, max-age=31536000",
    val metadata: Map<String, String> = emptyMap(),
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L

        /**
         * upload에 허용되는 MIME type set입니다.
         *
         * SVG는 의도적으로 제외합니다. CDN이 permissive `Content-Type`으로 SVG를 반환하면
         * stored XSS vector가 만들어질 수 있기 때문입니다.
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
