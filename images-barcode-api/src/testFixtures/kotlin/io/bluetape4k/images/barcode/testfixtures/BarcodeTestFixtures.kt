package io.bluetape4k.images.barcode.testfixtures

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.images.ImageDimensions
import java.awt.Color
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage

/**
 * provider test가 공유하는 deterministic barcode fixture helper입니다.
 *
 * 이 helper들은 test runtime에 이미지를 생성합니다. 외부 image asset을 포함하지 않으므로
 * 추적해야 할 third-party image license가 없습니다.
 */
object BarcodeTestFixtures {

    /**
     * 생성된 barcode fixture의 license/source note입니다.
     */
    const val GENERATED_SOURCE_NOTE: String =
        "Generated at test runtime from deterministic code; no external image asset."

    /**
     * 의도적으로 인코딩 이미지가 아니게 만든 byte sequence입니다.
     */
    val malformedImageBytes: ByteArray =
        "not-an-image".toByteArray(Charsets.UTF_8)

    /**
     * barcode content가 없는 plain white image를 생성합니다.
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
     * white background를 보존하면서 이미지를 시계 방향으로 회전합니다.
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
