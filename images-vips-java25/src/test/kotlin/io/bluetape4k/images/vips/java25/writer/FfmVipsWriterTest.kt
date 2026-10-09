package io.bluetape4k.images.vips.java25.writer

import app.photofox.vipsffm.VImage
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.images.vips.VipsEncodeOptions
import io.bluetape4k.images.vips.java25.AbstractFfmVipsTest
import io.bluetape4k.images.vips.java25.JPEG_MAGIC
import io.bluetape4k.images.vips.java25.PNG_MAGIC
import io.bluetape4k.images.vips.java25.WEBP_MARKER
import io.bluetape4k.images.vips.java25.WEBP_RIFF
import io.bluetape4k.images.vips.testfixtures.VipsTestFixtures
import io.bluetape4k.logging.KLogging
import org.junit.jupiter.api.Test
import java.lang.foreign.Arena

/**
 * vips-ffm writer object [FfmVipsJpegWriter], [FfmVipsPngWriter], [FfmVipsWebpWriter]의 unit test입니다.
 *
 * `internal` visibility에 접근하기 위해 writer와 같은 package에 test를 둡니다.
 * fixture는 trusted input이므로 test마다 shared `Arena.ofShared()`로 `VImage` instance를 직접 생성해
 * `ffmVipsImageOf` safety guard를 우회합니다.
 */
class FfmVipsWriterTest: AbstractFfmVipsTest() {

    companion object: KLogging()

    // ─── FfmVipsJpegWriter 검증 ───────────────────────────────────────────────

    @Test
    fun `FfmVipsJpegWriter writeToBytes returns non-empty bytes with JPEG magic`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        Arena.ofShared().use { arena ->
            val vImage = VImage.newFromBytes(arena, bytes)
            val result = FfmVipsJpegWriter.writeToBytes(vImage, VipsEncodeOptions.Default)
            result.size shouldBeGreaterThan 0
            result.startsWith(JPEG_MAGIC).shouldBeTrue()
        }
    }

    @Test
    fun `FfmVipsJpegWriter writeToBytes with high quality produces larger output than low quality`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        Arena.ofShared().use { arena ->
            val vImageLow = VImage.newFromBytes(arena, bytes)
            val vImageHigh = VImage.newFromBytes(arena, bytes)
            val lowResult = FfmVipsJpegWriter.writeToBytes(vImageLow, VipsEncodeOptions(quality = 20))
            val highResult = FfmVipsJpegWriter.writeToBytes(vImageHigh, VipsEncodeOptions(quality = 95))
            highResult.size shouldBeGreaterThan lowResult.size
        }
    }

    @Test
    fun `FfmVipsJpegWriter writeToBytes encodes PNG input as JPEG`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_PNG)
        Arena.ofShared().use { arena ->
            val vImage = VImage.newFromBytes(arena, bytes)
            val result = FfmVipsJpegWriter.writeToBytes(vImage, VipsEncodeOptions.Default)
            result.size shouldBeGreaterThan 0
            result.startsWith(JPEG_MAGIC).shouldBeTrue()
        }
    }

    // ─── FfmVipsPngWriter 검증 ────────────────────────────────────────────────

    @Test
    fun `FfmVipsPngWriter writeToBytes returns non-empty bytes with PNG magic`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_PNG)
        Arena.ofShared().use { arena ->
            val vImage = VImage.newFromBytes(arena, bytes)
            val result = FfmVipsPngWriter.writeToBytes(vImage, VipsEncodeOptions.Default)
            result.size shouldBeGreaterThan 0
            result.startsWith(PNG_MAGIC).shouldBeTrue()
        }
    }

    @Test
    fun `FfmVipsPngWriter writeToBytes with effort 9 produces smaller output than effort 1`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_PNG)
        Arena.ofShared().use { arena ->
            val vImageLow = VImage.newFromBytes(arena, bytes)
            val vImageHigh = VImage.newFromBytes(arena, bytes)
            val lowEffortResult = FfmVipsPngWriter.writeToBytes(vImageLow, VipsEncodeOptions(effort = 1))
            val highEffortResult = FfmVipsPngWriter.writeToBytes(vImageHigh, VipsEncodeOptions(effort = 9))
            lowEffortResult.size shouldBeGreaterThan highEffortResult.size
        }
    }

    @Test
    fun `FfmVipsPngWriter writeToBytes encodes JPEG input as PNG`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        Arena.ofShared().use { arena ->
            val vImage = VImage.newFromBytes(arena, bytes)
            val result = FfmVipsPngWriter.writeToBytes(vImage, VipsEncodeOptions.Default)
            result.size shouldBeGreaterThan 0
            result.startsWith(PNG_MAGIC).shouldBeTrue()
        }
    }

    // ─── FfmVipsWebpWriter 검증 ───────────────────────────────────────────────

    @Test
    fun `FfmVipsWebpWriter writeToBytes returns non-empty bytes with RIFF and WEBP markers`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_WEBP)
        Arena.ofShared().use { arena ->
            val vImage = VImage.newFromBytes(arena, bytes)
            val result = FfmVipsWebpWriter.writeToBytes(vImage, VipsEncodeOptions.Default)
            result.size shouldBeGreaterThan 0
            result.startsWith(WEBP_RIFF).shouldBeTrue()
            result.regionMatches(8, WEBP_MARKER).shouldBeTrue()
        }
    }

    @Test
    fun `FfmVipsWebpWriter writeToBytes with high quality produces larger output than low quality`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_WEBP)
        Arena.ofShared().use { arena ->
            val vImageLow = VImage.newFromBytes(arena, bytes)
            val vImageHigh = VImage.newFromBytes(arena, bytes)
            val lowResult = FfmVipsWebpWriter.writeToBytes(vImageLow, VipsEncodeOptions(quality = 20))
            val highResult = FfmVipsWebpWriter.writeToBytes(vImageHigh, VipsEncodeOptions(quality = 95))
            highResult.size shouldBeGreaterThan lowResult.size
        }
    }

    @Test
    fun `FfmVipsWebpWriter writeToBytes encodes JPEG input as WebP`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        Arena.ofShared().use { arena ->
            val vImage = VImage.newFromBytes(arena, bytes)
            val result = FfmVipsWebpWriter.writeToBytes(vImage, VipsEncodeOptions.Default)
            result.size shouldBeGreaterThan 0
            result.startsWith(WEBP_RIFF).shouldBeTrue()
            result.regionMatches(8, WEBP_MARKER).shouldBeTrue()
        }
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) if (this[i] != prefix[i]) return false
        return true
    }

    private fun ByteArray.regionMatches(offset: Int, other: ByteArray): Boolean {
        if (size < offset + other.size) return false
        for (i in other.indices) if (this[offset + i] != other[i]) return false
        return true
    }
}
