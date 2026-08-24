package io.bluetape4k.images

import com.sksamuel.scrimage.webp.WebpWriter
import io.bluetape4k.images.coroutines.SuspendJpegWriter
import io.bluetape4k.images.coroutines.SuspendPngWriter
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.junit5.tempfolder.TempFolder
import io.bluetape4k.junit5.tempfolder.TempFolderTest
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.okio.asSource
import io.bluetape4k.okio.buffered
import io.bluetape4k.okio.coroutines.asSuspendedSink
import io.bluetape4k.okio.coroutines.asSuspendedSource
import io.bluetape4k.okio.coroutines.buffered as bufferedSuspended
import kotlinx.coroutines.test.runTest
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldNotBeEqualTo
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.assertions.shouldBeTrue
import okio.Buffer
import okio.Sink
import okio.Source
import okio.Timeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE

@TempFolderTest
class ImmutableImageSupportTest: AbstractImageTest() {

    companion object: KLoggingChannel()

    private val useTempFile = true

    @ParameterizedTest(name = "load write coroutines: {0}.jpg")
    @MethodSource("getImageFileNames")
    fun `load and write jpg image async`(filename: String, tempFolder: TempFolder) = runTest {
        val image =
            suspendLoadImage(Path.of("${BASE_PATH}/$filename.jpg"))

        if (useTempFile) {
            image.forSuspendWriter(SuspendJpegWriter.Default).write(tempFolder.createFile().toPath())
        } else {
            image.forSuspendWriter(SuspendJpegWriter.Default)
                .write(Path.of("${BASE_PATH}/${filename}_async.jpg"))
        }
    }

    @ParameterizedTest(name = "load write coroutines: {0}.png")
    @MethodSource("getImageFileNames")
    fun `load and write png image async`(filename: String, tempFolder: TempFolder) = runTest {
        val image =
            suspendLoadImage(Path.of("${BASE_PATH}/$filename.png"))

        if (useTempFile) {
            image.forSuspendWriter(SuspendPngWriter.MaxCompression).write(tempFolder.createFile().toPath())
        } else {
            image.forSuspendWriter(SuspendPngWriter.MaxCompression)
                .write(Path.of("${BASE_PATH}/${filename}_async.png"))
        }
    }

    @Test
    fun `withGraphics는 원본 이미지를 변경하지 않는다`() {
        val original = immutableImageOf(whiteTestImage(10, 10))
        val originalRgb = original.awt().getRGB(0, 0)

        val result = original.withGraphics { g ->
            g.color = Color.RED
            g.fillRect(0, 0, 10, 10)
        }

        // 원본은 흰색 유지
        original.awt().getRGB(0, 0) shouldBeEqualTo originalRgb
        // 반환된 복사본은 빨간색
        result.awt().getRGB(0, 0) shouldNotBeEqualTo originalRgb
    }

    @Test
    fun `withGraphics는 수신 객체와 다른 인스턴스를 반환한다`() {
        val original = immutableImageOf(whiteTestImage(10, 10))

        val result = original.withGraphics { }

        result.shouldNotBeNull()
        result.width shouldBeEqualTo original.width
        // 별도 복사본이므로 픽셀 버퍼가 독립적
        result.awt() shouldNotBeEqualTo original.awt()
    }

    @Test
    fun `withGraphics는 action이 예외를 던져도 Graphics2D를 dispose하고 원본을 반환 가능하다`() {
        val original = immutableImageOf(whiteTestImage(10, 10))

        assertFailsWith<RuntimeException> {
            original.withGraphics { throw RuntimeException("test error") }
        }

        // 예외 발생 후에도 원본 이미지가 정상 사용 가능 (Graphics2D dispose됨)
        original.width shouldBeGreaterThan 0
        original.awt().getRGB(0, 0).let { /* 픽셀 읽기 성공 */ }
    }

    @Test
    fun `load image from Okio BufferedSource`() {
        Path.of("$BASE_PATH/homer.jpg").toFile().inputStream().asSource().buffered().use { source ->
            val image = immutableImageOf(source)

            image.width shouldBeGreaterThan 0
            image.height shouldBeGreaterThan 0
        }
    }

    @Test
    fun `load large generated image from Okio Source closes owned source`() {
        val source = TrackingSource(whiteTestImage(1024, 768))

        val image = immutableImageOf(source)

        image.width shouldBeEqualTo 1024
        image.height shouldBeEqualTo 768
        source.closed.shouldBeTrue()
    }

