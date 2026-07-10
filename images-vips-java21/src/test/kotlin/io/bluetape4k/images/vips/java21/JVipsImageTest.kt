package io.bluetape4k.images.vips.java21

import io.bluetape4k.images.vips.VipsIncubatingApi
import io.bluetape4k.images.vips.VipsEncodeException
import io.bluetape4k.images.vips.VipsEncodeOptions
import io.bluetape4k.images.vips.VipsImageFormat
import io.bluetape4k.images.vips.coroutines.suspendToBytes
import io.bluetape4k.images.vips.testfixtures.VipsTestFixtures
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.okio.asSource
import io.bluetape4k.okio.buffered
import io.bluetape4k.okio.coroutines.asSuspendedSource
import io.bluetape4k.okio.coroutines.buffered as bufferedSuspended
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldBeLessOrEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotBeNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ

@OptIn(VipsIncubatingApi::class)
class JVipsImageTest : AbstractJVipsTest() {

    companion object {
        private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
        private val WEBP_RIFF = byteArrayOf(0x52.toByte(), 0x49.toByte(), 0x46.toByte(), 0x46.toByte())
        private val WEBP_MARKER = byteArrayOf(0x57.toByte(), 0x45.toByte(), 0x42.toByte(), 0x50.toByte())
        private val FTYP_MARKER = byteArrayOf(0x66, 0x74, 0x79, 0x70)
    }

    // ─── 1: load + dimensions ─────────────────────────────────────────────

