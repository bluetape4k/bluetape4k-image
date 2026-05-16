package io.bluetape4k.images.coroutines

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import org.junit.jupiter.api.Test

class TiffCompressionTest {

    @Test
    fun `TiffCompression has five entries`() {
        TiffCompression.entries.size shouldBeEqualTo 5
    }

    @Test
    fun `TiffCompression NONE has correct ioName`() {
        TiffCompression.NONE.ioName shouldBeEqualTo "None"
    }

    @Test
    fun `TiffCompression DEFLATE has correct ioName`() {
        TiffCompression.DEFLATE.ioName shouldBeEqualTo "Deflate"
    }

    @Test
    fun `TiffCompression LZW has correct ioName`() {
        TiffCompression.LZW.ioName shouldBeEqualTo "LZW"
    }

    @Test
    fun `TiffCompression PACKBITS has correct ioName`() {
        TiffCompression.PACKBITS.ioName shouldBeEqualTo "PackBits"
    }

    @Test
    fun `TiffCompression JPEG has correct ioName`() {
        TiffCompression.JPEG.ioName shouldBeEqualTo "JPEG"
    }

    @Test
    fun `all TiffCompression ioNames are non-blank`() {
        TiffCompression.entries.forEach { compression ->
            compression.ioName.isNotBlank().shouldBeTrue()
        }
    }
}
