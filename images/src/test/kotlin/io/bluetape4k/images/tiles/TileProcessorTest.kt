package io.bluetape4k.images.tiles

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.images.AbstractImageTest
import io.bluetape4k.images.immutableImageOf
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.utils.Resourcex
import org.junit.jupiter.api.Test

class TileProcessorTest: AbstractImageTest() {

    private companion object: KLogging() {
        private const val TEST_PARALLELISM = 1
        private const val TEST_MAX_TILE_COUNT = 4096
        private const val TEST_TILE_WIDTH = 64
        private const val TEST_TILE_HEIGHT = 64
    }


    @Test
    fun `split and merge preserve image size`() {
        val image = immutableImageOf(Resourcex.getInputStream(CAFE_JPG).shouldNotBeNull())
        val processor = TileProcessor(maxTileCount = TEST_MAX_TILE_COUNT, parallelism = TEST_PARALLELISM)

        val tiles = processor.split(image, TileSize(TEST_TILE_WIDTH, TEST_TILE_HEIGHT))
        val merged = processor.merge(tiles, image.width, image.height)

        tiles.size shouldBeGreaterThan 1

        log.debug { "merged=$merged" }
        merged.width shouldBeEqualTo image.width
        merged.height shouldBeEqualTo image.height
    }

}
