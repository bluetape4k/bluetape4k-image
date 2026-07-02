package io.bluetape4k.images.barcode.testfixtures

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.images.ImageDimensions
import java.awt.Color
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage

/**
 * Deterministic barcode fixture helpers shared by provider tests.
 *
 * These helpers generate images at test runtime. They do not embed external
 * image assets, so there is no third-party image license to track.
 */
object BarcodeTestFixtures {

    /**
     * License/source note for generated barcode fixtures.
     */
    const val GENERATED_SOURCE_NOTE: String =
        "Generated at test runtime from deterministic code; no external image asset."

    /**
     * Byte sequence that is intentionally not an encoded image.
     */
    val malformedImageBytes: ByteArray =
        "not-an-image".toByteArray(Charsets.UTF_8)

    /**
     * Creates a plain white image with no barcode content.
     */
    fun blankImage(
        dimensions: ImageDimensions = ImageDimensions(width = 180, height = 120),
    ): ImmutableImage {
        val buffered = BufferedImage(dimensions.width, dimensions.height, BufferedImage.TYPE_INT_RGB)
        val graphics = buffered.createGraphics()
        try {
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, buffered.width, buffered.height)
        } finally {
            graphics.dispose()
        }
        return ImmutableImage.fromAwt(buffered)
    }

    /**
     * Rotates an image clockwise while preserving a white background.
     */
    fun rotateClockwise(image: ImmutableImage): ImmutableImage {
        val source = image.awt()
        val rotated = BufferedImage(source.height, source.width, BufferedImage.TYPE_INT_RGB)
        val graphics = rotated.createGraphics()
        try {
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, rotated.width, rotated.height)
            val transform = AffineTransform()
            transform.translate(source.height.toDouble(), 0.0)
            transform.rotate(Math.PI / 2.0)
            graphics.drawImage(source, transform, null)
        } finally {
            graphics.dispose()
        }
        return ImmutableImage.fromAwt(rotated)
    }
}