    @Test
    fun `vipsImageOf file returns correct dimensions`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        vipsImageOf(bytes).use { img ->
            img.width shouldBeEqualTo VipsTestFixtures.SAMPLE_JPEG_WIDTH
            img.height shouldBeEqualTo VipsTestFixtures.SAMPLE_JPEG_HEIGHT
        }
    }

    // ─── 2: resize ────────────────────────────────────────────────────────

    @Test
    fun `resize to 800x600 produces expected dimensions`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        vipsImageOf(bytes).use { img ->
            img.resize(800, 600).use { resized ->
                resized.width shouldBeLessOrEqualTo 800
                resized.height shouldBeLessOrEqualTo 600
            }
        }
    }

    // ─── 3: thumbnail ─────────────────────────────────────────────────────

    @Test
    fun `thumbnail 300 longest side is at most 300`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        vipsImageOf(bytes).use { img ->
            img.thumbnail(300).use { thumb ->
                maxOf(thumb.width, thumb.height) shouldBeLessOrEqualTo 300
            }
        }
    }

    // ─── 4: toBytes JPEG ──────────────────────────────────────────────────

    @Test
    fun `toBytes JPEG starts with JPEG magic bytes`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        vipsImageOf(bytes).use { img ->
            val output = img.toBytes(VipsImageFormat.JPEG)
            output.size shouldBeGreaterThan 0
            output.startsWith(JPEG_MAGIC).shouldBeTrue()
        }
    }

    // ─── 5: toBytes PNG ───────────────────────────────────────────────────

    @Test
    fun `toBytes PNG starts with PNG magic bytes`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_PNG)
        vipsImageOf(bytes).use { img ->
            val output = img.toBytes(VipsImageFormat.PNG)
            output.size shouldBeGreaterThan 0
            output.startsWith(PNG_MAGIC).shouldBeTrue()
        }
    }

    // ─── 6: toBytes WebP ──────────────────────────────────────────────────

    @Test
    fun `toBytes WebP has RIFF and WEBP markers`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_WEBP)
        vipsImageOf(bytes).use { img ->
            val output = img.toBytes(VipsImageFormat.WEBP)
            output.size shouldBeGreaterThan 0
            output.startsWith(WEBP_RIFF).shouldBeTrue()
            output.regionMatches(8, WEBP_MARKER).shouldBeTrue()
        }
    }

    @Test
    fun `toBytes AVIF is capability gated`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        vipsImageOf(bytes).use { img ->
            assertOptionalHeifFamilyEncoding(runCatching { img.toBytes(VipsImageFormat.AVIF) }, VipsImageFormat.AVIF)
        }
    }

    @Test
    fun `toBytes HEIC reports JVips backend unsupported`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        vipsImageOf(bytes).use { img ->
            val error = assertFailsWith<VipsEncodeException> { img.toBytes(VipsImageFormat.HEIC) }
            error.message.orEmpty() shouldContain "HEIC encoding is not supported by the JVips backend"
        }
    }

    // ─── 7: suspendToBytes ────────────────────────────────────────────────

    @Test
    fun `suspendToBytes JPEG produces non-empty bytes with JPEG magic`() = runTest {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        vipsImageOf(bytes).use { img ->
            val suspended = img.suspendToBytes(VipsImageFormat.JPEG, VipsEncodeOptions.Default)
            suspended.size shouldBeGreaterThan 0
            suspended.startsWith(JPEG_MAGIC).shouldBeTrue()
        }
    }

    // ─── 8: use-close idempotency ─────────────────────────────────────────

    @Test
    fun `close called twice does not throw`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        val img = vipsImageOf(bytes)
        img.close()
        img.close() // must not throw
    }

    // ─── 9: use-after-close throws ────────────────────────────────────────

    @Test
    fun `operations after close throw IllegalStateException`(@TempDir tmpDir: Path) {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        val img = vipsImageOf(bytes)
        img.close()

        assertFailsWith<IllegalStateException> { img.resize(100, 100) }
        assertFailsWith<IllegalStateException> { img.thumbnail(100) }
        assertFailsWith<IllegalStateException> { img.crop(0, 0, 100, 100) }
        assertFailsWith<IllegalStateException> { img.toBytes(VipsImageFormat.JPEG) }
        assertFailsWith<IllegalStateException> { img.writeTo(tmpDir.resolve("closed.jpg"), VipsImageFormat.JPEG) }
        assertFailsWith<IllegalStateException> {
            img.writeTo(ByteArrayOutputStream(), VipsImageFormat.JPEG)
        }
    }

    // ─── 10: crop exact dimensions ────────────────────────────────────────

    @Test
    fun `crop 0 0 100 100 returns 100x100`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        vipsImageOf(bytes).use { img ->
            img.crop(0, 0, 100, 100).use { cropped ->
                cropped.width shouldBeEqualTo 100
                cropped.height shouldBeEqualTo 100
            }
        }
    }

    // ─── 11: writeTo Path ─────────────────────────────────────────────────

    @Test
    fun `writeTo path creates valid JPEG file`(@TempDir tmpDir: Path) {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        vipsImageOf(bytes).use { img ->
            val outPath = tmpDir.resolve("out.jpg")
            img.writeTo(outPath, VipsImageFormat.JPEG)
            val written = outPath.toFile().readBytes()
            written.size shouldBeGreaterThan 0
            written.startsWith(JPEG_MAGIC).shouldBeTrue()
        }
    }

    // ─── 12: writeTo OutputStream ─────────────────────────────────────────

    @Test
    fun `writeTo OutputStream produces bytes with JPEG magic`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        vipsImageOf(bytes).use { img ->
            val baos = ByteArrayOutputStream()
            img.writeTo(baos, VipsImageFormat.JPEG)
            val out = baos.toByteArray()
            out.size shouldBeGreaterThan 0
            out.startsWith(JPEG_MAGIC).shouldBeTrue()
        }
    }

    @Test
    fun `vipsImageOf loads from caller-owned Okio BufferedSource`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        bytes.inputStream().asSource().buffered().use { source ->
            vipsImageOf(source).use { img ->
                img.width shouldBeEqualTo VipsTestFixtures.SAMPLE_JPEG_WIDTH
                img.height shouldBeEqualTo VipsTestFixtures.SAMPLE_JPEG_HEIGHT
            }
        }
    }

    @Test
    fun `suspendVipsImageOf loads from buffered suspended source`(@TempDir tmpDir: Path) = runSuspendIO {
        val input = tmpDir.resolve("sample.jpg")
        Files.write(input, VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG))
        val channel = AsynchronousFileChannel.open(input, READ)
        val source = channel.asSuspendedSource().bufferedSuspended()

        try {
            suspendVipsImageOf(source).use { img ->
                img.width shouldBeEqualTo VipsTestFixtures.SAMPLE_JPEG_WIDTH
                img.height shouldBeEqualTo VipsTestFixtures.SAMPLE_JPEG_HEIGHT
            }
            channel.isOpen.shouldBeTrue()
        } finally {
            source.close()
            channel.close()
        }
    }

    // ─── 13: invalid resize args ──────────────────────────────────────────

    @Test
    fun `resize with zero width throws`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        vipsImageOf(bytes).use { img ->
            assertFailsWith<Exception> { img.resize(0, 600) }
        }
    }

    // ─── 14: out-of-bounds crop ───────────────────────────────────────────

    @Test
    fun `crop beyond image bounds throws`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        vipsImageOf(bytes).use { img ->
            assertFailsWith<Exception> { img.crop(0, 0, img.width + 1, img.height) }
        }
    }

    @Test
    fun `close remains idempotent after failed operation`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        val img = vipsImageOf(bytes)

        assertFailsWith<Exception> { img.resize(0, 600) }

        img.close()
        img.close()
    }

    // ─── 15: corrupt data throws VipsDecodeException ──────────────────────

    @Test
    fun `corrupt bytes throw VipsDecodeException on load`() {
        val corrupt = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00, 0x01, 0x02, 0x03)
        assertFailsWith<Exception> { vipsImageOf(corrupt) }
    }

    // ─── helpers ──────────────────────────────────────────────────────────

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

    private fun assertOptionalHeifFamilyEncoding(result: Result<ByteArray>, format: VipsImageFormat) {
        result.onSuccess { output ->
            output.size shouldBeGreaterThan 0
            output.regionMatches(4, FTYP_MARKER).shouldBeTrue()
            when (format) {
                VipsImageFormat.AVIF -> {
                    val brand = String(output, 8, 4, Charsets.US_ASCII)
                    (brand == "avif" || brand == "avis").shouldBeTrue()
                }
                else -> error("Unexpected optional HEIF-family format: $format")
            }
        }.onFailure { error ->
            (error is VipsEncodeException).shouldBeTrue()
            error.message.orEmpty() shouldContain format.name
        }
    }
}