    @Test
    fun `load large generated image from BufferedSource keeps caller ownership`() {
        val source = TrackingSource(whiteTestImage(1024, 768))
        val bufferedSource = source.buffered()

        val image = immutableImageOf(bufferedSource)

        image.width shouldBeEqualTo 1024
        image.height shouldBeEqualTo 768
        source.closed shouldBeEqualTo false
        bufferedSource.close()
    }

    @Test
    fun `bounded byte array loader rejects encoded payload over max bytes`() {
        val bytes = whiteTestImage(16, 16)
        val limits = ImageDecodeLimits(maxEncodedBytes = bytes.size - 1L)

        assertFailsWith<IllegalArgumentException> {
            immutableImageOf(bytes, limits)
        }.message shouldBeEqualTo "Image input encodedBytes=${bytes.size} exceeds maxEncodedBytes=${limits.maxEncodedBytes}."
    }

    @Test
    fun `bounded byte array loader rejects decoded pixels before full decode`() {
        val bytes = whiteTestImage(16, 16)
        val limits = ImageDecodeLimits(maxEncodedBytes = bytes.size.toLong(), maxDecodedPixels = 255L)

        assertFailsWith<IllegalArgumentException> {
            immutableImageOf(bytes, limits)
        }.message shouldBeEqualTo "Image input decodedPixels=256 exceeds maxInputPixels=255 (dimensions=16x16)."
    }

    @Test
    fun `strict external loader rejects payload when dimensions are unknown`() {
        val bytes = "not an encoded image".toByteArray()
        val limits = ImageDecodeLimits(maxEncodedBytes = bytes.size.toLong())

        assertFailsWith<IllegalArgumentException> {
            immutableExternalImageOf(bytes, limits)
        }.message shouldBeEqualTo "Image input dimensions could not be determined."
    }

    @Test
    fun `strict external loader preserves encoded byte limit`() {
        val bytes = whiteTestImage(16, 16)
        val limits = ImageDecodeLimits(maxEncodedBytes = bytes.size - 1L)

        assertFailsWith<IllegalArgumentException> {
            immutableExternalImageOf(bytes, limits)
        }.message shouldBeEqualTo "Image input encodedBytes=${bytes.size} exceeds maxEncodedBytes=${limits.maxEncodedBytes}."
    }

    @Test
    fun `strict external path loader rejects payload before decode`(tempFolder: TempFolder) {
        val bytes = whiteTestImage(16, 16)
        val path = tempFolder.createFile("strict-path.png").toPath()
        Files.write(path, bytes)

        assertFailsWith<IllegalArgumentException> {
            immutableExternalImageOf(
                path,
                ImageDecodeLimits(maxEncodedBytes = bytes.size - 1L),
            )
        }.message shouldBeEqualTo "Image input encodedBytes=${bytes.size} exceeds maxEncodedBytes=${bytes.size - 1}."
    }

    @Test
    fun `strict external input stream loader preserves caller ownership`() {
        val stream = TrackingInputStream(whiteTestImage(16, 16))

        val image = immutableExternalImageOf(stream)

        image.width shouldBeEqualTo 16
        image.height shouldBeEqualTo 16
        stream.closed.shouldBeFalse()
    }

    @Test
    fun `strict external buffered source loader preserves caller ownership`() {
        val source = TrackingSource(whiteTestImage(16, 16))
        val bufferedSource = source.buffered()

        val image = immutableExternalImageOf(bufferedSource)

        image.width shouldBeEqualTo 16
        image.height shouldBeEqualTo 16
        source.closed.shouldBeFalse()
        bufferedSource.close()
    }

    @Test
    fun `strict external source loader closes owned source`() {
        val source = TrackingSource(whiteTestImage(16, 16))

        val image = immutableExternalImageOf(source)

        image.width shouldBeEqualTo 16
        image.height shouldBeEqualTo 16
        source.closed.shouldBeTrue()
    }

    @Test
    fun `strict external loader accepts WebP when metadata supplies dimensions`() {
        val bytes = immutableImageOf(whiteTestImage(24, 16))
            .forWriter(WebpWriter.DEFAULT)
            .bytes()

        val image = immutableExternalImageOf(
            bytes,
            ImageDecodeLimits(
                maxEncodedBytes = bytes.size.toLong(),
                maxDecodedPixels = 24L * 16L,
                maxDecodedSide = 24,
            )
        )

        image.width shouldBeEqualTo 24
        image.height shouldBeEqualTo 16
    }

    @Test
    fun `bounded stream loader rejects encoded payload while reading`() {
        val bytes = whiteTestImage(16, 16)
        val limits = ImageDecodeLimits(maxEncodedBytes = bytes.size - 1L)

        assertFailsWith<IllegalArgumentException> {
            immutableImageOf(ByteArrayInputStream(bytes), limits)
        }.message shouldBeEqualTo "Image input encodedBytes=${bytes.size} exceeds maxEncodedBytes=${limits.maxEncodedBytes}."
    }

