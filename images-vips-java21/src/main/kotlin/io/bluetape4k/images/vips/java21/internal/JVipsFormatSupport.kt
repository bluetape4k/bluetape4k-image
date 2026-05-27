package io.bluetape4k.images.vips.java21.internal

import io.bluetape4k.images.IncubatingImageApi
import io.bluetape4k.images.vips.VipsEncodeException
import io.bluetape4k.images.vips.VipsImageFormat

@OptIn(IncubatingImageApi::class)
internal object JVipsFormatSupport {

    fun requireEncoding(format: VipsImageFormat) {
        if (!supportsEncoding(format)) {
            throw VipsEncodeException(
                "$format encoding is not supported by the JVips backend. " +
                    "Use JPEG, PNG, WEBP, AVIF, or the java25 FFM backend for HEIC."
            )
        }
    }

    private fun supportsEncoding(format: VipsImageFormat): Boolean =
        when (format) {
            VipsImageFormat.JPEG,
            VipsImageFormat.PNG,
            VipsImageFormat.WEBP,
            VipsImageFormat.AVIF -> true
            VipsImageFormat.HEIC -> false
        }
}
