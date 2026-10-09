package io.bluetape4k.images.examples.spring.model

import io.bluetape4k.images.spring.ImageObjectKey
import java.io.Serializable

/**
 * local object key와 read URL을 포함한 upload result입니다.
 */
data class ImageUploadResponse(
    val original: StoredImageResponse,
    val thumbnail: StoredImageResponse,
    val originalBytes: Long,
    val thumbnailBytes: Long,
): java.io.Serializable {

    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * quickstart API가 반환하는 local storage object reference입니다.
 */
data class StoredImageResponse(
    val key: String,
    val url: String,
): java.io.Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L

        fun from(key: ImageObjectKey): StoredImageResponse =
            StoredImageResponse(
                key = key.fullKey,
                url = "/api/images/${key.fullKey}",
            )
    }
}

/**
 * invalid quickstart API request에 대한 error response입니다.
 */
data class ApiErrorResponse(
    val error: String,
    val message: String,
): Serializable {

    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}