    @Test
    fun `bounded path loader accepts valid image within limits`(tempFolder: TempFolder) {
        val bytes = whiteTestImage(16, 16)
        val path = tempFolder.createFile("bounded.png").toPath()
        Files.write(path, bytes)

        val image = immutableImageOf(
            path,
            ImageDecodeLimits(maxEncodedBytes = bytes.size.toLong(), maxDecodedPixels = 256L, maxDecodedSide = 16)
        )

        image.width shouldBeEqualTo 16
        image.height shouldBeEqualTo 16
    }

    @Test
    fun `suspend load image from Okio Source`() = runTest {
        val image = Path.of("$BASE_PATH/homer.jpg").toFile().inputStream().asSource().use { source ->
            suspendLoadImage(source)
        }

        image.width shouldBeGreaterThan 0
        image.height shouldBeGreaterThan 0
    }

    @Test
    fun `suspend load image from suspended file source`() = runSuspendIO {
        val sourcePath = Path.of("$BASE_PATH/homer.jpg")
        val channel = AsynchronousFileChannel.open(sourcePath, READ)

        val image = suspendLoadImage(channel.asSuspendedSource())

        image.width shouldBeGreaterThan 0
        image.height shouldBeGreaterThan 0
    }

    @Test
    fun `suspend load image from buffered suspended source keeps caller ownership`() = runSuspendIO {
        val sourcePath = Path.of("$BASE_PATH/homer.jpg")
        val channel = AsynchronousFileChannel.open(sourcePath, READ)
        val source = channel.asSuspendedSource().bufferedSuspended()

        try {
            val image = suspendLoadImage(source)

            image.width shouldBeGreaterThan 0
            image.height shouldBeGreaterThan 0
            channel.isOpen.shouldBeTrue()
        } finally {
            source.close()
            channel.close()
        }
    }

    @Test
    fun `suspendWrite writes to Okio BufferedSink`() = runTest {
        val image = immutableImageOf(Path.of("$BASE_PATH/homer.jpg"))
        val buffer = Buffer()

        image.suspendWrite(SuspendJpegWriter.Default, buffer)

        buffer.size shouldBeGreaterThan 0L
    }

    @Test
    fun `suspendWrite to Okio Sink closes owned sink`() = runTest {
        val image = immutableImageOf(whiteTestImage(512, 512))
        val sink = TrackingSink()

        image.suspendWrite(SuspendJpegWriter.Default, sink)

        sink.closed.shouldBeTrue()
        sink.output.size shouldBeGreaterThan 0L
    }

    @Test
    fun `suspendWrite writes to suspended file sink`(tempFolder: TempFolder) = runSuspendIO {
        val image = immutableImageOf(Path.of("$BASE_PATH/homer.jpg"))
        val output = tempFolder.createFile("suspended-output.jpg").toPath()
        val channel = AsynchronousFileChannel.open(output, WRITE, CREATE, TRUNCATE_EXISTING)

        image.suspendWrite(SuspendJpegWriter.Default, channel.asSuspendedSink())

        output.toFile().length() shouldBeGreaterThan 0L
    }

    private class TrackingSource(bytes: ByteArray): Source {
        private val buffer = Buffer().write(bytes)
        var closed: Boolean = false

        override fun read(sink: Buffer, byteCount: Long): Long =
            buffer.read(sink, byteCount)

        override fun timeout(): Timeout = Timeout.NONE

        override fun close() {
            closed = true
        }
    }

    private class TrackingInputStream(bytes: ByteArray): InputStream() {
        private val delegate = ByteArrayInputStream(bytes)
        var closed: Boolean = false

        override fun read(): Int = delegate.read()

        override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
            delegate.read(bytes, offset, length)

        override fun close() {
            closed = true
            delegate.close()
        }
    }

    private class TrackingSink: Sink {
        val output: Buffer = Buffer()
        var closed: Boolean = false

        override fun write(source: Buffer, byteCount: Long) {
            output.write(source, byteCount)
        }

        override fun flush() = Unit

        override fun timeout(): Timeout = Timeout.NONE

        override fun close() {
            closed = true
        }
    }

    private fun whiteTestImage(w: Int, h: Int): ByteArray {
        val buf = bufferedImageOf(w, h)
        buf.useGraphics { g ->
            g.color = Color.WHITE
            g.fillRect(0, 0, w, h)
        }
        return buf.toByteArray("png")
    }
}
