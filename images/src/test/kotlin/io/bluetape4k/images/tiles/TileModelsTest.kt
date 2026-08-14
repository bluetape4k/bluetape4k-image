package io.bluetape4k.images.tiles

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.images.AbstractImageTest
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage

class TileModelsTest : AbstractImageTest() {

    private fun sampleImage(w: Int = 64, h: Int = 64): ImmutableImage {
        val buf = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        return ImmutableImage.fromAwt(buf)
    }

    // ── TileSize 검증 ──────────────────────────────────────────────────────

    @Test
    fun `TileSize stores width and height`() {
        val size = TileSize(256, 128)
        size.width shouldBeEqualTo 256
        size.height shouldBeEqualTo 128
    }

    @Test
    fun `TileSize rejects zero width`() {
        assertFailsWith<IllegalArgumentException> {
            TileSize(0, 128)
        }
    }

    @Test
    fun `TileSize rejects negative height`() {
        assertFailsWith<IllegalArgumentException> {
            TileSize(128, -1)
        }
    }

    @Test
    fun `TileSize accepts 1x1`() {
        val size = TileSize(1, 1)
        size.width shouldBeEqualTo 1
        size.height shouldBeEqualTo 1
    }

    // ── ImageTile 검증 ─────────────────────────────────────────────────────

    @Test
    fun `ImageTile stores position and image`() {
        val img = sampleImage(32, 32)
        val tile = ImageTile(x = 10, y = 20, width = 32, height = 32, image = img)

        tile.x shouldBeEqualTo 10
        tile.y shouldBeEqualTo 20
        tile.width shouldBeEqualTo 32
        tile.height shouldBeEqualTo 32
        tile.image shouldBeEqualTo img
    }

    @Test
    fun `ImageTile is a data class supporting equality`() {
        val img = sampleImage(16, 16)
        val tile1 = ImageTile(0, 0, 16, 16, img)
        val tile2 = ImageTile(0, 0, 16, 16, img)

        tile1 shouldBeEqualTo tile2
    }
}
