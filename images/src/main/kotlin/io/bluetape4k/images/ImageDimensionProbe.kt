package io.bluetape4k.images

import io.bluetape4k.support.requirePositiveNumber
import java.io.ByteArrayInputStream
import java.io.Serializable
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.imageio.stream.ImageInputStream

/**
 * Header-derived dimensions for the first image frame.
 *
 * The dimensions are intended for validation before expensive full-image
 * decode, thumbnail generation, OCR, or native processing work.
 *
 * Example:
 * ```kotlin
 * val dimensions = probeImageDimensions(uploadBytes)
 * dimensions?.requireMaxPixels(16_777_216)
 * dimensions?.requireMaxSide(8_192)
 * ```
 */
data class ImageDimensions(
    val width: Int,
    val height: Int,
) : Serializable {

    init {
        width.requirePositiveNumber("width")
        height.requirePositiveNumber("height")
    }

    /**
     * Total first-frame pixel count as `width * height`.
     */
    val pixelCount: Long
        get() = width.toLong() * height.toLong()

    /**
     * Fails when this image exceeds the configured decoded pixel budget.
     */
    fun requireMaxPixels(maxPixels: Long, subject: String = "image"): ImageDimensions {
        maxPixels.requirePositiveNumber("maxPixels")
        require(pixelCount <= maxPixels) {
            "$subject decodedPixels=$pixelCount exceeds maxInputPixels=$maxPixels (dimensions=${width}x$height)."
        }
        return this
    }

    /**
     * Fails when either decoded side exceeds the configured side budget.
     */
    fun requireMaxSide(maxSide: Int, subject: String = "image"): ImageDimensions {
        maxSide.requirePositiveNumber("maxSide")
        require(width <= maxSide && height <= maxSide) {
            "$subject decodedDimensions=${width}x$height exceeds maxInputSide=$maxSide."
        }
        return this
    }

    private companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Reads first-frame dimensions from encoded image bytes without decoding pixels.
 *
 * This probes the image header through ImageIO readers and does not allocate a
 * full pixel buffer. Use it as an early safety check for untrusted uploads.
 *
 * Returns `null` when ImageIO cannot identify a reader for the payload.
 */
fun probeImageDimensions(bytes: ByteArray): ImageDimensions? {
    val input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)) ?: return null
    return input.use(::probeImageDimensions)
}

/**
 * Reads first-frame dimensions from an image path without decoding pixels.
 *
 * This probes the image header through ImageIO readers and does not allocate a
 * full pixel buffer. Use it as an early safety check for batch inputs.
 *
 * Returns `null` when ImageIO cannot identify a reader for the file.
 */
fun probeImageDimensions(path: Path): ImageDimensions? {
    val input = ImageIO.createImageInputStream(path.toFile()) ?: return null
    return input.use(::probeImageDimensions)
}

/**
 * Reads the first-frame pixel count from encoded image bytes.
 */
fun probeImagePixelCount(bytes: ByteArray): Long? =
    probeImageDimensions(bytes)?.pixelCount

/**
 * Reads the first-frame pixel count from an image path.
 */
fun probeImagePixelCount(path: Path): Long? =
    probeImageDimensions(path)?.pixelCount

private fun probeImageDimensions(input: ImageInputStream): ImageDimensions? {
    val readers = ImageIO.getImageReaders(input)
    if (!readers.hasNext()) {
        return null
    }

    val reader = readers.next()
    try {
        input.seek(0)
        reader.input = input
        return ImageDimensions(
            width = reader.getWidth(0),
            height = reader.getHeight(0),
        )
    } finally {
        reader.dispose()
    }
}
