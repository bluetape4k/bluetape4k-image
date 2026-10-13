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
}
