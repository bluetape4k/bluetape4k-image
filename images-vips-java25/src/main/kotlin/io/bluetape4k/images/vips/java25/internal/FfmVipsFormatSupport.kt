package io.bluetape4k.images.vips.java25.internal

import app.photofox.vipsffm.jextract.VipsRaw
import io.bluetape4k.images.IncubatingImageApi
import io.bluetape4k.images.vips.VipsDecodeException
import io.bluetape4k.images.vips.VipsEncodeException
import io.bluetape4k.images.vips.VipsImageFormat
import java.lang.foreign.Arena

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
        runCatching {
            Arena.ofConfined().use { arena ->
                VipsRaw.vips_type_find(
                    arena.allocateFrom("VipsOperation"),
                    arena.allocateFrom(name),
                ) != 0L
            }
        }.getOrDefault(false)
}
