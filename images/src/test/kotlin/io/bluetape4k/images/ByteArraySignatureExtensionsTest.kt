package io.bluetape4k.images

import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import org.junit.jupiter.api.Test

class ByteArraySignatureExtensionsTest {

    @Test
    fun `matches prefix using unsigned byte values`() {
        byteArrayOf(0x89.toByte(), 0x50, 0x4E)
            .hasPrefix(intArrayOf(0x89, 0x50))
            .shouldBeTrue()
    }

    @Test
    fun `accepts an empty prefix`() {
        byteArrayOf().hasPrefix(intArrayOf()).shouldBeTrue()
    }

    @Test
    fun `rejects a mismatch and a shorter input`() {
        byteArrayOf(0x89.toByte(), 0x51).hasPrefix(intArrayOf(0x89, 0x50)).shouldBeFalse()
        byteArrayOf(0x89.toByte()).hasPrefix(intArrayOf(0x89, 0x50)).shouldBeFalse()
    }

    @Test
    fun `rejects prefix values outside the unsigned byte range`() {
        byteArrayOf(0xFF.toByte()).hasPrefix(intArrayOf(-1)).shouldBeFalse()
        byteArrayOf(0x00).hasPrefix(intArrayOf(256)).shouldBeFalse()
    }

    @Test
    fun `identifies PNG header and rejects mismatch or truncation`() {
        val header = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        header.isPng().shouldBeTrue()
        header.copyOf().also { it[7] = 0 }.isPng().shouldBeFalse()
        header.copyOf(7).isPng().shouldBeFalse()
    }

    @Test
    fun `identifies JPEG prefix and rejects mismatch or truncation`() {
        val header = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        header.isJpeg().shouldBeTrue()
        byteArrayOf(0xFF.toByte(), 0xD8.toByte()).isJpeg().shouldBeFalse()
        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00).isJpeg().shouldBeFalse()
    }

    @Test
    fun `identifies WebP markers at their required offsets`() {
        val header = byteArrayOf(
            0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50,
        )
        header.isWebp().shouldBeTrue()
        header.copyOf(11).isWebp().shouldBeFalse()
        header.copyOf().also { it[0] = 0 }.isWebp().shouldBeFalse()
        header.copyOf().also { it[8] = 0 }.isWebp().shouldBeFalse()
        header.copyOf().also {
            it[4] = 0xFF.toByte()
            it[5] = 0xFF.toByte()
            it[6] = 0xFF.toByte()
            it[7] = 0xFF.toByte()
        }.isWebp().shouldBeTrue()
    }
}
