package io.bluetape4k.images.vips.java21

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeLessOrEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotBeEmpty
import io.bluetape4k.images.vips.VipsEncodeException
import io.bluetape4k.images.vips.VipsEncodeOptions
import io.bluetape4k.images.vips.VipsImageFormat
import io.bluetape4k.images.vips.VipsImageFormat.AVIF
import io.bluetape4k.images.vips.VipsImageFormat.HEIC
import io.bluetape4k.images.vips.VipsImageFormat.JPEG
import io.bluetape4k.images.vips.VipsImageFormat.PNG
import io.bluetape4k.images.vips.VipsImageFormat.WEBP
import io.bluetape4k.images.vips.VipsIncubatingApi
import io.bluetape4k.images.vips.coroutines.suspendToBytes
import io.bluetape4k.images.vips.testfixtures.VipsTestFixtures
import io.bluetape4k.images.vips.testfixtures.assertWebpLosslessContract
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.KLogging
import io.bluetape4k.okio.asSource
import io.bluetape4k.okio.buffered
import io.bluetape4k.okio.coroutines.asSuspendedSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import io.bluetape4k.okio.coroutines.buffered as bufferedSuspended

@OptIn(VipsIncubatingApi::class)
class JVipsImageTest: AbstractJVipsTest() {

    private companion object: KLogging()

    @Test
    fun `public WebP lossless encoding preserves RGBA and default stays lossy`() {
        assertWebpLosslessContract { vipsImageOf(it) }
    }

    // ─── 1: load와 dimension 검증 ─────────────────────────────────────────

    @Test
    fun `vipsImageOf file returns correct dimensions`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        vipsImageOf(bytes).use { img ->
            img.width shouldBeEqualTo VipsTestFixtures.SAMPLE_JPEG_WIDTH
            img.height shouldBeEqualTo VipsTestFixtures.SAMPLE_JPEG_HEIGHT
        }
    }

    // ─── 2: resize 검증 ──────────────────────────────────────────────────

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

    // ─── 3: thumbnail 검증 ───────────────────────────────────────────────

    @Test
    fun `thumbnail 300 longest side is at most 300`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        vipsImageOf(bytes).use { img ->
            img.thumbnail(300).use { thumb ->
                maxOf(thumb.width, thumb.height) shouldBeLessOrEqualTo 300
            }
        }
    }

    // ─── 4: JPEG toBytes 검증 ────────────────────────────────────────────

    @Test
    fun `toBytes JPEG starts with JPEG magic bytes`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        vipsImageOf(bytes).use { img ->
            val output = img.toBytes(JPEG)
            output.shouldNotBeEmpty()
            output.startsWith(JPEG_MAGIC).shouldBeTrue()
        }
    }

    // ─── 5: PNG toBytes 검증 ─────────────────────────────────────────────

    @Test
    fun `toBytes PNG starts with PNG magic bytes`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_PNG)
        vipsImageOf(bytes).use { img ->
            val output = img.toBytes(PNG)
            output.shouldNotBeEmpty()
            output.startsWith(PNG_MAGIC).shouldBeTrue()
        }
    }

    // ─── 6: WebP toBytes 검증 ────────────────────────────────────────────

    @Test
    fun `toBytes WebP has RIFF and WEBP markers`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_WEBP)
        vipsImageOf(bytes).use { img ->
            val output = img.toBytes(WEBP)
            output.shouldNotBeEmpty()
            output.startsWith(WEBP_RIFF).shouldBeTrue()
            output.regionMatches(8, WEBP_MARKER).shouldBeTrue()
        }
    }

    @Test
    fun `toBytes AVIF is capability gated`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        vipsImageOf(bytes).use { img ->
            assertOptionalHeifFamilyEncoding(runCatching { img.toBytes(AVIF) }, AVIF)
        }
    }

    @Test
    fun `toBytes HEIC reports JVips backend unsupported`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        vipsImageOf(bytes).use { img ->
            val error = assertFailsWith<VipsEncodeException> { img.toBytes(HEIC) }
            error.message.orEmpty() shouldContain "HEIC encoding is not supported by the JVips backend"
        }
    }

    // ─── 7: suspendToBytes 검증 ─────────────────────────────────────────

    @Test
    fun `suspendToBytes JPEG produces non-empty bytes with JPEG magic`() = runTest {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        vipsImageOf(bytes).use { img ->
            val suspended = img.suspendToBytes(JPEG, VipsEncodeOptions.Default)
            suspended.shouldNotBeEmpty()
            suspended.startsWith(JPEG_MAGIC).shouldBeTrue()
        }
    }

    // ─── 8: use-close idempotency 검증 ──────────────────────────────────

    @Test
    fun `close called twice does not throw`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        val img = vipsImageOf(bytes)
        img.close()
        img.close() // must not throw
    }

    // ─── 9: use-after-close exception 검증 ──────────────────────────────

    @Test
    fun `operations after close throw IllegalStateException`(@TempDir tmpDir: Path) {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        val img = vipsImageOf(bytes)
        img.close()

        assertFailsWith<IllegalStateException> { img.resize(100, 100) }
        assertFailsWith<IllegalStateException> { img.thumbnail(100) }
        assertFailsWith<IllegalStateException> { img.crop(0, 0, 100, 100) }
        assertFailsWith<IllegalStateException> { img.toBytes(JPEG) }
        assertFailsWith<IllegalStateException> { img.writeTo(tmpDir.resolve("closed.jpg"), JPEG) }
        assertFailsWith<IllegalStateException> { img.writeTo(ByteArrayOutputStream(), JPEG) }
    }

    // ─── 10: crop exact dimension 검증 ──────────────────────────────────

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

    // ─── 11: Path writeTo 검증 ──────────────────────────────────────────

    @Test
    fun `writeTo path creates valid JPEG file`(@TempDir tmpDir: Path) {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        vipsImageOf(bytes).use { img ->
            val outPath = tmpDir.resolve("out.jpg")
            img.writeTo(outPath, JPEG)
            val written = outPath.toFile().readBytes()
            written.shouldNotBeEmpty()
            written.startsWith(JPEG_MAGIC).shouldBeTrue()
        }
    }

    // ─── 12: OutputStream writeTo 검증 ──────────────────────────────────

    @Test
    fun `writeTo OutputStream produces bytes with JPEG magic`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        vipsImageOf(bytes).use { img ->
            val baos = ByteArrayOutputStream()
            img.writeTo(baos, JPEG)
            val out = baos.toByteArray()
            out.shouldNotBeEmpty()
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

    // ─── 13: invalid resize args 검증 ───────────────────────────────────

    @Test
    fun `resize with zero width throws`() {
        val bytes = VipsTestFixtures.loadFixture(VipsTestFixtures.SAMPLE_JPEG)
        vipsImageOf(bytes).use { img ->
            assertFailsWith<Exception> { img.resize(0, 600) }
        }
    }

    // ─── 14: out-of-bounds crop 검증 ────────────────────────────────────

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

    // ─── 15: corrupt data VipsDecodeException 검증 ─────────────────────

    @Test
    fun `corrupt bytes throw VipsDecodeException on load`() {
        val corrupt = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00, 0x01, 0x02, 0x03)
        assertFailsWith<Exception> { vipsImageOf(corrupt) }
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────

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
            output.shouldNotBeEmpty()
            output.regionMatches(4, FTYP_MARKER).shouldBeTrue()
            when (format) {
                AVIF -> {
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
