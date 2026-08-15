package io.bluetape4k.images.spring

import java.io.Serializable
import java.time.Instant

/**
 * image object body를 열지 않고 확인한 provider-neutral metadata입니다.
 *
 * ETag은 backend가 반환한 opaque entity tag이며 MD5 또는 content hash로 해석하지
 * 않습니다. [lastModified] 정밀도는 backend/filesystem이 제공하는 값에 따르며
 * sub-second 정밀도를 보장하지 않습니다. backend가 값을 제공하지 않는 field는 null입니다.
 */
data class ImageObjectMetadata(
    val key: ImageObjectKey,
    val sizeBytes: Long,
    val etag: String? = null,
    val contentType: String? = null,
    val lastModified: Instant? = null,
) : Serializable {

    init {
        require(sizeBytes >= 0) { "sizeBytes must be greater than or equal to 0." }
    }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
