package io.bluetape4k.images.vips.testfixtures

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.images.vips.VipsEncodeOptions
import io.bluetape4k.images.vips.VipsImage
import io.bluetape4k.images.vips.VipsImageFormat
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

private const val COLOR_STEP = 31
private const val ALPHA_SHIFT = 24
private const val FOURCC_BYTES = 4
private const val RIFF_HEADER_BYTES = 12
private const val CHUNK_HEADER_BYTES = 8

/** 두 native backend의 공개 인코딩 경로에서 무손실 모드와 RGBA 보존을 검증합니다. */
fun assertWebpLosslessContract(open: (ByteArray) -> VipsImage) {
    val source = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until source.height) {
        for (x in 0 until source.width) {
            val alpha = if (x == 0 && y == 0) 0 else 64 + (x + y) % 4 * 63
            val rgb = if (alpha == 0) 0 else
                ((x * COLOR_STEP) shl 16) or ((y * COLOR_STEP) shl 8) or ((x xor y) * COLOR_STEP)
            source.setRGB(x, y, (alpha shl ALPHA_SHIFT) or rgb)
        }
    }
    val input = ByteArrayOutputStream().use { output ->
        ImageIO.write(source, "png", output)
        output.toByteArray()
    }
    open(input).use { image ->
        val encoded = image.toBytes(VipsImageFormat.WEBP, VipsEncodeOptions(lossless = true))
        webpChunks(encoded) shouldContain "VP8L"
        val decoded = open(encoded).use { it.toBytes(VipsImageFormat.PNG) }
        val actual = decoded.inputStream().use { ImageIO.read(it) }
        actual.width shouldBeEqualTo source.width
        actual.height shouldBeEqualTo source.height
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                actual.getRGB(x, y) shouldBeEqualTo source.getRGB(x, y)
            }
        }
        webpChunks(image.toBytes(VipsImageFormat.WEBP)) shouldContain "VP8 "
    }
}

private fun webpChunks(bytes: ByteArray): List<String> {
    val chunks = mutableListOf<String>()
    var offset = RIFF_HEADER_BYTES
    while (offset + CHUNK_HEADER_BYTES <= bytes.size) {
        chunks += bytes.copyOfRange(offset, offset + FOURCC_BYTES).toString(Charsets.US_ASCII)
        val size = (0..3).sumOf { index ->
            (bytes[offset + FOURCC_BYTES + index].toLong() and 255L) shl (index * 8)
        }
        val next = offset.toLong() + CHUNK_HEADER_BYTES + size + (size and 1)
        check(next <= bytes.size) { "WebP chunk exceeds encoded body" }
        offset = next.toInt()
    }
    return chunks
}
