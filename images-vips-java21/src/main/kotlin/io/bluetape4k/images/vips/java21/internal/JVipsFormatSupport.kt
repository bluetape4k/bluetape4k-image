package io.bluetape4k.images.vips.java21.internal

import io.bluetape4k.images.vips.VipsEncodeException
import io.bluetape4k.images.vips.VipsImageFormat
import io.bluetape4k.images.vips.VipsImageFormat.AVIF
import io.bluetape4k.images.vips.VipsImageFormat.HEIC
import io.bluetape4k.images.vips.VipsImageFormat.JPEG
import io.bluetape4k.images.vips.VipsImageFormat.PNG
import io.bluetape4k.images.vips.VipsImageFormat.WEBP
import io.bluetape4k.images.vips.VipsIncubatingApi

@OptIn(VipsIncubatingApi::class)
internal object JVipsFormatSupport {

    fun requireEncoding(format: VipsImageFormat) {
        if (!supportsEncoding(format)) {
            throw VipsEncodeException(
                "$format encoding is not supported by the JVips backend. " +
                        "Use JPEG, PNG, WEBP, AVIF, or the java25 FFM backend for HEIC."
            )
        }
    }

    private fun supportsEncoding(format: VipsImageFormat): Boolean = when (format) {
        JPEG, PNG, WEBP, AVIF -> true
        HEIC -> false
    }
}
