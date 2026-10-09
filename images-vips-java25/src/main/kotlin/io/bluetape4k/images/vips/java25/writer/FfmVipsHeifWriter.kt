package io.bluetape4k.images.vips.java25.writer

import app.photofox.vipsffm.VImage
import app.photofox.vipsffm.VipsError
import app.photofox.vipsffm.VipsOption
import app.photofox.vipsffm.enums.VipsForeignHeifCompression
import io.bluetape4k.images.vips.VipsEncodeException
import io.bluetape4k.images.vips.VipsEncodeOptions
import io.bluetape4k.images.vips.VipsImageFormat
import io.bluetape4k.images.vips.VipsImageFormat.AVIF
import io.bluetape4k.images.vips.VipsImageFormat.HEIC
import io.bluetape4k.images.vips.VipsIncubatingApi

/**
 * AVIF와 HEIC용 vips-ffm HEIF-family encoder입니다.
 */
@OptIn(VipsIncubatingApi::class)
internal object FfmVipsHeifWriter {

    fun writeToBytes(image: VImage, format: VipsImageFormat, options: VipsEncodeOptions): ByteArray {
        val compression = when (format) {
            AVIF -> VipsForeignHeifCompression.FOREIGN_HEIF_COMPRESSION_AV1
            HEIC -> VipsForeignHeifCompression.FOREIGN_HEIF_COMPRESSION_HEVC
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
