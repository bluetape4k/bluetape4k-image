package io.bluetape4k.images.examples.spring.intelligence.support

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.nio.JpegWriter
import com.sksamuel.scrimage.nio.PngWriter
import com.sksamuel.scrimage.webp.WebpWriter

internal fun pngBytes(width: Int = 40, height: Int = 30): ByteArray =
    ImmutableImage.create(width, height)
        .forWriter(PngWriter.MaxCompression)
        .bytes()

internal fun jpegBytes(width: Int = 40, height: Int = 30): ByteArray =
    ImmutableImage.create(width, height)
        .forWriter(JpegWriter.Default)
        .bytes()

internal fun webpBytes(width: Int = 40, height: Int = 30): ByteArray =
    ImmutableImage.create(width, height)
        .forWriter(WebpWriter.DEFAULT)
        .bytes()
