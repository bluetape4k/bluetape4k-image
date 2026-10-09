package io.bluetape4k.images.examples.spring.model

import org.springframework.http.MediaType


internal fun extensionForContentType(contentType: String): String =
    when (contentType) {
        MediaType.IMAGE_JPEG_VALUE -> "jpg"
        MediaType.IMAGE_PNG_VALUE -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/avif" -> "avif"
        "image/heic" -> "heic"
        else -> throw IllegalArgumentException("Unsupported image content type: $contentType")
    }

internal fun contentTypeForName(name: String): String =
    when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> MediaType.IMAGE_JPEG_VALUE
        "png" -> MediaType.IMAGE_PNG_VALUE
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "avif" -> "image/avif"
        "heic" -> "image/heic"
        else -> MediaType.APPLICATION_OCTET_STREAM_VALUE
    }
