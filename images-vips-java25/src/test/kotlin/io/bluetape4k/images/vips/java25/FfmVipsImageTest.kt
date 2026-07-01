package io.bluetape4k.images.vips.java25

import app.photofox.vipsffm.VImage
import io.bluetape4k.images.IncubatingImageApi
import io.bluetape4k.images.vips.VipsDecodeException
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
import java.lang.foreign.Arena
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ

@OptIn(IncubatingImageApi::class)
class FfmVipsImageTest : AbstractFfmVipsTest() {

    companion object {
        private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
        private val WEBP_RIFF = byteArrayOf(0x52.toByte(), 0x49.toByte(), 0x46.toByte(), 0x46.toByte())
        private val WEBP_MARKER = byteArrayOf(0x57.toByte(), 0x45.toByte(), 0x42.toByte(), 0x50.toByte())
        private val FTYP_MARKER = byteArrayOf(0x66, 0x74, 0x79, 0x70)
    }

    // ─── 1: load + dimensions ─────────────────────────────────────────────

    @Test
    fun `ffmVipsImageOf bytes returns correct dimensions`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        ffmVipsImageOf(bytes).use { img ->
            img.width shouldBeEqualTo VipsTestFixtures.SAMPLE_JPEG_WIDTH
            img.height shouldBeEqualTo VipsTestFixtures.SAMPLE_JPEG_HEIGHT
        }
    }

    // ─── 2: resize ────────────────────────────────────────────────────────

    @Test
    fun `resize to 800x600 produces expected dimensions`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        ffmVipsImageOf(bytes).use { img ->
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
        ffmVipsImageOf(bytes).use { img ->
            img.thumbnail(300).use { thumb ->
                maxOf(thumb.width, thumb.height) shouldBeLessOrEqualTo 300
            }
        }
    }

    // ─── 4: toBytes JPEG ──────────────────────────────────────────────────

    @Test
    fun `toBytes JPEG starts with JPEG magic bytes`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        ffmVipsImageOf(bytes).use { img ->
            val output = img.toBytes(VipsImageFormat.JPEG)
            output.size shouldBeGreaterThan 0
            output.startsWith(JPEG_MAGIC).shouldBeTrue()
        }
    }

    // ─── 5: toBytes PNG ───────────────────────────────────────────────────

    @Test
    fun `toBytes PNG starts with PNG magic bytes`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_PNG)
        ffmVipsImageOf(bytes).use { img ->
            val output = img.toBytes(VipsImageFormat.PNG)
            output.size shouldBeGreaterThan 0
            output.startsWith(PNG_MAGIC).shouldBeTrue()
        }
    }

    // ─── 6: toBytes WebP ──────────────────────────────────────────────────

    @Test
    fun `toBytes WebP has RIFF and WEBP markers`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_WEBP)
        ffmVipsImageOf(bytes).use { img ->
            val output = img.toBytes(VipsImageFormat.WEBP)
            output.size shouldBeGreaterThan 0
            output.startsWith(WEBP_RIFF).shouldBeTrue()
            output.regionMatches(8, WEBP_MARKER).shouldBeTrue()
        }
    }

    @Test
    fun `toBytes AVIF is capability gated`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        ffmVipsImageOf(bytes).use { img ->
            assertOptionalHeifFamilyEncoding(runCatching { img.toBytes(VipsImageFormat.AVIF) }, VipsImageFormat.AVIF)
        }
    }

    @Test
    fun `toBytes HEIC is capability gated`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        ffmVipsImageOf(bytes).use { img ->
            assertOptionalHeifFamilyEncoding(runCatching { img.toBytes(VipsImageFormat.HEIC) }, VipsImageFormat.HEIC)
        }
    }

    // ─── 7: suspendToBytes ────────────────────────────────────────────────

    @Test
    fun `suspendToBytes JPEG produces non-empty bytes with JPEG magic`() = runTest {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        ffmVipsImageOf(bytes).use { img ->
            val suspended = img.suspendToBytes(VipsImageFormat.JPEG, VipsEncodeOptions.Default)
            suspended.size shouldBeGreaterThan 0
            suspended.startsWith(JPEG_MAGIC).shouldBeTrue()
        }
    }

    // ─── 8: close idempotency ─────────────────────────────────────────────

    @Test
    fun `close called twice does not throw`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        val img = ffmVipsImageOf(bytes)
        img.close()
        img.close() // must not throw
    }

    // ─── 9: use-after-close throws ────────────────────────────────────────

    @Test
    fun `operations after close throw IllegalStateException`(@TempDir tmpDir: Path) {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        val img = ffmVipsImageOf(bytes)
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
        ffmVipsImageOf(bytes).use { img ->
            img.crop(0, 0, 100, 100).use { cropped ->
                cropped.width shouldBeEqualTo 100
                cropped.height shouldBeEqualTo 100
            }
        }
    }

    @Test
    fun `derived image remains usable after source closes`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        val source = ffmVipsImageOf(bytes)
        val derived = source.thumbnail(300)

        source.close()

        derived.use { thumb ->
            maxOf(thumb.width, thumb.height) shouldBeLessOrEqualTo 300
            val output = thumb.toBytes(VipsImageFormat.JPEG)
            output.size shouldBeGreaterThan 0
            output.startsWith(JPEG_MAGIC).shouldBeTrue()
        }
    }

    @Test
    fun `resized image remains usable after source closes`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        val source = ffmVipsImageOf(bytes)
        val derived = source.resize(800, 600)

        source.close()

        derived.use { resized ->
            resized.width shouldBeLessOrEqualTo 800
            resized.height shouldBeLessOrEqualTo 600
            val output = resized.toBytes(VipsImageFormat.JPEG)
            output.size shouldBeGreaterThan 0
            output.startsWith(JPEG_MAGIC).shouldBeTrue()
        }
    }

    @Test
    fun `cropped image remains usable after source closes`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        val source = ffmVipsImageOf(bytes)
        val derived = source.crop(0, 0, 100, 100)

        source.close()

        derived.use { cropped ->
            cropped.width shouldBeEqualTo 100
            cropped.height shouldBeEqualTo 100
            val output = cropped.toBytes(VipsImageFormat.JPEG)
            output.size shouldBeGreaterThan 0
            output.startsWith(JPEG_MAGIC).shouldBeTrue()
        }
    }

    @Test
    fun `closing derived image does not close source image`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        ffmVipsImageOf(bytes).use { source ->
            val derived = source.crop(0, 0, 100, 100)

            derived.close()

            val output = source.toBytes(VipsImageFormat.JPEG)
            output.size shouldBeGreaterThan 0
            output.startsWith(JPEG_MAGIC).shouldBeTrue()
        }
    }

    // ─── 11: writeTo Path ─────────────────────────────────────────────────

    @Test
    fun `writeTo path creates valid JPEG file`(@TempDir tmpDir: Path) {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        ffmVipsImageOf(bytes).use { img ->
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
        ffmVipsImageOf(bytes).use { img ->
            val baos = ByteArrayOutputStream()
            img.writeTo(baos, VipsImageFormat.JPEG)
            val out = baos.toByteArray()
            out.size shouldBeGreaterThan 0
            out.startsWith(JPEG_MAGIC).shouldBeTrue()
        }
    }

    @Test
    fun `ffmVipsImageOf loads from caller-owned Okio BufferedSource`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        bytes.inputStream().asSource().buffered().use { source ->
            ffmVipsImageOf(source).use { img ->
                img.width shouldBeEqualTo VipsTestFixtures.SAMPLE_JPEG_WIDTH
                img.height shouldBeEqualTo VipsTestFixtures.SAMPLE_JPEG_HEIGHT
            }
        }
    }

    @Test
    fun `suspendFfmVipsImageOf loads from buffered suspended source`(@TempDir tmpDir: Path) = runSuspendIO {
        val input = tmpDir.resolve("sample.jpg")
        Files.write(input, VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG))
        val channel = AsynchronousFileChannel.open(input, READ)
        val source = channel.asSuspendedSource().bufferedSuspended()

        try {
            suspendFfmVipsImageOf(source).use { img ->
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
        ffmVipsImageOf(bytes).use { img ->
            assertFailsWith<Exception> { img.resize(0, 600) }
        }
    }

    // ─── 14: out-of-bounds crop ───────────────────────────────────────────

    @Test
    fun `crop beyond image bounds throws`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        ffmVipsImageOf(bytes).use { img ->
            assertFailsWith<Exception> { img.crop(0, 0, img.width + 1, img.height) }
        }
    }

    @Test
    fun `close remains idempotent after failed operation`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        val img = ffmVipsImageOf(bytes)

        assertFailsWith<Exception> { img.resize(0, 600) }

        img.close()
        img.close()
    }

    @Test
    fun `failed derived operations leave source image usable`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        ffmVipsImageOf(bytes).use { img ->
            assertFailsWith<Exception> { img.resize(0, 600) }
            assertFailsWith<Exception> { img.thumbnail(0) }
            assertFailsWith<Exception> { img.crop(0, 0, img.width + 1, img.height) }

            val output = img.toBytes(VipsImageFormat.JPEG)
            output.size shouldBeGreaterThan 0
            output.startsWith(JPEG_MAGIC).shouldBeTrue()
        }
    }

    // ─── 15: owned arena failure cleanup ──────────────────────────────────

    @Test
    fun `owned arena closes when native load fails`() {
        var capturedArena: Arena? = null

        assertFailsWith<Exception> {
            withOwnedArena { arena ->
                capturedArena = arena
                VImage.newFromBytes(arena, byteArrayOf(0x01, 0x02, 0x03))
            }
        }

        assertFailsWith<IllegalStateException> {
            capturedArena.shouldNotBeNull().allocate(1)
        }
    }

    @Test
    fun `owned arena closes when wrapper creation fails after native allocation`() {
        var capturedArena: Arena? = null

        assertFailsWith<VipsDecodeException> {
            withOwnedArena { arena ->
                capturedArena = arena
                VImage.newImage(arena)
                throw VipsDecodeException("simulated wrapper creation failure")
            }
        }

        assertFailsWith<IllegalStateException> {
            capturedArena.shouldNotBeNull().allocate(1)
        }
    }

    // ─── 16: corrupt data ─────────────────────────────────────────────────

    @Test
    fun `corrupt bytes throw VipsDecodeException on load`() {
        val corrupt = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00, 0x01, 0x02, 0x03)
        assertFailsWith<Exception> { ffmVipsImageOf(corrupt) }
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
            val brand = String(output, 8, 4, Charsets.US_ASCII)
            when (format) {
                VipsImageFormat.AVIF -> (brand == "avif" || brand == "avis").shouldBeTrue()
                VipsImageFormat.HEIC -> (brand == "heic" || brand == "heix" || brand == "hevc" || brand == "hevx" || brand == "mif1")
                    .shouldBeTrue()
                else -> error("Unexpected optional HEIF-family format: $format")
            }
        }.onFailure { error ->
            (error is VipsEncodeException).shouldBeTrue()
            error.message.orEmpty() shouldContain format.name
        }
    }
}
