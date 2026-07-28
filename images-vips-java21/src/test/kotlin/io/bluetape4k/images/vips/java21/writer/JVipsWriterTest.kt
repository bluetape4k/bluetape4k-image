package io.bluetape4k.images.vips.java21.writer

import com.criteo.vips.VipsImage as NativeVipsImage
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.images.vips.VipsEncodeOptions
import io.bluetape4k.images.vips.java21.AbstractJVipsTest
import io.bluetape4k.images.vips.testfixtures.VipsTestFixtures
import org.junit.jupiter.api.Test

/**
 * JVips writer object [JVipsJpegWriter], [JVipsPngWriter], [JVipsWebpWriter]의 unit test입니다.
 *
 * `internal` visibility에 접근하기 위해 writer와 같은 package에 test를 둡니다.
 * fixture는 trusted input이므로 `vipsImageOf` safety guard를 우회하기 위해 native
 * `com.criteo.vips.VipsImage`를 직접 생성합니다.
 */
class JVipsWriterTest : AbstractJVipsTest() {

    companion object {
        private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
        private val WEBP_RIFF = byteArrayOf(0x52.toByte(), 0x49.toByte(), 0x46.toByte(), 0x46.toByte())
        private val WEBP_MARKER = byteArrayOf(0x57.toByte(), 0x45.toByte(), 0x42.toByte(), 0x50.toByte())
    }

    // ─── JVipsJpegWriter 검증 ─────────────────────────────────────────────────

    @Test
    fun `JVipsJpegWriter writeToBytes returns non-empty bytes with JPEG magic`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        val native = NativeVipsImage(bytes, bytes.size)
        try {
            val result = JVipsJpegWriter.writeToBytes(native, VipsEncodeOptions.Default)
            result.size shouldBeGreaterThan 0
            result.startsWith(JPEG_MAGIC).shouldBeTrue()
        } finally {
            native.release()
        }
    }

    @Test
    fun `JVipsJpegWriter writeToBytes with high quality produces larger output than low quality`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        val nativeLow = NativeVipsImage(bytes, bytes.size)
        val nativeHigh = NativeVipsImage(bytes, bytes.size)
        try {
            val lowQualityOptions = VipsEncodeOptions(quality = 20)
            val highQualityOptions = VipsEncodeOptions(quality = 95)
            val lowResult = JVipsJpegWriter.writeToBytes(nativeLow, lowQualityOptions)
            val highResult = JVipsJpegWriter.writeToBytes(nativeHigh, highQualityOptions)
            highResult.size shouldBeGreaterThan lowResult.size
        } finally {
            nativeLow.release()
            nativeHigh.release()
        }
    }

    @Test
    fun `JVipsJpegWriter writeToBytes encodes PNG input as JPEG`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_PNG)
        val native = NativeVipsImage(bytes, bytes.size)
        try {
            val result = JVipsJpegWriter.writeToBytes(native, VipsEncodeOptions.Default)
            result.size shouldBeGreaterThan 0
            result.startsWith(JPEG_MAGIC).shouldBeTrue()
        } finally {
            native.release()
        }
    }

    // ─── JVipsPngWriter 검증 ──────────────────────────────────────────────────

    @Test
    fun `JVipsPngWriter writeToBytes returns non-empty bytes with PNG magic`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_PNG)
        val native = NativeVipsImage(bytes, bytes.size)
        try {
            val result = JVipsPngWriter.writeToBytes(native, VipsEncodeOptions.Default)
            result.size shouldBeGreaterThan 0
            result.startsWith(PNG_MAGIC).shouldBeTrue()
        } finally {
            native.release()
        }
    }

    @Test
    fun `JVipsPngWriter writeToBytes with effort 9 produces smaller output than effort 1`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_PNG)
        val nativeLowEffort = NativeVipsImage(bytes, bytes.size)
        val nativeHighEffort = NativeVipsImage(bytes, bytes.size)
        try {
            val lowEffortOptions = VipsEncodeOptions(effort = 1)
            val highEffortOptions = VipsEncodeOptions(effort = 9)
            val lowEffortResult = JVipsPngWriter.writeToBytes(nativeLowEffort, lowEffortOptions)
            val highEffortResult = JVipsPngWriter.writeToBytes(nativeHighEffort, highEffortOptions)
            lowEffortResult.size shouldBeGreaterThan highEffortResult.size
        } finally {
            nativeLowEffort.release()
            nativeHighEffort.release()
        }
    }

    @Test
    fun `JVipsPngWriter writeToBytes encodes JPEG input as PNG`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        val native = NativeVipsImage(bytes, bytes.size)
        try {
            val result = JVipsPngWriter.writeToBytes(native, VipsEncodeOptions.Default)
            result.size shouldBeGreaterThan 0
            result.startsWith(PNG_MAGIC).shouldBeTrue()
        } finally {
            native.release()
        }
    }

    // ─── JVipsWebpWriter 검증 ─────────────────────────────────────────────────

    @Test
    fun `JVipsWebpWriter writeToBytes returns non-empty bytes with RIFF and WEBP markers`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_WEBP)
        val native = NativeVipsImage(bytes, bytes.size)
        try {
            val result = JVipsWebpWriter.writeToBytes(native, VipsEncodeOptions.Default)
            result.size shouldBeGreaterThan 0
            result.startsWith(WEBP_RIFF).shouldBeTrue()
            result.regionMatches(8, WEBP_MARKER).shouldBeTrue()
        } finally {
            native.release()
        }
    }

    @Test
    fun `JVipsWebpWriter writeToBytes with high quality produces larger output than low quality`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_WEBP)
        val nativeLow = NativeVipsImage(bytes, bytes.size)
        val nativeHigh = NativeVipsImage(bytes, bytes.size)
        try {
            val lowQualityOptions = VipsEncodeOptions(quality = 20)
            val highQualityOptions = VipsEncodeOptions(quality = 95)
            val lowResult = JVipsWebpWriter.writeToBytes(nativeLow, lowQualityOptions)
            val highResult = JVipsWebpWriter.writeToBytes(nativeHigh, highQualityOptions)
            highResult.size shouldBeGreaterThan lowResult.size
        } finally {
            nativeLow.release()
            nativeHigh.release()
        }
    }

    @Test
    fun `JVipsWebpWriter writeToBytes encodes JPEG input as WebP`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        val native = NativeVipsImage(bytes, bytes.size)
        try {
            val result = JVipsWebpWriter.writeToBytes(native, VipsEncodeOptions.Default)
            result.size shouldBeGreaterThan 0
            result.startsWith(WEBP_RIFF).shouldBeTrue()
            result.regionMatches(8, WEBP_MARKER).shouldBeTrue()
        } finally {
            native.release()
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
