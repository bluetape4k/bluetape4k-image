package io.bluetape4k.images.tiles

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import com.sksamuel.scrimage.ImmutableImage
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage

class TileBoundsTest {

    private val image = ImmutableImage.wrapAwt(BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB).apply {
        setRGB(0, 0, Color.RED.rgb)
    })
    private val processor = TileProcessor()

    @Test
    fun `maximum tile width and height produce one small tile`() {
        val tiles = processor.split(image, TileSize(Int.MAX_VALUE, Int.MAX_VALUE))
        tiles.size shouldBeEqualTo 1
        tiles.single().width shouldBeEqualTo 2
        tiles.single().height shouldBeEqualTo 1
    }

    @Test
    fun `merge rejects overflowing coordinates before drawing`() {
        for (tile in listOf(
            ImageTile(Int.MAX_VALUE, 0, 2, 1, image),
            ImageTile(0, Int.MAX_VALUE, 2, 1, image),
            ImageTile(-1, 0, 2, 1, image),
            ImageTile(0, -1, 2, 1, image),
        )) {
            assertFailsWith<IllegalArgumentException> { processor.merge(listOf(tile), 2, 1) }
        }
    }

    @Test
    fun `merge rejects dimensions that differ from the actual tile`() {
        for (tile in listOf(
            ImageTile(0, 0, 1, 1, image),
            ImageTile(0, 0, 2, 0, image),
            ImageTile(0, 0, -1, 1, image),
        )) {
            assertFailsWith<IllegalArgumentException> { processor.merge(listOf(tile), 2, 1) }
        }
    }

    @Test
    fun `exact boundary preserves source pixels`() {
        val merged = processor.merge(processor.split(image, TileSize(2, 1)), 2, 1)
        merged.width shouldBeEqualTo image.width
        merged.height shouldBeEqualTo image.height
        for (x in 0 until image.width) {
            merged.awt().getRGB(x, 0) shouldBeEqualTo image.awt().getRGB(x, 0)
        }
    }
}
