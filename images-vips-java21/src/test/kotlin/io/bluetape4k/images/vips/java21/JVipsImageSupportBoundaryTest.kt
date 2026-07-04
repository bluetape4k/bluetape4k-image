package io.bluetape4k.images.vips.java21

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.images.vips.VipsDecodeException
import io.bluetape4k.images.vips.VipsLimits
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.WRITE

class JVipsImageSupportBoundaryTest {

    @Test
    fun `path loader rejects oversized file before native decode`(@TempDir tmpDir: Path) {
        val oversized = tmpDir.resolve("oversized.jpg")
        writeOversizedJpegLikeFile(oversized)

        val error = assertFailsWith<VipsDecodeException> {
            vipsImageOf(oversized)
        }

        error.message shouldContain "exceeds"
    }

    private fun writeOversizedJpegLikeFile(path: Path) {
        Files.write(path, JPEG_MAGIC)
        Files.newByteChannel(path, WRITE).use { channel ->
            channel.position(VipsLimits.MAX_INPUT_BYTES)
            channel.write(ByteBuffer.wrap(byteArrayOf(0)))
        }
    }

    private companion object {
        val JPEG_MAGIC: ByteArray = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    }
}
