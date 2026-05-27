package io.bluetape4k.images.vips.java25.writer

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VipsError
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsForeignHeifCompression
import io.bluetape4k.images.IncubatingImageApi
import io.bluetape4k.images.vips.VipsEncodeException
import io.bluetape4k.images.vips.VipsEncodeOptions
import io.bluetape4k.images.vips.VipsImageFormat

/**
 * vips-ffm HEIF-family encoder for AVIF and HEIC.
 */
@OptIn(IncubatingImageApi::class)
internal object FfmVipsHeifWriter {

    fun writeToBytes(image: VImage, format: VipsImageFormat, options: VipsEncodeOptions): ByteArray {
        val compression = when (format) {
            VipsImageFormat.AVIF -> VipsForeignHeifCompression.FOREIGN_HEIF_COMPRESSION_AV1
            VipsImageFormat.HEIC -> VipsForeignHeifCompression.FOREIGN_HEIF_COMPRESSION_HEVC
            else -> throw VipsEncodeException("Unsupported HEIF-family format for encoding: $format")
        }

        return try {
            val blob = image.heifsaveBuffer(
                VipsOption.Int("Q", options.quality),
                VipsOption.Int("effort", options.effort),
                VipsOption.Boolean("lossless", options.lossless),
                VipsOption.Boolean("strip", options.stripMetadata),
                VipsOption.Enum("compression", compression),
            )
            val buf = blob.asClonedByteBuffer()
            ByteArray(buf.remaining()).also { buf.get(it) }
        } catch (e: VipsError) {
            throw VipsEncodeException(
                "$format encoding failed. Ensure libvips was built with libheif and the required encoder.",
                e
            )
        }
    }
}
