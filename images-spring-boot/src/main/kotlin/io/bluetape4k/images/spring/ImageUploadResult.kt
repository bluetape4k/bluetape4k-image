package io.bluetape4k.images.spring

import java.io.Serializable
import java.time.Instant

/**
 * 성공한 image upload operation의 result입니다.
 *
 * ## 동작
 * - [key]는 image가 저장된 위치를 식별합니다.
 * - [etag]는 backend가 반환한 opaque entity tag입니다. S3 multipart object의 ETag처럼
 *   MD5 또는 content hash가 아닐 수 있으므로 문자열을 해석하거나 정규화하지 않습니다.
 * - [sizeBytes]는 저장된 byte 수입니다.
 * - [contentType]은 저장된 image의 MIME type입니다.
 * - [uploadedAt]은 upload 완료 시각을 기록합니다. 기본값은 [Instant.now]입니다.
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
