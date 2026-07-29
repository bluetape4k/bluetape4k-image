package io.bluetape4k.images.vips.java21.writer

import com.criteo.vips.VipsException
import com.criteo.vips.VipsImage
import io.bluetape4k.images.vips.VipsEncodeException
import io.bluetape4k.images.vips.VipsEncodeOptions

/**
 * JVips AVIF encoder입니다.
 *
 * libheif/libaom support가 포함된 libvips build가 필요합니다.
 */
internal object JVipsAvifWriter {

    fun writeToBytes(image: VipsImage, options: VipsEncodeOptions): ByteArray {
        return try {
            image.writeAVIFToArray(options.quality, options.stripMetadata, options.effort)
        } catch (e: VipsException) {
            throw VipsEncodeException(
                "AVIF encoding failed. Ensure libvips was built with libheif and an AV1 encoder such as libaom.",
                e
            )
        }
    }
}
