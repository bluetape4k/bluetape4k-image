package io.bluetape4k.images.vips.java25

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.images.vips.VipsDecodeException
import io.bluetape4k.images.vips.VipsLimits
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.WRITE

class FfmVipsImageSupportBoundaryTest {

    private companion object: KLogging() {
        val JPEG_MAGIC: ByteArray = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    }

    @TempDir
    private lateinit var tmpDir: Path

    @Test
    fun `path loader rejects oversized file before native decode`() {
        val oversized = tmpDir.resolve("oversized.jpg")
        log.debug { "Oversized: $oversized" }

        writeOversizedJpegLikeFile(oversized)

        val error = assertFailsWith<VipsDecodeException> {
            ffmVipsImageOf(oversized)
        }

        log.debug { "error message=${error.message}" }
        error.message shouldContain "exceeds"
    }

    private fun writeOversizedJpegLikeFile(path: Path) {
        Files.write(path, JPEG_MAGIC)
        Files.newByteChannel(path, WRITE).use { channel ->
            channel.position(VipsLimits.MAX_INPUT_BYTES)
            channel.write(ByteBuffer.wrap(byteArrayOf(0)))
        }
    }
}
