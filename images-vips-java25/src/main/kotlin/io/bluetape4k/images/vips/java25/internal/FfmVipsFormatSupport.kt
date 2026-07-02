package io.bluetape4k.images.vips.java25.internal

import io.bluetape4k.images.IncubatingImageApi
import io.bluetape4k.images.vips.VipsDecodeException
import io.bluetape4k.images.vips.VipsEncodeException
import io.bluetape4k.images.vips.VipsImageFormat
import io.bluetape4k.images.vips.java25.FfmVipsRuntime

@OptIn(IncubatingImageApi::class)
internal object FfmVipsFormatSupport {

    fun requireDecoding(format: VipsImageFormat) {
        if (!supportsDecoding(format)) {
            throw VipsDecodeException(
                "$format decoding is not supported by this libvips build. " +
                    "Install libvips with libheif/libaom support."
            )
        }
    }

    fun requireEncoding(format: VipsImageFormat) {
        if (!supportsEncoding(format)) {
            throw VipsEncodeException(
                "$format encoding is not supported by this libvips build. " +
                    "Install libvips with libheif/libaom support."
            )
        }
    }

    fun supportsDecoding(format: VipsImageFormat): Boolean =
        when (format) {
            VipsImageFormat.JPEG,
            VipsImageFormat.PNG,
            VipsImageFormat.WEBP -> true
            VipsImageFormat.AVIF,
            VipsImageFormat.HEIC -> supportsOperation("heifload_buffer")
        }

    fun supportsEncoding(format: VipsImageFormat): Boolean =
        when (format) {
            VipsImageFormat.JPEG,
            VipsImageFormat.PNG,
            VipsImageFormat.WEBP -> true
            VipsImageFormat.AVIF,
            VipsImageFormat.HEIC -> supportsOperation("heifsave_buffer")
        }

    private fun supportsOperation(name: String): Boolean =
        FfmVipsRuntime.codecProbe.supportsOperation(name)
}
